package gts.drivinglicencewallet.domain.workflow

import kotlin.time.Instant

/**
 * Where an application is in the onboarding workflow.
 *
 * Each status offers only the moves that the workflow allows, so an illegal
 * move does not compile. For example, [CheckingPhoto] has no `approve()`, and
 * the final statuses have no moves at all. A move returns a [StatusChange],
 * which is the only way to change a status. Once it is saved, the change
 * becomes a [HistoryEntry] in the application's audit trail.
 *
 * ```
 * Submitted → ValidatingData → CheckingPhoto → RiskAssessment → Approved
 *                   ↓                ↓               ↓
 *                 Failed           Failed   PendingManualReview → Approved or Rejected
 * ```
 *
 * Any processing step can also end in [Failed] when processing gives up after
 * repeated technical errors.
 */
sealed interface ApplicationStatus {

    /** The application is saved on the device and waits to be processed. */
    data object Submitted : ApplicationStatus {

        fun startProcessing(): StatusChange<ValidatingData> = StatusChange(
            from = this,
            to = ValidatingData,
            event = WorkflowEvent.ProcessingStarted
        )
    }

    /**
     * One of the processing steps is running. Processing can give up during
     * any of them.
     */
    sealed interface Processing : ApplicationStatus {

        /** Gives up after [attempts] tries, because of technical errors. */
        fun abandon(attempts: Int): StatusChange<Failed> = StatusChange(
            from = this,
            to = Failed,
            event = WorkflowEvent.ProcessingAbandoned(attempts = attempts)
        )
    }

    /**
     * The applicant's details are being checked for the required fields, a
     * valid email and the minimum age.
     */
    data object ValidatingData : Processing {

        fun pass(): StatusChange<CheckingPhoto> = StatusChange(
            from = this,
            to = CheckingPhoto,
            event = WorkflowEvent.DataValidationPassed
        )

        fun fail(problems: Set<DataProblem>): StatusChange<Failed> = StatusChange(
            from = this,
            to = Failed,
            event = WorkflowEvent.DataValidationFailed(problems = problems)
        )
    }

    /**
     * The photo is being checked. It must be present, of a supported type,
     * large enough and of good enough quality.
     */
    data object CheckingPhoto : Processing {

        fun pass(): StatusChange<RiskAssessment> = StatusChange(
            from = this,
            to = RiskAssessment,
            event = WorkflowEvent.PhotoCheckPassed
        )

        fun fail(problems: Set<PhotoProblem>): StatusChange<Failed> = StatusChange(
            from = this,
            to = Failed,
            event = WorkflowEvent.PhotoCheckFailed(problems = problems)
        )
    }

    /**
     * The application is being assessed, to decide whether it can be approved
     * automatically or needs a reviewer.
     */
    data object RiskAssessment : Processing {

        fun approveAutomatically(): StatusChange<Approved> = StatusChange(
            from = this,
            to = Approved,
            event = WorkflowEvent.AutoApproved
        )

        fun requireManualReview(reasons: Set<ReviewReason>): StatusChange<PendingManualReview> = StatusChange(
            from = this,
            to = PendingManualReview,
            event = WorkflowEvent.ManualReviewRequired(reasons = reasons)
        )
    }

    /** The application is waiting for a reviewer to approve or reject it. */
    data object PendingManualReview : ApplicationStatus {

        fun approve(): StatusChange<Approved> = StatusChange(
            from = this,
            to = Approved,
            event = WorkflowEvent.ManuallyApproved
        )

        fun reject(reason: RejectionReason): StatusChange<Rejected> = StatusChange(
            from = this,
            to = Rejected,
            event = WorkflowEvent.ManuallyRejected(reason = reason)
        )
    }

    /** The workflow has ended. A final status never changes. */
    sealed interface Final : ApplicationStatus

    data object Approved : Final

    data object Rejected : Final

    /** A check failed, or processing gave up. The history entry says which. */
    data object Failed : Final

    companion object {

        /**
         * The first change of every application. It is the only change without
         * a previous status.
         */
        fun submit(): StatusChange<Submitted> = StatusChange(
            from = null,
            to = Submitted,
            event = WorkflowEvent.ApplicationSubmitted
        )
    }
}

/**
 * Returns the move from this status that [event] describes, or null if this
 * status has no such move. Restoring a saved application replays its events
 * through this function.
 */
internal fun ApplicationStatus.changeFor(event: WorkflowEvent): StatusChange<ApplicationStatus>? {
    return when (event) {
        // The submission only starts a history, so no status moves to it.
        WorkflowEvent.ApplicationSubmitted -> null
        WorkflowEvent.ProcessingStarted -> (this as? ApplicationStatus.Submitted)?.startProcessing()
        WorkflowEvent.DataValidationPassed -> (this as? ApplicationStatus.ValidatingData)?.pass()
        is WorkflowEvent.DataValidationFailed -> (this as? ApplicationStatus.ValidatingData)?.fail(problems = event.problems)
        WorkflowEvent.PhotoCheckPassed -> (this as? ApplicationStatus.CheckingPhoto)?.pass()
        is WorkflowEvent.PhotoCheckFailed -> (this as? ApplicationStatus.CheckingPhoto)?.fail(problems = event.problems)
        WorkflowEvent.AutoApproved -> (this as? ApplicationStatus.RiskAssessment)?.approveAutomatically()
        is WorkflowEvent.ManualReviewRequired -> (this as? ApplicationStatus.RiskAssessment)?.requireManualReview(reasons = event.reasons)
        WorkflowEvent.ManuallyApproved -> (this as? ApplicationStatus.PendingManualReview)?.approve()
        is WorkflowEvent.ManuallyRejected -> (this as? ApplicationStatus.PendingManualReview)?.reject(reason = event.reason)
        is WorkflowEvent.ProcessingAbandoned -> (this as? ApplicationStatus.Processing)?.abandon(attempts = event.attempts)
    }
}

/**
 * A move from one [ApplicationStatus] to the next, with the [WorkflowEvent]
 * that caused it.
 *
 * Only the workflow in this module can create one, so other modules can save
 * a change but cannot make up an illegal one. [from] is null only for a new
 * application.
 */
class StatusChange<out S : ApplicationStatus> internal constructor(
    val from: ApplicationStatus?,
    val to: S,
    val event: WorkflowEvent,
) {

    fun toHistoryEntry(timestamp: Instant): HistoryEntry = HistoryEntry(
        timestamp = timestamp,
        fromStatus = from,
        toStatus = to,
        event = event
    )
}

/**
 * One entry in an application's audit trail. It records what happened and
 * when, with the status before and after.
 *
 * [fromStatus] is null only for the first entry, when the application was
 * submitted. Only the workflow creates entries, so each one is a move that the
 * workflow allows.
 */
@ConsistentCopyVisibility
data class HistoryEntry internal constructor(
    val timestamp: Instant,
    val fromStatus: ApplicationStatus?,
    val toStatus: ApplicationStatus,
    val event: WorkflowEvent,
)
