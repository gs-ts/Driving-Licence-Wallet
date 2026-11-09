package gts.drivinglicencewallet.data.photo

import gts.drivinglicencewallet.data.database.LicenceApplicationDao
import gts.drivinglicencewallet.domain.application.PhotoReference
import kotlin.time.Instant

/**
 * The start-up clean-up of photos that no application refers to, such as one
 * that was stored just before a crash.
 */
class PhotoCleanUp internal constructor(
    private val dao: LicenceApplicationDao,
    private val photoStorage: EncryptedPhotoStorage,
) {

    /**
     * Deletes the photos that no application refers to and that were written
     * before [appStartedAt]. If the database cannot be read, this throws before
     * it deletes anything, because an empty set of references would delete
     * every old photo.
     */
    suspend fun deleteUnreferencedPhotos(appStartedAt: Instant) {
        val inUse = dao.findPhotoReferences()
            .map { reference -> PhotoReference(value = reference) }
            .toSet()
        photoStorage.deleteUnreferenced(inUse = inUse, writtenBefore = appStartedAt)
    }
}
