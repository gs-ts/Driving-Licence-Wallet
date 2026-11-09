package gts.drivinglicencewallet.data.photo

import android.util.AtomicFile
import androidx.core.util.writeBytes
import com.google.crypto.tink.Configuration
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig2026
import java.io.File
import java.io.IOException

/**
 * The key that encrypts the photos. It is a Tink keyset, which is saved in
 * [keysetFile], encrypted by a key in the Android Keystore ([keystoreAlias]).
 * The Keystore key cannot be copied off the device, so a copy of the app's
 * files cannot decrypt the photos.
 *
 * A new key is made only while [photosDirectory] holds no photos. Once it does,
 * a key that cannot be read is an error, and nothing is deleted, because a new
 * key would lock the saved photos out for good.
 */
internal class PhotoKey(
    private val keysetFile: File,
    private val photosDirectory: File,
    private val keystoreAlias: String,
) {

    /**
     * Reads the key, or makes one while there are no photos, ready to encrypt
     * and decrypt them. Throws a `GeneralSecurityException` or an [IOException]
     * if it cannot. It uses the Keystore and the disk, so call it off the main
     * thread.
     */
    fun get(): StreamingAead {
        val keyset = if (hasPhotos()) read() else create()
        return keyset.getPrimitive(CONFIGURATION, StreamingAead::class.java)
    }

    /**
     * A directory that exists but cannot be listed may hold photos, so it
     * counts as having them.
     */
    private fun hasPhotos(): Boolean = photosDirectory.list()?.isNotEmpty() ?: photosDirectory.exists()

    private fun read(): KeysetHandle = TinkProtoKeysetFormat.parseEncryptedKeyset(
        AtomicFile(keysetFile).readFully(),
        AndroidKeystore.getAead(keystoreAlias),
        ASSOCIATED_DATA,
        CONFIGURATION
    )

    private fun create(): KeysetHandle {
        val keyset = KeysetHandle.generateNew(PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB, CONFIGURATION)
        AndroidKeystore.generateNewAes256GcmKey(keystoreAlias)
        val encryptedKeyset = TinkProtoKeysetFormat.serializeEncryptedKeyset(
            keyset,
            AndroidKeystore.getAead(keystoreAlias),
            ASSOCIATED_DATA,
            CONFIGURATION
        )
        // AtomicFile writes the whole file or nothing, and syncs it to disk, so
        // the keyset is saved before any photo needs it.
        AtomicFile(keysetFile).writeBytes(array = encryptedKeyset)
        return keyset
    }

    private companion object {

        /**
         * Tink's streaming AEAD algorithms. They are passed to each call
         * instead of being registered in Tink's global registry.
         */
        val CONFIGURATION: Configuration = StreamingAeadConfig2026.get()

        /**
         * Ties the encrypted keyset to this use, so that it cannot be decrypted
         * as a different secret.
         */
        val ASSOCIATED_DATA = "licence photo keyset".encodeToByteArray()
    }
}
