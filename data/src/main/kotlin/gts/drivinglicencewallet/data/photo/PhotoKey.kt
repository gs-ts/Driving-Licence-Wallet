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
 * The key that encrypts the photos: a Tink keyset, saved in [keysetFile] encrypted by a key in the Android
 * Keystore ([keystoreAlias]). The Keystore key can't be copied off the device, so a copy of the app's files
 * can't decrypt the photos.
 *
 * A new key is made only while [photosDirectory] holds no photos. Once it does, a key that can't be read is
 * an error, and nothing is deleted: a new key would lock the saved photos out for good.
 */
internal class PhotoKey(
    private val keysetFile: File,
    private val photosDirectory: File,
    private val keystoreAlias: String,
) {

    /**
     * Reads the key, or makes one while there are no photos, ready to encrypt and decrypt them.
     * Throws a `GeneralSecurityException` or an [IOException] if it can't. It uses the Keystore and the disk,
     * so call it off the main thread.
     */
    fun get(): StreamingAead {
        val keyset = if (hasPhotos()) read() else create()
        return keyset.getPrimitive(CONFIGURATION, StreamingAead::class.java)
    }

    /** A directory that exists but can't be listed may hold photos, so it counts as having them. */
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
        // AtomicFile writes the whole file or nothing, and syncs it to disk, so it's saved before any photo needs it.
        AtomicFile(keysetFile).writeBytes(array = encryptedKeyset)
        return keyset
    }

    private companion object {

        /** Tink's streaming AEAD algorithms, passed to each call instead of registering them in Tink's global registry. */
        val CONFIGURATION: Configuration = StreamingAeadConfig2026.get()

        /** Ties the encrypted keyset to this use, so it can't pass for another secret. */
        val ASSOCIATED_DATA = "licence photo keyset".encodeToByteArray()
    }
}
