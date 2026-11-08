package gts.drivinglicencewallet.data.database

import androidx.room3.withWriteTransaction
import gts.drivinglicencewallet.data.database.entities.ApplicationWithHistory
import gts.drivinglicencewallet.data.database.entities.toApplicationOrNull
import gts.drivinglicencewallet.data.database.entities.toEntity
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.ApplicationStore
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.RecordResult
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.StatusChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import kotlin.time.Instant

/**
 * Keeps the applications in the encrypted database.
 *
 * [record] checks the status and saves the change in one write transaction, so two runs can't both
 * make the same move. An application that can't be read back is left out and logged by its ID only.
 */
internal class RoomApplicationStore(private val database: LicenceDatabase) : ApplicationStore {

    private val dao = database.applicationDao()

    override suspend fun add(application: LicenceApplication) {
        database.withWriteTransaction {
            dao.insertApplication(application = application.toEntity())
            dao.insertHistory(events = application.history.map { entry -> entry.toEntity(applicationId = application.id) })
        }
    }

    override suspend fun find(id: ApplicationId): LicenceApplication? = dao.find(id = id.value)?.readOrSkip()

    override suspend fun findInProgress(): LicenceApplication? =
        dao.findAll()
            .mapNotNull { saved -> saved.readOrSkip() }
            .firstOrNull { application -> application.status !is ApplicationStatus.Final }

    /** Returns the application as it was saved, read back in the same transaction. */
    override suspend fun record(
        id: ApplicationId,
        change: StatusChange<ApplicationStatus>,
        timestamp: Instant,
    ): RecordResult = database.withWriteTransaction {
        val current = dao.find(id = id.value)?.readOrSkip()
        val changed = current?.record(change = change, timestamp = timestamp)
        when {
            current == null -> RecordResult.NotFound
            changed == null -> RecordResult.Refused(current = current)
            else -> {
                dao.insertHistory(events = listOf(element = changed.history.last().toEntity(applicationId = id)))
                val saved = checkNotNull(value = dao.find(id = id.value)?.readOrSkip()) { "A recorded application can't be read back." }
                RecordResult.Recorded(application = saved)
            }
        }
    }

    override fun observe(id: ApplicationId): Flow<LicenceApplication?> =
        dao.observe(id = id.value)
            .map { saved -> saved?.readOrSkip() }
            .distinctUntilChanged()

    /** The application, or null if its saved data can't be read. Logs only the ID; the rest is personal data. */
    private fun ApplicationWithHistory.readOrSkip(): LicenceApplication? {
        val restored = toApplicationOrNull()
        if (restored == null) Timber.w("Skipped application %s: its saved data can't be read.", application.id)
        return restored
    }
}
