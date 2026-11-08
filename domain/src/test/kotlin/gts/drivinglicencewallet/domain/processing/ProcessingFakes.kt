package gts.drivinglicencewallet.domain.processing

import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.FakeLicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.application.RecordResult
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import gts.drivinglicencewallet.domain.workflow.StatusChange
import kotlin.time.Instant

/** Gives the answers it's set up with, and remembers each call. */
class FakeIssuerBackend : IssuerBackend {

    var dataProblems: Set<DataProblem> = emptySet()
    var photoProblems: Set<PhotoProblem> = emptySet()
    var reviewReasons: Set<ReviewReason> = emptySet()

    /** The calls that answer [BackendResult.Unavailable]. */
    var unavailableCalls: Set<BackendCall> = emptySet()
    val calls = mutableListOf<BackendCall>()
    val processingDates = mutableListOf<Instant>()

    override suspend fun checkData(details: ApplicantDetails, processingStartedAt: Instant): BackendResult<Set<DataProblem>> {
        processingDates += processingStartedAt
        return answer(call = BackendCall.CheckData, value = dataProblems)
    }

    override suspend fun checkPhoto(photo: PhotoReference): BackendResult<Set<PhotoProblem>> =
        answer(call = BackendCall.CheckPhoto, value = photoProblems)

    override suspend fun assessRisk(
        details: ApplicantDetails,
        photo: PhotoReference,
        processingStartedAt: Instant,
    ): BackendResult<Set<ReviewReason>> {
        processingDates += processingStartedAt
        return answer(call = BackendCall.AssessRisk, value = reviewReasons)
    }

    private fun <T> answer(call: BackendCall, value: T): BackendResult<T> {
        calls += call
        return if (call in unavailableCalls) BackendResult.Unavailable else BackendResult.Answered(answer = value)
    }
}

enum class BackendCall { CheckData, CheckPhoto, AssessRisk }

/** Stands in for a killed process: nothing after it runs. */
class SimulatedCrash : RuntimeException()

/** Crashes right before or right after saving its [crashOn]th change, counting from 1. */
class CrashingRepository(
    private val repository: FakeLicenceApplicationRepository,
    private val crashOn: Int,
    private val afterSaving: Boolean,
) : LicenceApplicationRepository by repository {

    private var saves = 0

    override suspend fun record(
        id: ApplicationId,
        change: StatusChange<ApplicationStatus>,
        timestamp: Instant
    ): RecordResult {
        saves += 1
        if (saves == crashOn && !afterSaving) throw SimulatedCrash()
        val result = repository.record(id = id, change = change, timestamp = timestamp)
        if (saves == crashOn && afterSaving) throw SimulatedCrash()
        return result
    }
}

/** Another run saves the same change first, when the change starts from [raceAt]. */
class RacingRepository(
    private val repository: FakeLicenceApplicationRepository,
    private val raceAt: ApplicationStatus,
) : LicenceApplicationRepository by repository {

    override suspend fun record(
        id: ApplicationId,
        change: StatusChange<ApplicationStatus>,
        timestamp: Instant
    ): RecordResult {
        if (change.from == raceAt) repository.record(id = id, change = change, timestamp = timestamp)
        return repository.record(id = id, change = change, timestamp = timestamp)
    }
}
