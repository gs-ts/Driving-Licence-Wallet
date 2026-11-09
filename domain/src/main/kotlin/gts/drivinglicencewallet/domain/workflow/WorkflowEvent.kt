package gts.drivinglicencewallet.domain.workflow

import gts.drivinglicencewallet.domain.applicant.ApplicantField

/**
 * What happened to an application. Every [StatusChange] and [HistoryEntry]
 * carries one.
 *
 * A failed check or a manual review always has at least one reason, so the
 * history can say why. The app turns the reasons into text when it shows the
 * history.
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

    /**
     * Processing gave up after [attempts] tries, because of technical errors
     * such as an unreachable service.
     */
    data class ProcessingAbandoned(val attempts: Int) : WorkflowEvent {
        init {
            require(value = attempts > 0) { "Processing can only give up after at least one attempt." }
        }
    }
}

/** Why the applicant's details failed data validation. */
sealed interface DataProblem {

    data class MissingField(val field: ApplicantField) : DataProblem

    data object InvalidEmail : DataProblem

    /** The applicant was younger than 18 on the day processing started. */
    data object Under18 : DataProblem
}

/** Why the photo failed the photo check. */
enum class PhotoProblem {

    Missing,

    UnsupportedType,

    /** The file size is below the minimum. */
    TooSmall,

    /** The quality score is below the minimum, so the photo cannot be used. */
    LowQuality,
}

/**
 * Why the risk assessment sent the application to a reviewer instead of
 * approving it.
 */
enum class ReviewReason {

    /** The applicant was younger than 21 on the day processing started. */
    Under21,

    CategoryNotB,

    /** The national ID is on the watchlist. */
    OnWatchlist,

    /**
     * The photo passed the check, but its quality score is below the review
     * threshold.
     */
    LowPhotoQuality,
}

/**
 * Why a reviewer rejected an application, in the reviewer's own words. It is
 * never blank.
 */
@JvmInline
value class RejectionReason private constructor(val text: String) {

    companion object {

        /** Removes surrounding spaces, and returns null if [text] is blank. */
        fun parse(text: String): RejectionReason? {
            val trimmedText = text.trim()
            return if (trimmedText.isEmpty()) null else RejectionReason(text = trimmedText)
        }
    }
}
