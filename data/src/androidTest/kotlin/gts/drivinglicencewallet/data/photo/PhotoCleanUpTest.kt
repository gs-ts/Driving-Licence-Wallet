package gts.drivinglicencewallet.data.photo

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.integration.android.AndroidKeystore
import gts.drivinglicencewallet.data.database.RoomLicenceApplicationRepository
import gts.drivinglicencewallet.data.database.TestDatabaseFiles
import gts.drivinglicencewallet.data.database.applicantDetails
import gts.drivinglicencewallet.data.database.submittedAt
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.PhotoReference
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The start-up clean-up on the real encrypted database and photo storage. */
@RunWith(AndroidJUnit4::class)
class PhotoCleanUpTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = TestDatabaseFiles(name = "clean-up-test")
    private val directory = File(context.noBackupFilesDir, "clean-up-test-photos")
    private val keysetFile = File(context.noBackupFilesDir, "clean-up-test-photos.keyset")
    private val keystoreAlias = "clean-up-test-photo-key"
    private val pickedPhotos = File(context.cacheDir, "clean-up-test-picked-photos")
    private val storage = EncryptedPhotoStorage(
        contentResolver = context.contentResolver,
        directory = directory,
        key = PhotoKey(keysetFile = keysetFile, photosDirectory = directory, keystoreAlias = keystoreAlias),
    )

    @BeforeTest
    fun deleteLeftovers() {
        deleteFiles()
    }

    @AfterTest
    fun deleteFiles() {
        files.delete()
        directory.deleteRecursively()
        keysetFile.delete()
        pickedPhotos.deleteRecursively()
        if (AndroidKeystore.hasKey(keystoreAlias)) AndroidKeystore.deleteKey(keystoreAlias)
    }

    @Test
    fun givenAPhotoInUseAndAnOrphanedPhoto_whenTheAppStarts_thenOnlyTheOrphanIsDeleted() = runTest {
        val inUse = storeOldPhoto(id = "in-use")
        storeOldPhoto(id = "orphan")
        val database = files.open()
        RoomLicenceApplicationRepository(database = database).add(application = applicationWith(photo = inUse))

        PhotoCleanUp(dao = database.licenceApplicationDao(), photoStorage = storage).deleteUnreferencedPhotos(appStartedAt = appStartedAt)

        assertEquals(expected = setOf(inUse), actual = storedPhotos())
        database.close()
    }

    @Test
    fun givenAPhotoInUseAndADatabaseThatCantBeRead_whenTheAppStarts_thenNoPhotoIsDeleted() = runTest {
        val inUse = storeOldPhoto(id = "in-use")
        val database = files.open()
        RoomLicenceApplicationRepository(database = database).add(application = applicationWith(photo = inUse))
        database.close()
        // A key that can't open the database, as when the Keystore fails for a moment.
        val withAnotherKey = files.open(key = { ByteArray(size = 32) })

        assertFails {
            PhotoCleanUp(dao = withAnotherKey.licenceApplicationDao(), photoStorage = storage).deleteUnreferencedPhotos(appStartedAt = appStartedAt)
        }

        assertEquals(expected = setOf(inUse), actual = storedPhotos())
        withAnotherKey.close()
    }

    /** Stores a photo for application [id], written before the app started. */
    private suspend fun storeOldPhoto(id: String): PhotoReference {
        pickedPhotos.mkdirs()
        val picked = File(pickedPhotos, "photo.jpg").apply { writeBytes(array = ByteArray(size = 1_000)) }
        val photo = assertNotNull(actual = storage.store(source = PhotoSource(value = Uri.fromFile(picked).toString()), id = ApplicationId(value = id)))
        assertTrue(actual = File(directory, photo.value).setLastModified((appStartedAt - 1.minutes).toEpochMilliseconds()))
        return photo
    }

    private fun applicationWith(photo: PhotoReference): LicenceApplication =
        LicenceApplication.submit(id = ApplicationId(value = photo.value), details = applicantDetails(), photo = photo, timestamp = submittedAt)

    private fun storedPhotos(): Set<PhotoReference> = directory.list().orEmpty().map { name -> PhotoReference(value = name) }.toSet()

    private companion object {

        /** When the app started, for the clean-up. */
        val appStartedAt: Instant = Instant.parse(input = "2026-03-02T09:00:00Z")
    }
}
