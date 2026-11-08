package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.applicant.completeDetails
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.HistoryEntry
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.RejectionReason
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
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

    @Test
    fun `given each path through the workflow, when an application is restored from its saved events, then it equals the one the workflow recorded`() {
        val startProcessing = ApplicationStatus.Submitted.startProcessing()
        val passData = ApplicationStatus.ValidatingData.pass()
        val passPhoto = ApplicationStatus.CheckingPhoto.pass()
        val requireReview = ApplicationStatus.RiskAssessment.requireManualReview(reasons = setOf(element = ReviewReason.Under21))
        val rejectionReason = assertNotNull(actual = RejectionReason.parse(text = "The face isn't visible in the photo."))
        // Together, the paths use every event, so a move swapped for a similar one fails at least one of them.
        val paths = listOf(
            listOf(startProcessing, ApplicationStatus.ValidatingData.fail(problems = setOf(element = DataProblem.Under18))),
            listOf(startProcessing, passData, ApplicationStatus.CheckingPhoto.fail(problems = setOf(element = PhotoProblem.TooSmall))),
            listOf(startProcessing, passData, ApplicationStatus.CheckingPhoto.abandon(attempts = 3)),
            listOf(startProcessing, passData, passPhoto, ApplicationStatus.RiskAssessment.approveAutomatically()),
            listOf(startProcessing, passData, passPhoto, requireReview, ApplicationStatus.PendingManualReview.approve()),
            listOf(startProcessing, passData, passPhoto, requireReview, ApplicationStatus.PendingManualReview.reject(reason = rejectionReason)),
        )

        paths.forEach { changes ->
            val recorded = changes.foldIndexed(initial = submitApplication()) { index, application, change ->
                application.recordOrFail(change = change, timestamp = submittedAt + (index + 1).minutes)
            }
            val savedEvents = recorded.history.map { entry -> SavedEvent(event = entry.event, timestamp = entry.timestamp) }

            val restored = restoreApplication(events = savedEvents)

            assertEquals(expected = recorded, actual = restored, message = "Path ending with ${recorded.history.last().event}")
        }
    }

    @Test
    fun `given an event that isn't a move from the status before it, when the application is restored, then there's no application`() {
        val savedEvents = listOf(
            SavedEvent(event = WorkflowEvent.ApplicationSubmitted, timestamp = submittedAt),
            SavedEvent(event = WorkflowEvent.AutoApproved, timestamp = processingStartedAt),
        )

        assertNull(actual = restoreApplication(events = savedEvents))
    }

    @Test
    fun `given events that don't start with the submission, when the application is restored, then there's no application`() {
        val savedEvents = listOf(SavedEvent(event = WorkflowEvent.ProcessingStarted, timestamp = processingStartedAt))

        assertNull(actual = restoreApplication(events = savedEvents))
        assertNull(actual = restoreApplication(events = emptyList()))
    }

    private fun submitApplication() = LicenceApplication.submit(
        id = ApplicationId("application-1"),
        details = completeDetails(),
        photo = PhotoReference("photo-1"),
        timestamp = submittedAt,
    )

    /** Restores [events] into an application with the same ID, details and photo as [submitApplication]'s. */
    private fun restoreApplication(events: List<SavedEvent>) = LicenceApplication.restore(
        id = ApplicationId(value = "application-1"),
        details = completeDetails(),
        photo = PhotoReference(value = "photo-1"),
        events = events,
    )
}
