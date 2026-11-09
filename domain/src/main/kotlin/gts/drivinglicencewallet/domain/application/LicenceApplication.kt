package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.HistoryEntry
import gts.drivinglicencewallet.domain.workflow.StatusChange
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import gts.drivinglicencewallet.domain.workflow.changeFor
import kotlin.time.Instant

/**
 * An application for a driving licence. It holds the applicant's details,
 * their photo and the application's history.
 *
 * The status and the dates are read from the history, so they always agree
 * with it. The history starts with the submission and grows by one entry with
 * each [record]. Only [submit], [record] and [restore] create applications, so
 * every history follows the workflow.
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

    /**
     * When processing started, or null before then. The age checks use this
     * time, so that every run checks the age on the same date.
     */
    val processingStartedAt: Instant?
        get() = history.firstOrNull { entry -> entry.event == WorkflowEvent.ProcessingStarted }?.timestamp

    /**
     * Adds [change] to the history, which changes the status.
     *
     * Returns null if the application is not in the change's starting status
     * ([StatusChange.from]). For example, a second approval returns null,
     * because the application is already Approved.
     */
    fun record(change: StatusChange<ApplicationStatus>, timestamp: Instant): LicenceApplication? {
        if (change.from != status) return null
        return copy(history = history + change.toHistoryEntry(timestamp = timestamp))
    }

    /**
     * Records the status change that [saved] describes. Returns null if the
     * current status does not allow that change.
     */
    private fun replay(saved: SavedEvent): LicenceApplication? =
        status.changeFor(event = saved.event)?.let { change -> record(change = change, timestamp = saved.timestamp) }

    companion object {

        /** Creates an application whose history starts with its submission. */
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
         * Rebuilds a saved application by replaying its [events] through the
         * workflow, oldest first.
         *
         * Returns null if the events do not start with the submission, or if
         * one of them is not a valid change from the status before it. This
         * way, a damaged history never reaches the app.
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

/**
 * One event of a saved history, with what happened and when.
 * [LicenceApplication.restore] rebuilds the statuses from these events.
 */
data class SavedEvent(
    val event: WorkflowEvent,
    val timestamp: Instant,
)

/**
 * Identifies an application. The app creates the ID when the applicant
 * submits, so it also works as the submission's idempotency key.
 */
@JvmInline
value class ApplicationId(val value: String)

/**
 * Where the app stored the applicant's photo. Only the photo storage knows
 * what [value] means.
 */
@JvmInline
value class PhotoReference(val value: String)
