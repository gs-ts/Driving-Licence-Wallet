package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.StatusChange
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Stores the applications.
 *
 * A status change and its history entry are saved together. So anything that reads an application
 * (the status screen, the worker, a test, or the app after a crash) never sees one without the other.
 */
interface LicenceApplicationRepository {

    suspend fun add(application: LicenceApplication)

    suspend fun find(id: ApplicationId): LicenceApplication?

    /** The application that isn't finished yet, if there is one. */
    suspend fun findInProgress(): LicenceApplication?

    /**
     * Saves [change] only if the application is still in the change's starting status ([StatusChange.from]).
     * For example, an approval starts from PendingManualReview, so once the application is Approved, a second
     * approval is refused. A duplicate run's step is refused the same way.
     */
    suspend fun record(
        id: ApplicationId,
        change: StatusChange<ApplicationStatus>,
        timestamp: Instant,
    ): RecordResult

    /** Emits the application each time it changes, or null if there's no such application. */
    fun observe(id: ApplicationId): Flow<LicenceApplication?>
}

sealed interface RecordResult {

    data class Recorded(val application: LicenceApplication) : RecordResult

    /**
     * The change wasn't saved, because the application isn't in the change's starting status.
     * [current] is the application as it is now.
     */
    data class Refused(val current: LicenceApplication) : RecordResult

    data object NotFound : RecordResult
}
