package gts.drivinglicencewallet.data.photo

import android.content.ContentResolver
import androidx.core.net.toUri
import com.google.crypto.tink.StreamingAead
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.application.PhotoStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.time.Instant

/** Reads a stored photo, such as for the issuer's photo check. */
internal interface PhotoReader {

    /** The photo's bytes, or null if its file is gone or can't be decrypted. */
    suspend fun read(photo: PhotoReference): ByteArray?
}

/**
 * Keeps each application's photo in [directory], encrypted with Tink's streaming AEAD and [key].
 *
 * Each photo is one file, named after its application's ID, and that name is its [PhotoReference].
 * The name is also the associated data, so a file renamed to another application's name can't be decrypted.
 * The logs name the application only: the photo's URI stays out of them.
 */
internal class EncryptedPhotoStorage(
    private val contentResolver: ContentResolver,
    private val directory: File,
    private val key: PhotoKey,
    private val ioDispatcher: CoroutineDispatcher,
) : PhotoStorage, PhotoReader {

    /** Read or made on first use, then kept, so two photos stored at once can't each make a new key. */
    private val streamingAead: StreamingAead by lazy { key.get() }

    /** Encrypts the photo while it copies it, 1 MB at a time, so a large photo is never all in memory. */
    override suspend fun store(source: PhotoSource, id: ApplicationId): PhotoReference? = withContext(context = ioDispatcher) {
        val file = File(directory, id.value)
        try {
            encrypt(source = source, file = file)
            PhotoReference(value = file.name)
        } catch (exception: IOException) {
            discard(file = file, error = exception)
        } catch (exception: GeneralSecurityException) {
            discard(file = file, error = exception)
        } catch (exception: SecurityException) {
            // The app may no longer read the URI, such as after a restart.
            discard(file = file, error = exception)
        }
    }

    override suspend fun read(photo: PhotoReference): ByteArray? = withContext(context = ioDispatcher) {
        try {
            decrypt(file = File(directory, photo.value))
        } catch (exception: IOException) {
            unreadable(photo = photo, error = exception)
        } catch (exception: GeneralSecurityException) {
            unreadable(photo = photo, error = exception)
        }
    }

    /**
     * Deletes the photos not in [inUse], such as one stored just before a crash, before its application was saved.
     * Only photos written before [writtenBefore] go. Pass the time the app started: a newer photo may belong to a
     * submit that hasn't saved its application yet.
     */
    suspend fun deleteUnreferenced(inUse: Set<PhotoReference>, writtenBefore: Instant) {
        withContext(context = ioDispatcher) {
            val unreferenced = directory.listFiles()
                .orEmpty()
                .filter { file -> PhotoReference(value = file.name) !in inUse }
                .filter { file -> file.lastModified() < writtenBefore.toEpochMilliseconds() }
            unreferenced.forEach { file -> file.delete() }

            if (unreferenced.isNotEmpty()) {
                Timber.i("Deleted %d unreferenced photos.", unreferenced.size)
            }
        }
    }

    /**
     * Opens the photo and gets the key before it creates the file. A photo that can't be opened then leaves
     * nothing behind, and the key, which checks whether any photo exists, doesn't count this one.
     */
    private fun encrypt(source: PhotoSource, file: File) {
        contentResolver.openInputStream(source.value.toUri())?.use { input ->
            // Before the file exists, so the key doesn't count it as a photo.
            val encrypter = streamingAead
            directory.mkdirs()
            FileOutputStream(file).use { output ->
                encrypter.newEncryptingStream(
                    output,
                    file.name.encodeToByteArray()
                ).use { encrypting ->
                    input.copyTo(out = encrypting)
                }
            }
        } ?: throw FileNotFoundException("The photo's provider has just crashed.")
    }

    private fun decrypt(file: File): ByteArray = FileInputStream(file).use { input ->
        streamingAead.newDecryptingStream(
            input,
            file.name.encodeToByteArray()
        ).use { decrypting ->
            decrypting.readBytes()
        }
    }

    /** Deletes what was written, and returns null. Logs only the error's type: its message can hold the photo's URI. */
    private fun discard(file: File, error: Exception): PhotoReference? {
        file.delete()
        Timber.w(
            "Couldn't store the photo of application %s: %s",
            file.name,
            error.javaClass.simpleName
        )
        return null
    }

    /** Returns null, and logs only the error's type. */
    private fun unreadable(photo: PhotoReference, error: Exception): ByteArray? {
        Timber.w(
            "Couldn't read the photo of application %s: %s",
            photo.value,
            error.javaClass.simpleName
        )
        return null
    }
}
