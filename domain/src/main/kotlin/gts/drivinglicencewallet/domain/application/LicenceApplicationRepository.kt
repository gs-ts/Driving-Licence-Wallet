package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.StatusChange
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** Stores the applications. */
interface LicenceApplicationRepository {

    suspend fun add(application: LicenceApplication)

    suspend fun find(id: ApplicationId): LicenceApplication?

    /** The application that is not finished yet, if there is one. */
    suspend fun findInProgress(): LicenceApplication?

    /**
     * Saves [change] only if the application is still in the change's starting
     * status ([StatusChange.from]). For example, an approval starts from
     * PendingManualReview, so once the application is Approved, a second
     * approval is refused. A step of a duplicate processing run is refused in
     * the same way.
     */
    suspend fun record(
        id: ApplicationId,
        change: StatusChange<ApplicationStatus>,
        timestamp: Instant,
    ): RecordResult

    /**
     * Emits the application each time it changes, or null if there is no
     * application with this ID.
     */
    fun observe(id: ApplicationId): Flow<LicenceApplication?>
}

sealed interface RecordResult {

    data class Recorded(val application: LicenceApplication) : RecordResult

    /**
     * The change was not saved, because the application is not in the change's
     * starting status. [current] is the application as it is now.
     */
    data class Refused(val current: LicenceApplication) : RecordResult

    data object NotFound : RecordResult
}
