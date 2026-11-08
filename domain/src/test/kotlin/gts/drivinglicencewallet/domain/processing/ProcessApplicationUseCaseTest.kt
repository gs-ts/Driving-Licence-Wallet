package gts.drivinglicencewallet.domain.processing

import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.FakeLicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.FixedClock
import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.submittedApplication
import gts.drivinglicencewallet.domain.processing.BackendCall.AssessRisk
import gts.drivinglicencewallet.domain.processing.BackendCall.CheckData
import gts.drivinglicencewallet.domain.processing.BackendCall.CheckPhoto
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.Approved
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.CheckingPhoto
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.Failed
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.PendingManualReview
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.RiskAssessment
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.Submitted
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.ValidatingData
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.HistoryEntry
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant

class ProcessApplicationUseCaseTest {

    private val submittedAt = Instant.parse(input = "2026-11-08T09:00:00Z")
    private val processedAt = Instant.parse(input = "2026-11-08T09:05:00Z")
    private val id = ApplicationId(value = "application-1")
    private val repository = FakeLicenceApplicationRepository()
    private val backend = FakeIssuerBackend()

    /** An application that passes every check, after a run without a crash. */
    private val approvedHistory = listOf(
        entry(from = null, to = Submitted, event = WorkflowEvent.ApplicationSubmitted, timestamp = submittedAt),
        entry(from = Submitted, to = ValidatingData, event = WorkflowEvent.ProcessingStarted),
        entry(from = ValidatingData, to = CheckingPhoto, event = WorkflowEvent.DataValidationPassed),
        entry(from = CheckingPhoto, to = RiskAssessment, event = WorkflowEvent.PhotoCheckPassed),
        entry(from = RiskAssessment, to = Approved, event = WorkflowEvent.AutoApproved),
    )

    @Test
    fun `given an application that passes every check, when it is processed, then it is approved automatically with each step in its history`() = runTest {
        addSubmittedApplication(repository = repository)

        val result = process()

        assertEquals(expected = ProcessingResult.Done, actual = result)
        assertEquals(expected = approvedHistory, actual = historyOf(repository = repository))
        assertEquals(expected = listOf(CheckData, CheckPhoto, AssessRisk), actual = backend.calls)
    }

    @Test
    fun `given a reason for a manual review, when the application is processed, then it waits for a reviewer and a later run changes nothing`() = runTest {
        backend.reviewReasons = setOf(element = ReviewReason.Under21)
        addSubmittedApplication(repository = repository)

        process()
        val history = historyOf(repository = repository)
        val laterResult = process()

        val reviewRequired = entry(
            from = RiskAssessment,
            to = PendingManualReview,
            event = WorkflowEvent.ManualReviewRequired(reasons = setOf(element = ReviewReason.Under21)),
        )
        assertEquals(expected = approvedHistory.take(n = 4) + reviewRequired, actual = history)
        assertEquals(expected = ProcessingResult.Done, actual = laterResult)
        assertEquals(expected = history, actual = historyOf(repository = repository))
        assertEquals(expected = listOf(CheckData, CheckPhoto, AssessRisk), actual = backend.calls)
    }

    @Test
    fun `given details that fail the data check, when the application is processed, then it fails with the problems and the photo is never checked`() = runTest {
        backend.dataProblems = setOf(element = DataProblem.Under18)
        addSubmittedApplication(repository = repository)

        process()

        val dataCheckFailed = entry(
            from = ValidatingData,
            to = Failed,
            event = WorkflowEvent.DataValidationFailed(problems = setOf(element = DataProblem.Under18)),
        )
        assertEquals(expected = approvedHistory.take(n = 2) + dataCheckFailed, actual = historyOf(repository = repository))
        assertEquals(expected = listOf(element = CheckData), actual = backend.calls)
    }

    @Test
    fun `given a photo that fails the photo check, when the application is processed, then it fails with the photo problems`() = runTest {
        backend.photoProblems = setOf(element = PhotoProblem.TooSmall)
        addSubmittedApplication(repository = repository)

        process()

        val photoCheckFailed = entry(
            from = CheckingPhoto,
            to = Failed,
            event = WorkflowEvent.PhotoCheckFailed(problems = setOf(element = PhotoProblem.TooSmall)),
        )
        assertEquals(expected = approvedHistory.take(n = 3) + photoCheckFailed, actual = historyOf(repository = repository))
    }

    @Test
    fun `given the backend is temporarily unavailable, when the application is processed, then it asks to retry later and the next attempt carries on`() = runTest {
        addSubmittedApplication(repository = repository)
        backend.unavailableCalls = BackendCall.entries.toSet()

        val firstResult = process(attempt = 1)
        val statusAfterFirstAttempt = assertNotNull(actual = repository[id]).status
        backend.unavailableCalls = emptySet()
        val secondResult = process(attempt = 2)

        assertEquals(expected = ProcessingResult.RetryLater, actual = firstResult)
        assertEquals(expected = ValidatingData, actual = statusAfterFirstAttempt)
        assertEquals(expected = ProcessingResult.Done, actual = secondResult)
        assertEquals(expected = approvedHistory, actual = historyOf(repository = repository))
    }

