package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.HistoryEntry
import gts.drivinglicencewallet.domain.workflow.StatusChange
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import kotlin.time.Instant

/**
 * An application for a driving licence: the applicant's details, their photo, and its history.
 *
 * The status and the dates come from the history, so they always agree with it. The history
 * starts with the submission and grows by one entry with each [record].
 */
data class LicenceApplication(
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
     * Adds [change] to the history, which moves the status.
     *
     * Returns null when [change] doesn't start from the current status, for example because
     * a duplicate run already made it.
     */
    fun record(change: StatusChange<ApplicationStatus>, timestamp: Instant): LicenceApplication? {
        if (change.from != status) return null
        return copy(history = history + change.toHistoryEntry(timestamp = timestamp))
    }

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
    }
}

/**
 * Identifies an application. The app creates it when the applicant submits, so it also works
 * as the submission's idempotency key.
 */
@JvmInline
value class ApplicationId(val value: String)

/** Where the app stored the applicant's photo. Only the photo storage knows what [value] means. */
@JvmInline
value class PhotoReference(val value: String)
