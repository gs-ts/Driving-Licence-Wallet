package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.applicant.completeDetails
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.HistoryEntry
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class LicenceApplicationTest {

    private val submittedAt = Instant.parse("2026-11-07T10:00:00Z")
    private val processingStartedAt = Instant.parse("2026-11-07T10:00:02Z")

    @Test
    fun `given new details, when the application is submitted, then its status is submitted and the submission is its only history entry`() {
        val application = submitApplication()

        val submission = HistoryEntry(
            timestamp = submittedAt,
            fromStatus = null,
            toStatus = ApplicationStatus.Submitted,
            event = WorkflowEvent.ApplicationSubmitted,
        )
        assertEquals(ApplicationStatus.Submitted, application.status)
        assertEquals(listOf(submission), application.history)
        assertEquals(submittedAt, application.createdAt)
        assertEquals(submittedAt, application.updatedAt)
    }

    @Test
    fun `given a submitted application, when processing starts, then the status moves and updatedAt is the time of the new entry`() {
        val application = submitApplication()

        val processing = assertNotNull(application.record(ApplicationStatus.Submitted.startProcessing(), processingStartedAt))

        val processingStarted = HistoryEntry(
            timestamp = processingStartedAt,
            fromStatus = ApplicationStatus.Submitted,
            toStatus = ApplicationStatus.ValidatingData,
            event = WorkflowEvent.ProcessingStarted,
        )
        assertEquals(ApplicationStatus.ValidatingData, processing.status)
        assertEquals(application.history + processingStarted, processing.history)
        assertEquals(submittedAt, processing.createdAt)
        assertEquals(processingStartedAt, processing.updatedAt)
    }

    @Test
    fun `given an application that has already moved on, when the same change is recorded again, then it is refused`() {
        val change = ApplicationStatus.Submitted.startProcessing()
        val processing = assertNotNull(submitApplication().record(change, processingStartedAt))

        assertNull(processing.record(change, processingStartedAt))
    }

    private fun submitApplication() = LicenceApplication.submit(
        id = ApplicationId("application-1"),
        details = completeDetails(),
        photo = PhotoReference("photo-1"),
        timestamp = submittedAt,
    )
}
