package gts.drivinglicencewallet.data.database.encryption

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.crypto.tink.integration.android.AndroidKeystore
import gts.drivinglicencewallet.data.database.TestDatabaseFiles
import gts.drivinglicencewallet.data.database.containsSequence
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DatabaseKeyTest {

    private val files = TestDatabaseFiles(name = "key-test")

    @BeforeTest
    fun deleteLeftovers() {
        files.delete()
    }

    @AfterTest
    fun deleteFiles() {
        files.delete()
    }

    @Test
    fun givenNoDatabase_whenTheKeyIsMadeAndReadBack_thenItIsTheSame32BytesAndTheKeyFileDoesNotHoldThem() {
        val made = files.databaseKey().get()
        assertTrue(actual = files.databaseFile.createNewFile())

        val read = files.databaseKey().get()

        assertEquals(expected = 32, actual = made.size)
        assertContentEquals(expected = made, actual = read)
        assertFalse(actual = files.keyFile.readBytes().containsSequence(sequence = made))
    }

    @Test
    fun givenADatabaseAndAKeyThatCantBeDecrypted_whenTheKeyIsRead_thenItFailsAndNothingIsDeleted() {
        files.databaseKey().get()
        assertTrue(actual = files.databaseFile.createNewFile())
        // The Keystore key is gone, as when the device's secure storage is reset.
        AndroidKeystore.deleteKey(files.keystoreAlias)

        assertFailsWith<IllegalStateException> { files.databaseKey().get() }

        assertTrue(actual = files.databaseFile.exists())
        assertTrue(actual = files.keyFile.exists())
    }

    @Test
    fun givenNoDatabaseAndAKeyThatCantBeDecrypted_whenTheKeyIsAskedFor_thenANewOneIsMade() {
        val lost = files.databaseKey().get()
        AndroidKeystore.deleteKey(files.keystoreAlias)

        val made = files.databaseKey().get()
        assertTrue(actual = files.databaseFile.createNewFile())

        assertFalse(actual = made.contentEquals(other = lost))
        assertContentEquals(expected = made, actual = files.databaseKey().get())
    }
}
