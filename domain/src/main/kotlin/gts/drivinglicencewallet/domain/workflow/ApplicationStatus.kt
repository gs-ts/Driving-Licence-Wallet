package gts.drivinglicencewallet.domain.workflow

/**
 * Where an application is in the onboarding workflow.
 *
 * Each status offers only the moves the workflow allows, so an illegal move doesn't compile:
 * [CheckingPhoto] has no `approve()`, and the final statuses have no moves at all.
 * A move returns a [StatusChange], which is the only way to change a status. Once saved,
 * the change becomes a [HistoryEntry] in the application's audit trail.
 *
 * ```
 * Submitted → ValidatingData → CheckingPhoto → RiskAssessment → Approved
 *                   ↓                ↓               ↓
 *                 Failed           Failed   PendingManualReview → Approved or Rejected
 * ```
 *
 * Any processing step can also end in [Failed] when processing gives up after repeated
 * technical errors.
 */
sealed interface ApplicationStatus {

    /** Saved on the device and waiting to be processed. */
    data object Submitted : ApplicationStatus {

        fun startProcessing(): StatusChange<ValidatingData> = StatusChange(
            from = this,
            to = ValidatingData,
            event = WorkflowEvent.ProcessingStarted
        )
    }

    /** One of the processing steps is running. Processing can give up during any of them. */
    sealed interface Processing : ApplicationStatus {

        /** Gives up after [attempts] tries that failed for technical reasons. */
        fun abandon(attempts: Int): StatusChange<Failed> = StatusChange(
            from = this,
            to = Failed,
            event = WorkflowEvent.ProcessingAbandoned(attempts = attempts)
        )
    }

    /** Checking the applicant's details: required fields, email and minimum age. */
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

    /** Checking the photo: present, a supported type, large enough and of good enough quality. */
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

    /** Deciding whether the application can be approved automatically or needs a reviewer. */
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

    /** Waiting for a reviewer to approve or reject it. */
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

        /** The first change of every application. It's the only change with no previous status. */
        fun submit(): StatusChange<Submitted> = StatusChange(
            from = null,
            to = Submitted,
            event = WorkflowEvent.ApplicationSubmitted
        )
    }
}
