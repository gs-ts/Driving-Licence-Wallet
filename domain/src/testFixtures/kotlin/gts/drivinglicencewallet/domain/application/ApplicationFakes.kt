package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.applicant.completeDetails
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.StatusChange
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** An in-memory [LicenceApplicationRepository]. */
class FakeLicenceApplicationRepository : LicenceApplicationRepository {

    private val applications = MutableStateFlow<Map<ApplicationId, LicenceApplication>>(emptyMap())
    private val recordLock = Mutex()

    /** Returns the stored application without suspending. */
    operator fun get(id: ApplicationId): LicenceApplication? = applications.value[id]

    override suspend fun add(application: LicenceApplication) {
        applications.update { stored -> stored + (application.id to application) }
    }

    override suspend fun find(id: ApplicationId): LicenceApplication? = applications.value[id]

    override suspend fun findInProgress(): LicenceApplication? =
        applications.value.values.firstOrNull { application -> application.status !is ApplicationStatus.Final }

    override suspend fun findLatest(): LicenceApplication? = applications.value.values.maxByOrNull { application -> application.createdAt }

    override suspend fun record(id: ApplicationId, change: StatusChange<ApplicationStatus>, timestamp: Instant): RecordResult =
        recordLock.withLock {
            val stored = applications.value[id] ?: return@withLock RecordResult.NotFound
            val changed = stored.record(change = change, timestamp = timestamp) ?: return@withLock RecordResult.Refused(current = stored)
            applications.update { all -> all + (id to changed) }
            RecordResult.Recorded(application = changed)
        }

    override fun observe(id: ApplicationId): Flow<LicenceApplication?> =
        applications.map { all -> all[id] }.distinctUntilChanged()
}

/**
 * Names each photo after its application, or fails when [canStore] is false.
 * Storing a photo takes [storeDuration].
 */
class FakePhotoStorage(
    private val canStore: Boolean = true,
    private val storeDuration: Duration = Duration.ZERO,
) : PhotoStorage {

    override suspend fun store(source: PhotoSource, id: ApplicationId): PhotoReference? {
        delay(duration = storeDuration)
        return if (canStore) PhotoReference("photo-${id.value}") else null
    }
}

/** A clock that always returns [instant]. */
class FixedClock(private val instant: Instant) : Clock {

    override fun now(): Instant = instant
}

/** An application that has just been submitted with the [completeDetails]. */
fun submittedApplication(id: ApplicationId, timestamp: Instant): LicenceApplication = LicenceApplication.submit(
    id = id,
    details = completeDetails(),
    photo = PhotoReference("photo-${id.value}"),
    timestamp = timestamp,
)

/** Records [change], or throws an exception if the change is refused. */
fun LicenceApplication.recordOrFail(change: StatusChange<ApplicationStatus>, timestamp: Instant): LicenceApplication =
    checkNotNull(value = record(change = change, timestamp = timestamp)) { "Refused the change from ${change.from} to ${change.to}" }
