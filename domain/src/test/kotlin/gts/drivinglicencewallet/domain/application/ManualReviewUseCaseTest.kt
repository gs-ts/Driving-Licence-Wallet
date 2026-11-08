package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.HistoryEntry
import gts.drivinglicencewallet.domain.workflow.RejectionReason
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

class ManualReviewUseCaseTest {

    private val submittedAt = Instant.parse("2026-11-07T10:00:00Z")
    private val decidedAt = Instant.parse("2026-11-07T11:00:00Z")
    private val id = ApplicationId("application-1")
    private val reason = assertNotNull(RejectionReason.parse("The photo doesn't show the applicant"))
    private val store = FakeApplicationStore()
    private val manualReview = ManualReviewUseCase(store = store, clock = FixedClock(decidedAt))

    @Test
    fun `given an application pending manual review, when the reviewer approves it, then it is approved and the approval is added to its history`() = runTest {
        val pending = addPendingApplication()

        val approved = assertIs<ReviewResult.Recorded>(value = manualReview.approve(id)).application

        val approval = HistoryEntry(
            timestamp = decidedAt,
            fromStatus = ApplicationStatus.PendingManualReview,
            toStatus = ApplicationStatus.Approved,
            event = WorkflowEvent.ManuallyApproved,
        )
        assertEquals(expected = ApplicationStatus.Approved, actual = approved.status)
        assertEquals(expected = pending.history + approval, actual = approved.history)
        assertEquals(expected = approved, actual = store[id])
    }

    @Test
    fun `given an application pending manual review, when the reviewer rejects it with a reason, then it is rejected and its history keeps the reason`() = runTest {
        val pending = addPendingApplication()

        val rejected = assertIs<ReviewResult.Recorded>(value = manualReview.reject(id, reason)).application

        val rejection = HistoryEntry(
            timestamp = decidedAt,
            fromStatus = ApplicationStatus.PendingManualReview,
            toStatus = ApplicationStatus.Rejected,
            event = WorkflowEvent.ManuallyRejected(reason),
        )
        assertEquals(expected = ApplicationStatus.Rejected, actual = rejected.status)
        assertEquals(expected = pending.history + rejection, actual = rejected.history)
        assertEquals(expected = rejected, store[id])
    }

    @Test
    fun `given an application that is not pending manual review yet, when the reviewer decides, then it is refused with its current status and nothing changes`() = runTest {
        val submitted = submittedApplication(id = id, timestamp = submittedAt)
        store.add(submitted)

        assertEquals(expected = ReviewResult.NotPendingReview(status = ApplicationStatus.Submitted), actual = manualReview.approve(id))
        assertEquals(expected = ReviewResult.NotPendingReview(status = ApplicationStatus.Submitted), actual = manualReview.reject(id, reason))
        assertEquals(expected = submitted, store[id])
    }

    @Test
    fun `given an approved application, when the reviewer decides again, then it is refused and the history keeps one decision`() = runTest {
        addPendingApplication()
        val approved = assertIs<ReviewResult.Recorded>(manualReview.approve(id)).application

        assertEquals(expected = ReviewResult.NotPendingReview(status = ApplicationStatus.Approved), actual = manualReview.approve(id))
        assertEquals(expected = ReviewResult.NotPendingReview(status = ApplicationStatus.Approved), actual = manualReview.reject(id, reason))
        assertEquals(expected = approved, store[id])
    }

    private suspend fun addPendingApplication(): LicenceApplication {
        val pending = submittedApplication(id = id, timestamp = submittedAt)
            .recordOrFail(change = ApplicationStatus.Submitted.startProcessing(), timestamp = submittedAt)
            .recordOrFail(change = ApplicationStatus.ValidatingData.pass(), timestamp = submittedAt)
            .recordOrFail(change = ApplicationStatus.CheckingPhoto.pass(), timestamp = submittedAt)
            .recordOrFail(change = ApplicationStatus.RiskAssessment.requireManualReview(setOf(ReviewReason.Under21)), timestamp = submittedAt)
        store.add(pending)
        return pending
    }
}
