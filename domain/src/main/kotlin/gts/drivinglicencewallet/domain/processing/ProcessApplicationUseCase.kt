package gts.drivinglicencewallet.domain.processing

import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.RecordResult
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.StatusChange
import kotlin.time.Clock

/**
 * Runs an application's processing steps one at a time, and saves each step before the next.
 * A run after a crash starts from the saved status, so a finished step never runs again.
 */
class ProcessApplicationUseCase(
    private val repository: LicenceApplicationRepository,
    private val backend: IssuerBackend,
    private val clock: Clock,
    private val maxAttempts: Int,
) {

    /** [attempt] is the number of this run, from 1.
     * If a step is still unavailable on the last attempt, the application fails.
     * */
    suspend operator fun invoke(id: ApplicationId, attempt: Int): ProcessingResult {
        // No such application: there's nothing to process, and a retry wouldn't find it either.
        val application = repository.find(id = id) ?: return ProcessingResult.Done
        return continueFrom(application = application, attempt = attempt)
    }

    /** Runs the steps that are left, starting from the application's current status. */
    private suspend fun continueFrom(application: LicenceApplication, attempt: Int): ProcessingResult {
        return when (application.status) {
            ApplicationStatus.Submitted -> save(
                id = application.id,
                change = ApplicationStatus.Submitted.startProcessing(),
                attempt = attempt
            )
            ApplicationStatus.ValidatingData -> checkData(application = application, attempt = attempt)
            ApplicationStatus.CheckingPhoto -> checkPhoto(application = application, attempt = attempt)
            ApplicationStatus.RiskAssessment -> assessRisk(application = application, attempt = attempt)
            ApplicationStatus.PendingManualReview, is ApplicationStatus.Final -> ProcessingResult.Done
        }
    }

    private suspend fun checkData(application: LicenceApplication, attempt: Int): ProcessingResult {
        val result = backend.checkData(
            details = application.details,
            processingStartedAt = checkNotNull(value = application.processingStartedAt)
        )

        return saveAnswer(
            application = application,
            step = ApplicationStatus.ValidatingData,
            result = result,
            attempt = attempt
        ) { problems ->
            if (problems.isEmpty()) {
                ApplicationStatus.ValidatingData.pass()
            } else {
                ApplicationStatus.ValidatingData.fail(problems = problems)
            }
        }
    }

    private suspend fun checkPhoto(application: LicenceApplication, attempt: Int): ProcessingResult {
        val result = backend.checkPhoto(photo = application.photo)

        return saveAnswer(
            application = application,
            step = ApplicationStatus.CheckingPhoto,
            result = result,
            attempt = attempt
        ) { problems ->
            if (problems.isEmpty()) {
                ApplicationStatus.CheckingPhoto.pass()
            } else {
                ApplicationStatus.CheckingPhoto.fail(problems = problems)
            }
        }
    }

    private suspend fun assessRisk(application: LicenceApplication, attempt: Int): ProcessingResult {
        val result = backend.assessRisk(
            details = application.details,
            photo = application.photo,
            processingStartedAt = checkNotNull(value = application.processingStartedAt)
        )

        return saveAnswer(
            application = application,
            step = ApplicationStatus.RiskAssessment,
            result = result,
            attempt = attempt
        ) { reasons ->
            if (reasons.isEmpty()) {
                ApplicationStatus.RiskAssessment.approveAutomatically()
            } else {
                ApplicationStatus.RiskAssessment.requireManualReview(reasons = reasons)
            }
        }
    }

    /** Saves the change the backend's answer leads to. If the backend was unavailable, retries later or gives up. */
    private suspend fun <T> saveAnswer(
        application: LicenceApplication,
        step: ApplicationStatus.Processing,
        result: BackendResult<T>,
        attempt: Int,
        changeFor: (T) -> StatusChange<ApplicationStatus>,
    ): ProcessingResult {
        return when (result) {
            is BackendResult.Answered -> save(
                id = application.id,
                change = changeFor(result.answer),
                attempt = attempt
            )
            BackendResult.Unavailable -> retryOrGiveUp(
                id = application.id,
                step = step,
                attempt = attempt
            )
        }
    }

    private suspend fun retryOrGiveUp(
        id: ApplicationId,
        step: ApplicationStatus.Processing,
        attempt: Int,
    ): ProcessingResult {
        return if (attempt < maxAttempts) {
            ProcessingResult.RetryLater
        } else {
            save(
                id = id,
                change = step.abandon(attempts = attempt),
                attempt = attempt
            )
        }
    }

    /** Saves [change], then runs the steps that are left. */
    private suspend fun save(
        id: ApplicationId,
        change: StatusChange<ApplicationStatus>,
        attempt: Int,
    ): ProcessingResult {
        val result = repository.record(
            id = id,
            change = change,
            timestamp = clock.now()
        )

        return when (result) {
            is RecordResult.Recorded -> continueFrom(application = result.application, attempt = attempt)
            // Another run saved this step first, so carry on from where it got to.
            is RecordResult.Refused -> continueFrom(application = result.current, attempt = attempt)
            RecordResult.NotFound -> ProcessingResult.Done
        }
    }
}

sealed interface ProcessingResult {

    /** Nothing left for this run: the application is finished, waiting for a reviewer, or gone. */
    data object Done : ProcessingResult

    /** A step was temporarily unavailable. Run again later. */
    data object RetryLater : ProcessingResult
}
