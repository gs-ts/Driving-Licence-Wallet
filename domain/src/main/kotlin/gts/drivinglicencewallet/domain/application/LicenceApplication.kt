package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.HistoryEntry
import gts.drivinglicencewallet.domain.workflow.StatusChange
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import gts.drivinglicencewallet.domain.workflow.changeFor
import kotlin.time.Instant

/**
 * An application for a driving licence: the applicant's details, their photo, and its history.
 *
 * The status and the dates come from the history, so they always agree with it. The history
 * starts with the submission and grows by one entry with each [record]. Only [submit], [record]
 * and [restore] create applications, so a history always follows the workflow.
 */
@ConsistentCopyVisibility
data class LicenceApplication internal constructor(
    val id: ApplicationId,
    val details: ApplicantDetails,
    val photo: PhotoReference,
    val history: List<HistoryEntry>,
) {

    init {
        require(value = history.firstOrNull()?.event == WorkflowEvent.ApplicationSubmitted) {
            "An application's history starts with its submission."
        }
    }

    val status: ApplicationStatus
        get() = history.last().toStatus

    val createdAt: Instant
        get() = history.first().timestamp

    val updatedAt: Instant
        get() = history.last().timestamp

    /** When processing started, or null before then. The age rules use this time, so every run uses the same one. */
    val processingStartedAt: Instant?
        get() = history.firstOrNull { entry -> entry.event == WorkflowEvent.ProcessingStarted }?.timestamp

    /**
     * Adds [change] to the history, which moves the status.
     *
     * Returns null if the application isn't in the change's starting status ([StatusChange.from]).
     * For example, a second approval returns null, because the application is already Approved.
     */
    fun record(change: StatusChange<ApplicationStatus>, timestamp: Instant): LicenceApplication? {
        if (change.from != status) return null
        return copy(history = history + change.toHistoryEntry(timestamp = timestamp))
    }

    /** Records [saved] again as the move it stands for. Returns null if the current status has no such move. */
    private fun replay(saved: SavedEvent): LicenceApplication? =
        status.changeFor(event = saved.event)?.let { change -> record(change = change, timestamp = saved.timestamp) }

    companion object {

        /** A new application, with its submission as the first history entry. */
        fun submit(
            id: ApplicationId,
            details: ApplicantDetails,
            photo: PhotoReference,
            timestamp: Instant,
        ): LicenceApplication = LicenceApplication(
            id = id,
            details = details,
            photo = photo,
            history = listOf(element = ApplicationStatus.submit().toHistoryEntry(timestamp = timestamp))
        )

        /**
         * Rebuilds a saved application by replaying its [events] through the workflow, oldest first.
         *
         * Returns null if the events don't start with the submission, or if one of them isn't a move
         * from the status before it. So a damaged history never reaches the app.
         */
        fun restore(
            id: ApplicationId,
            details: ApplicantDetails,
            photo: PhotoReference,
            events: List<SavedEvent>,
        ): LicenceApplication? {
            val submission = events.firstOrNull()?.takeIf { saved -> saved.event == WorkflowEvent.ApplicationSubmitted }
            val submitted = submission?.let { saved ->
                submit(
                    id = id,
                    details = details,
                    photo = photo,
                    timestamp = saved.timestamp
                )
            }
            return events.drop(n = 1).fold(initial = submitted) { application, saved -> application?.replay(saved = saved) }
        }
    }
}

/** One line of a saved history: what happened, and when. [LicenceApplication.restore] rebuilds the statuses from these. */
data class SavedEvent(
    val event: WorkflowEvent,
    val timestamp: Instant,
)

/**
 * Identifies an application. The app creates it when the applicant submits, so it also works
 * as the submission's idempotency key.
 */
@JvmInline
value class ApplicationId(val value: String)

/** Where the app stored the applicant's photo. Only the photo storage knows what [value] means. */
@JvmInline
value class PhotoReference(val value: String)
