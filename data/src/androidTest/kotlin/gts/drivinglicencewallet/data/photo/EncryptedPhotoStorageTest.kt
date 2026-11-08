package gts.drivinglicencewallet.data.photo

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.integration.android.AndroidKeystore
import gts.drivinglicencewallet.data.database.containsSequence
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.PhotoReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncryptedPhotoStorageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.noBackupFilesDir, "photo-test")
    private val keysetFile = File(context.noBackupFilesDir, "photo-test.keyset")
    private val keystoreAlias = "photo-test-key"
    private val pickedPhotos = File(context.cacheDir, "picked-photos")

    @BeforeTest
    fun deleteLeftovers() {
        deleteFiles()
    }

    @AfterTest
    fun deleteFiles() {
        directory.deleteRecursively()
        keysetFile.delete()
        pickedPhotos.deleteRecursively()
        if (AndroidKeystore.hasKey(keystoreAlias)) AndroidKeystore.deleteKey(keystoreAlias)
    }

    @Test
    fun givenAPickedPhoto_whenItIsStored_thenItReadsBackTheSameBytesAfterARestart() = runTest {
        val photo = pickedPhoto()

        val reference = assertNotNull(actual = storage().store(source = photo.source, id = ApplicationId(value = "application-1")))

        assertContentEquals(expected = photo.bytes, actual = storage().read(photo = reference))
    }

    @Test
    fun givenAStoredPhoto_whenItsFileIsRead_thenItHoldsNoJpegHeaderOrPhotoBytes() = runTest {
        val photo = pickedPhoto()
        val reference = assertNotNull(actual = storage().store(source = photo.source, id = ApplicationId(value = "application-1")))
        val middle = photo.bytes.size / 2
        val photoSample = photo.bytes.copyOfRange(fromIndex = middle, toIndex = middle + 32)

        val saved = File(directory, reference.value).readBytes()

        assertFalse(actual = saved.copyOf(newSize = JPEG_START.size).contentEquals(other = JPEG_START))
        assertFalse(actual = saved.containsSequence(sequence = photoSample))
    }

    @Test
    fun givenAPhotoThatCantBeRead_whenItIsStored_thenNothingIsStored() = runTest {
        // Never written, like a photo deleted after it was picked.
        val missing = PhotoSource(value = Uri.fromFile(File(pickedPhotos, "missing.jpg")).toString())

        val reference = storage().store(source = missing, id = ApplicationId(value = "application-1"))

        assertNull(actual = reference)
        assertEquals(expected = emptySet(), actual = storedPhotos())
    }

    @Test
    fun givenOldUnreferencedPhotos_whenTheAppStarts_thenOnlyThoseAreDeleted() = runTest {
        val storage = storage()
        val photo = pickedPhoto()
        val referenced = assertNotNull(actual = storage.store(source = photo.source, id = ApplicationId(value = "referenced")))
        val unreferencedOld = assertNotNull(actual = storage.store(source = photo.source, id = ApplicationId(value = "unreferenced-old")))
        val unreferencedNew = assertNotNull(actual = storage.store(source = photo.source, id = ApplicationId(value = "unreferenced-new")))
        setWrittenAt(photo = referenced, time = appStartedAt - 1.minutes)
        setWrittenAt(photo = unreferencedOld, time = appStartedAt - 1.minutes)
        setWrittenAt(photo = unreferencedNew, time = appStartedAt + 1.minutes)

        storage.deleteUnreferenced(inUse = setOf(referenced), writtenBefore = appStartedAt)

        assertEquals(expected = setOf(referenced, unreferencedNew), actual = storedPhotos())
    }

    @Test
    fun givenPhotosAndAKeyThatCantBeDecrypted_whenAPhotoIsRead_thenItIsUnreadableAndNothingIsDeleted() = runTest {
        val reference = assertNotNull(actual = storage().store(source = pickedPhoto().source, id = ApplicationId(value = "application-1")))
        val savedKeyset = keysetFile.readBytes()
        // The Keystore key is gone, as when the device's secure storage is reset.
        AndroidKeystore.deleteKey(keystoreAlias)

        val read = storage().read(photo = reference)

        assertNull(actual = read)
        assertEquals(expected = setOf(reference), actual = storedPhotos())
        assertContentEquals(expected = savedKeyset, actual = keysetFile.readBytes())
    }

    @Test
    fun givenNoPhotosAndAKeyThatCantBeDecrypted_whenAPhotoIsStored_thenANewKeyIsMade() = runTest {
        val photo = pickedPhoto()
        val deleted = assertNotNull(actual = storage().store(source = photo.source, id = ApplicationId(value = "deleted")))
        File(directory, deleted.value).delete()
        AndroidKeystore.deleteKey(keystoreAlias)

        val reference = assertNotNull(actual = storage().store(source = photo.source, id = ApplicationId(value = "application-1")))

        assertContentEquals(expected = photo.bytes, actual = storage().read(photo = reference))
    }

    /** A new storage with a new key reader, like the one the app makes after a restart. */
    private fun storage() = EncryptedPhotoStorage(
        contentResolver = context.contentResolver,
        directory = directory,
        key = PhotoKey(keysetFile = keysetFile, photosDirectory = directory, keystoreAlias = keystoreAlias),
        ioDispatcher = Dispatchers.IO
    )

    /** A JPEG-like photo of 2.5 MB, so it spans several of the 1 MB encrypted segments. */
    private fun pickedPhoto(): PickedPhoto {
        val bytes = JPEG_START + Random(seed = 1).nextBytes(size = 2_500_000)
        val file = File(pickedPhotos, "photo.jpg")
        pickedPhotos.mkdirs()
        file.writeBytes(array = bytes)
        return PickedPhoto(source = PhotoSource(value = Uri.fromFile(file).toString()), bytes = bytes)
    }

    private fun setWrittenAt(photo: PhotoReference, time: Instant) {
        assertTrue(actual = File(directory, photo.value).setLastModified(time.toEpochMilliseconds()))
    }

    private fun storedPhotos(): Set<PhotoReference> = directory.list().orEmpty().map { name -> PhotoReference(value = name) }.toSet()

    private class PickedPhoto(val source: PhotoSource, val bytes: ByteArray)

    private companion object {

        val JPEG_START = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        /** When the app started, for the clean-up. */
        val appStartedAt: Instant = Instant.parse(input = "2026-03-02T09:00:00Z")
    }
}
