package gts.drivinglicencewallet.domain.workflow

/**
 * What happened to an application. Every [StatusChange] and [HistoryEntry] carries one.
 *
 * A failed check or a manual review always has at least one reason, so the history can
 * say why. The app turns the reasons into text when it shows the history.
 */
sealed interface WorkflowEvent {

    data object ApplicationSubmitted : WorkflowEvent

    data object ProcessingStarted : WorkflowEvent

    data object DataValidationPassed : WorkflowEvent

    data class DataValidationFailed(val problems: Set<DataProblem>) : WorkflowEvent {
        init {
            require(value = problems.isNotEmpty()) { "A failed data validation needs at least one problem." }
        }
    }

    data object PhotoCheckPassed : WorkflowEvent

    data class PhotoCheckFailed(val problems: Set<PhotoProblem>) : WorkflowEvent {
        init {
            require(value = problems.isNotEmpty()) { "A failed photo check needs at least one problem." }
        }
    }

    data object AutoApproved : WorkflowEvent

    data class ManualReviewRequired(val reasons: Set<ReviewReason>) : WorkflowEvent {
        init {
            require(value = reasons.isNotEmpty()) { "A manual review needs at least one reason." }
        }
    }

    data object ManuallyApproved : WorkflowEvent

    data class ManuallyRejected(val reason: RejectionReason) : WorkflowEvent

    /** Processing gave up after [attempts] tries that failed for technical reasons, such as an unreachable service. */
    data class ProcessingAbandoned(val attempts: Int) : WorkflowEvent {
        init {
            require(value = attempts > 0) { "Processing can only give up after at least one attempt." }
        }
    }
}
