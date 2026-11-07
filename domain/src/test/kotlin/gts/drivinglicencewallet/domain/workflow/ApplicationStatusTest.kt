package gts.drivinglicencewallet.domain.workflow

import gts.drivinglicencewallet.domain.applicant.ApplicantField
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.Approved
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.CheckingPhoto
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.Failed
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.PendingManualReview
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.Rejected
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.RiskAssessment
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.Submitted
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus.ValidatingData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ApplicationStatusTest {

    @Test
    fun `given a new application, when it is submitted, then it starts as submitted with no previous status`() {
        val change = ApplicationStatus.submit()

        assertChange(change, from = null, to = Submitted, event = WorkflowEvent.ApplicationSubmitted)
    }

    @Test
    fun `given a submitted application, when processing starts, then it moves to validating data`() {
        val change = Submitted.startProcessing()

        assertChange(change, from = Submitted, to = ValidatingData, event = WorkflowEvent.ProcessingStarted)
    }

    @Test
    fun `given data validation, when the data passes, then it moves to checking photo`() {
        val change = ValidatingData.pass()

        assertChange(change, from = ValidatingData, to = CheckingPhoto, event = WorkflowEvent.DataValidationPassed)
    }

    @Test
    fun `given data validation, when the data fails, then it moves to failed and the event carries the problems`() {
        val problems = setOf(DataProblem.MissingField(ApplicantField.NationalId), DataProblem.Under18)

        val change = ValidatingData.fail(problems)

        assertChange(change, from = ValidatingData, to = Failed, event = WorkflowEvent.DataValidationFailed(problems))
    }

    @Test
    fun `given the photo check, when the photo passes, then it moves to risk assessment`() {
        val change = CheckingPhoto.pass()

        assertChange(change, from = CheckingPhoto, to = RiskAssessment, event = WorkflowEvent.PhotoCheckPassed)
    }

    @Test
    fun `given the photo check, when the photo fails, then it moves to failed and the event carries the problems`() {
        val problems = setOf(PhotoProblem.UnsupportedType, PhotoProblem.TooSmall)

        val change = CheckingPhoto.fail(problems)

        assertChange(change, from = CheckingPhoto, to = Failed, event = WorkflowEvent.PhotoCheckFailed(problems))
    }

    @Test
    fun `given risk assessment, when no review is needed, then it is approved automatically`() {
        val change = RiskAssessment.approveAutomatically()

        assertChange(change, from = RiskAssessment, to = Approved, event = WorkflowEvent.AutoApproved)
    }

    @Test
    fun `given risk assessment, when a review is required, then it moves to pending manual review with the reasons`() {
        val reasons = setOf(ReviewReason.Under21, ReviewReason.CategoryNotB)

        val change = RiskAssessment.requireManualReview(reasons)

        assertChange(
            change,
            from = RiskAssessment,
            to = PendingManualReview,
            event = WorkflowEvent.ManualReviewRequired(reasons),
        )
    }

    @Test
    fun `given pending manual review, when the reviewer approves, then it is approved`() {
        val change = PendingManualReview.approve()

        assertChange(change, from = PendingManualReview, to = Approved, event = WorkflowEvent.ManuallyApproved)
    }

    @Test
    fun `given pending manual review, when the reviewer rejects with a reason, then it is rejected with that reason`() {
        val reason = assertNotNull(RejectionReason.parse("The photo doesn't show the applicant"))

        val change = PendingManualReview.reject(reason)

        assertChange(change, from = PendingManualReview, to = Rejected, event = WorkflowEvent.ManuallyRejected(reason))
    }

    @Test
    fun `given any processing step, when processing is abandoned, then it moves to failed with a technical cause`() {
        val processingSteps = listOf(ValidatingData, CheckingPhoto, RiskAssessment)

        processingSteps.forEach { step ->
            val change = step.abandon(attempts = 5)

            assertChange(change, from = step, to = Failed, event = WorkflowEvent.ProcessingAbandoned(attempts = 5))
        }
    }

    @Test
    fun `given no reasons, when a check fails or a review is required, then the change is refused`() {
        assertFailsWith<IllegalArgumentException> { ValidatingData.fail(problems = emptySet()) }
        assertFailsWith<IllegalArgumentException> { CheckingPhoto.fail(problems = emptySet()) }
        assertFailsWith<IllegalArgumentException> { RiskAssessment.requireManualReview(reasons = emptySet()) }
    }

    private fun assertChange(
        change: StatusChange<ApplicationStatus>,
        from: ApplicationStatus?,
        to: ApplicationStatus,
        event: WorkflowEvent,
    ) {
        assertEquals(from, change.from)
        assertEquals(to, change.to)
        assertEquals(event, change.event)
    }
}