    @Test
    fun `given any step is still unavailable on the last attempt, when the application is processed, then it fails at that step as given up after that many attempts`() = runTest {
        val gaveUp = WorkflowEvent.ProcessingAbandoned(attempts = MAX_ATTEMPTS)
        val historyByUnavailableCall = mapOf(
            CheckData to approvedHistory.take(n = 2) + entry(from = ValidatingData, to = Failed, event = gaveUp),
            CheckPhoto to approvedHistory.take(n = 3) + entry(from = CheckingPhoto, to = Failed, event = gaveUp),
            AssessRisk to approvedHistory.take(n = 4) + entry(from = RiskAssessment, to = Failed, event = gaveUp),
        )
        for ((unavailableCall, expectedHistory) in historyByUnavailableCall) {
            val repository = FakeLicenceApplicationRepository()
            val backend = FakeIssuerBackend()
            backend.unavailableCalls = setOf(element = unavailableCall)
            addSubmittedApplication(repository = repository)

            val result = process(repository = repository, backend = backend, attempt = MAX_ATTEMPTS)

            assertEquals(expected = ProcessingResult.Done, actual = result, message = "$unavailableCall unavailable")
            assertEquals(expected = expectedHistory, actual = historyOf(repository = repository), message = "$unavailableCall unavailable")
        }
    }

    @Test
    fun `given a crash right after any step is saved, when processing runs again, then the history is the same as without a crash and no step runs twice`() = runTest {
        for (crashOn in 1..PROCESSING_SAVES) {
            val repository = FakeLicenceApplicationRepository()
            val backend = FakeIssuerBackend()
            addSubmittedApplication(repository = repository)

            assertFailsWith<SimulatedCrash> {
                process(repository = CrashingRepository(repository = repository, crashOn = crashOn, afterSaving = true), backend = backend)
            }
            process(repository = repository, backend = backend, attempt = 2)

            assertEquals(expected = approvedHistory, actual = historyOf(repository = repository), message = "Crash after save $crashOn")
            assertEquals(expected = listOf(CheckData, CheckPhoto, AssessRisk), actual = backend.calls, message = "Crash after save $crashOn")
        }
    }

    @Test
    fun `given a crash right before any step is saved, when processing runs again, then the history is the same as without a crash and only the unsaved step runs twice`() = runTest {
        val callsByCrash = mapOf(
            1 to listOf(CheckData, CheckPhoto, AssessRisk),
            2 to listOf(CheckData, CheckData, CheckPhoto, AssessRisk),
            3 to listOf(CheckData, CheckPhoto, CheckPhoto, AssessRisk),
            4 to listOf(CheckData, CheckPhoto, AssessRisk, AssessRisk),
        )
        for ((crashOn, expectedCalls) in callsByCrash) {
            val repository = FakeLicenceApplicationRepository()
            val backend = FakeIssuerBackend()
            addSubmittedApplication(repository = repository)

            assertFailsWith<SimulatedCrash> {
                process(repository = CrashingRepository(repository = repository, crashOn = crashOn, afterSaving = false), backend = backend)
            }
            process(repository = repository, backend = backend, attempt = 2)

            assertEquals(expected = approvedHistory, actual = historyOf(repository = repository), message = "Crash before save $crashOn")
            assertEquals(expected = expectedCalls, actual = backend.calls, message = "Crash before save $crashOn")
        }
    }

    @Test
    fun `given processing started before a crash, when it runs again on a later day, then the backend still gets the time processing started`() = runTest {
        val nextDay = Instant.parse(input = "2026-11-09T09:00:00Z")
        addSubmittedApplication(repository = repository)

        assertFailsWith<SimulatedCrash> {
            process(repository = CrashingRepository(repository = repository, crashOn = 2, afterSaving = false))
        }
        process(attempt = 2, clock = FixedClock(instant = nextDay))

        assertEquals(expected = listOf(processedAt, processedAt, processedAt), actual = backend.processingDates)
    }

    @Test
    fun `given another run saves a step first, when this run tries to save it, then it carries on and the history has each step once`() = runTest {
        addSubmittedApplication(repository = repository)

        val result = process(repository = RacingRepository(repository = repository, raceAt = ValidatingData))

        assertEquals(expected = ProcessingResult.Done, actual = result)
        assertEquals(expected = approvedHistory, actual = historyOf(repository = repository))
    }

    private suspend fun addSubmittedApplication(repository: FakeLicenceApplicationRepository) {
        repository.add(application = submittedApplication(id = id, timestamp = submittedAt))
    }

    private suspend fun process(
        repository: LicenceApplicationRepository = this.repository,
        backend: IssuerBackend = this.backend,
        attempt: Int = 1,
        clock: Clock = FixedClock(instant = processedAt),
    ): ProcessingResult {
        val processApplication = ProcessApplicationUseCase(
            repository = repository,
            backend = backend,
            clock = clock,
            maxAttempts = MAX_ATTEMPTS,
        )
        return processApplication(id = id, attempt = attempt)
    }

    private fun historyOf(repository: FakeLicenceApplicationRepository): List<HistoryEntry> = assertNotNull(actual = repository[id]).history

    private fun entry(
        from: ApplicationStatus?,
        to: ApplicationStatus,
        event: WorkflowEvent,
        timestamp: Instant = processedAt,
    ) = HistoryEntry(
        timestamp = timestamp,
        fromStatus = from,
        toStatus = to,
        event = event,
    )

    private companion object {

        const val MAX_ATTEMPTS = 5

        /** Processing saves 4 changes: processing started, then one per check. */
        const val PROCESSING_SAVES = 4
    }
}
