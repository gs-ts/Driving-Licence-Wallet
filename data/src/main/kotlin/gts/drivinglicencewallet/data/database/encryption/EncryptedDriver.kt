package gts.drivinglicencewallet.data.database.encryption

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

/**
 * Opens the database with SQLCipher. Room opens it on the first query, which runs in the background,
 * so [key] (a call to the Keystore) never runs on the main thread.
 */
internal class EncryptedDriver(private val key: () -> ByteArray) : SQLiteDriver {

    /** SQLCipher pools its own connections, like Android's SQLite. So Room keeps each transaction on one thread. */
    override val hasConnectionPool: Boolean
        get() = true

    override fun open(fileName: String): SQLiteConnection {
        System.loadLibrary("sqlcipher")
        return SQLCipherDriver(rawKey(key = key()), null, null).open(fileName)
    }

    /** SQLCipher uses a key in this form as it is. A passphrase would add a slow key derivation to every open. */
    private fun rawKey(key: ByteArray): ByteArray = "x'${key.toHexString()}'".encodeToByteArray()
}
