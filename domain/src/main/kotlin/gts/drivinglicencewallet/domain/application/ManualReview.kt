package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.RejectionReason
import gts.drivinglicencewallet.domain.workflow.StatusChange
import kotlin.time.Clock

/**
 * Lets a reviewer approve or reject an application that is waiting for review. The decision goes into its history.
 * A decision on any other application is refused.
 */
class ManualReview(
    private val store: ApplicationStore,
    private val clock: Clock,
) {

    suspend fun approve(id: ApplicationId): ReviewResult = recordDecision(
        id = id,
        change = ApplicationStatus.PendingManualReview.approve()
    )

    suspend fun reject(id: ApplicationId, reason: RejectionReason): ReviewResult = recordDecision(
        id = id,
        change = ApplicationStatus.PendingManualReview.reject(reason = reason)
    )

    /** The store checks that the application is still pending when it saves, so a double tap can't record two decisions. */
    private suspend fun recordDecision(id: ApplicationId, change: StatusChange<ApplicationStatus>): ReviewResult {
        val result = store.record(
            id = id,
            change = change,
            timestamp = clock.now()
        )
        return when (result) {
            is RecordResult.Recorded -> ReviewResult.Recorded(application = result.application)
            is RecordResult.Refused -> ReviewResult.NotPendingReview(status = result.current.status)
            RecordResult.NotFound -> ReviewResult.NotFound
        }
    }
}

sealed interface ReviewResult {

    /** [application] is now approved or rejected. */
    data class Recorded(val application: LicenceApplication) : ReviewResult

    /** Refused: the application is in [status], not waiting for review. */
    data class NotPendingReview(val status: ApplicationStatus) : ReviewResult

    data object NotFound : ReviewResult
}
