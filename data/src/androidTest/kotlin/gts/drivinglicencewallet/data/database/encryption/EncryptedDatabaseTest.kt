package gts.drivinglicencewallet.data.database.encryption

import androidx.test.ext.junit.runners.AndroidJUnit4
import gts.drivinglicencewallet.data.database.NATIONAL_ID
import gts.drivinglicencewallet.data.database.RoomApplicationStore
import gts.drivinglicencewallet.data.database.TestDatabaseFiles
import gts.drivinglicencewallet.data.database.containsSequence
import gts.drivinglicencewallet.data.database.submittedApplication
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    private val files = TestDatabaseFiles(name = "encryption-test")

    @BeforeTest
    fun deleteLeftovers() {
        files.delete()
    }

    @AfterTest
    fun deleteFiles() {
        files.delete()
    }

    @Test
    fun givenASavedApplication_whenTheDatabaseFilesAreRead_thenTheyHoldNoSqliteHeaderAndNoNationalId() = runTest {
        val application = submittedApplication(id = "application-1")
        val database = files.open()
        RoomApplicationStore(database = database).add(application = application)

        // Recent writes may still be in the write-ahead log, so it's checked too.
        val savedFiles = listOf(files.databaseFile, File("${files.databaseFile.path}-wal"), File("${files.databaseFile.path}-shm"))
            .filter { file -> file.exists() }
        val databaseStart = files.databaseFile.readBytes().copyOf(newSize = SQLITE_HEADER.size)
        assertFalse(actual = databaseStart.contentEquals(other = SQLITE_HEADER))
        assertTrue(actual = savedFiles.none { file -> file.readBytes().containsSequence(sequence = NATIONAL_ID.encodeToByteArray()) })
        database.close()

        // A new key reader finds the saved key, as after a restart, so the data is really in these files.
        val reopened = files.open()
        assertEquals(expected = application, actual = RoomApplicationStore(database = reopened).find(id = application.id))
        reopened.close()
    }

    @Test
    fun givenASavedApplication_whenTheDatabaseIsOpenedWithAnotherKey_thenReadingFailsAndTheDataIsKept() = runTest {
        val application = submittedApplication(id = "application-1")
        val database = files.open()
        RoomApplicationStore(database = database).add(application = application)
        database.close()

        val withAnotherKey = files.open(key = { ByteArray(size = 32) })
        assertFails { RoomApplicationStore(database = withAnotherKey).find(id = application.id) }
        withAnotherKey.close()

        // A wrong key must never cost the data: SQLCipher refuses to open the file and leaves it as it is.
        val reopened = files.open()
        assertEquals(expected = application, actual = RoomApplicationStore(database = reopened).find(id = application.id))
        reopened.close()
    }

    private companion object {
        /** The first 16 bytes of every unencrypted SQLite database. */
        val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
    }
}
