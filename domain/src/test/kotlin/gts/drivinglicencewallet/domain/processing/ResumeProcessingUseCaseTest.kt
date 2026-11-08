package gts.drivinglicencewallet.domain.processing

import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.FakeLicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.recordOrFail
import gts.drivinglicencewallet.domain.application.submittedApplication
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ResumeProcessingUseCaseTest {

    private val submittedAt = Instant.parse(input = "2026-11-08T09:00:00Z")
    private val id = ApplicationId(value = "application-1")
    private val repository = FakeLicenceApplicationRepository()
    private val scheduled = mutableListOf<ApplicationId>()
    private val resumeProcessing = ResumeProcessingUseCase(repository = repository, scheduler = { scheduledId -> scheduled += scheduledId })

    @Test
    fun `given a submitted application that was never scheduled, when processing resumes, then it is scheduled`() = runTest {
        repository.add(application = submittedApplication(id = id, timestamp = submittedAt))

        resumeProcessing()

        assertEquals(expected = listOf(id), actual = scheduled)
    }

    @Test
    fun `given an application whose run a bug stopped in a step, when processing resumes, then it is scheduled again`() = runTest {
        val stopped = submittedApplication(id = id, timestamp = submittedAt)
            .recordOrFail(change = ApplicationStatus.Submitted.startProcessing(), timestamp = submittedAt)
        repository.add(application = stopped)

        resumeProcessing()

        assertEquals(expected = listOf(id), actual = scheduled)
    }

    @Test
    fun `given an application waiting for a reviewer, when processing resumes, then nothing is scheduled`() = runTest {
        val waiting = submittedApplication(id = id, timestamp = submittedAt)
            .recordOrFail(change = ApplicationStatus.Submitted.startProcessing(), timestamp = submittedAt)
            .recordOrFail(change = ApplicationStatus.ValidatingData.pass(), timestamp = submittedAt)
            .recordOrFail(change = ApplicationStatus.CheckingPhoto.pass(), timestamp = submittedAt)
            .recordOrFail(change = ApplicationStatus.RiskAssessment.requireManualReview(reasons = setOf(ReviewReason.Under21)), timestamp = submittedAt)
        repository.add(application = waiting)

        resumeProcessing()

        assertEquals(expected = emptyList(), actual = scheduled)
    }
}
