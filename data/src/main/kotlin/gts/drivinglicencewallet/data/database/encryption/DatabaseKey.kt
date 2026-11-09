package gts.drivinglicencewallet.data.database.encryption

import android.util.AtomicFile
import androidx.core.util.writeBytes
import com.google.crypto.tink.integration.android.AndroidKeystore
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom

/**
 * The key that encrypts the database. It consists of random bytes, which are
 * saved in [keyFile], encrypted by a key in the Android Keystore
 * ([keystoreAlias]). The Keystore key cannot be copied off the device, so a
 * copy of the app's files cannot open the database.
 *
 * A new key is made only while [databaseFile] does not exist. Once it exists,
 * a key that cannot be read is an error, and nothing is deleted, because a new
 * key would lock the saved applications out for good.
 */
internal class DatabaseKey(
    private val keyFile: File,
    private val databaseFile: File,
    private val keystoreAlias: String,
) {

    /**
     * Reads the key, or makes one for a new database. It uses the Keystore and
     * the disk, so call it off the main thread.
     */
    fun get(): ByteArray = if (databaseFile.exists()) read() else create()

    private fun read(): ByteArray = try {
        AndroidKeystore.getAead(keystoreAlias).decrypt(
            AtomicFile(keyFile).readFully(),
            ASSOCIATED_DATA
        )
    } catch (exception: GeneralSecurityException) {
        throw IllegalStateException("Can't decrypt the database key, so the database can't be opened.", exception)
    } catch (exception: IOException) {
        throw IllegalStateException("Can't read the database key, so the database can't be opened.", exception)
    }

    private fun create(): ByteArray {
        val key = ByteArray(size = KEY_SIZE_BYTES).also { bytes -> SecureRandom().nextBytes(bytes) }
        AndroidKeystore.generateNewAes256GcmKey(keystoreAlias)
        // AtomicFile writes the whole file or nothing, so a crash cannot leave half a key.
        AtomicFile(keyFile).writeBytes(
            array = AndroidKeystore.getAead(keystoreAlias).encrypt(key, ASSOCIATED_DATA)
        )
        return key
    }

    private companion object {

        const val KEY_SIZE_BYTES = 32

        /**
         * Ties the encrypted key to this use, so that it cannot be decrypted as
         * a different secret.
         */
        val ASSOCIATED_DATA = "licence database key".encodeToByteArray()
    }
}
