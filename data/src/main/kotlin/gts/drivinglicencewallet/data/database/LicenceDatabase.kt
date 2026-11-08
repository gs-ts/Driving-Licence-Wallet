package gts.drivinglicencewallet.data.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import gts.drivinglicencewallet.data.database.encryption.DatabaseKey
import gts.drivinglicencewallet.data.database.encryption.EncryptedDriver
import gts.drivinglicencewallet.data.database.entities.ApplicationEntity
import gts.drivinglicencewallet.data.database.entities.HistoryEventEntity
import java.io.File

/** The applications and their histories, encrypted with SQLCipher. */
@Database(entities = [ApplicationEntity::class, HistoryEventEntity::class], version = 1)
internal abstract class LicenceDatabase : RoomDatabase() {

    abstract fun applicationDao(): ApplicationDao
}

/** Opens the database in [databaseFile], encrypted with [key], such as [DatabaseKey.get]. */
internal fun openLicenceDatabase(
    context: Context,
    databaseFile: File,
    key: () -> ByteArray,
): LicenceDatabase = Room.databaseBuilder<LicenceDatabase>(context = context, name = databaseFile.absolutePath)
    .setDriver(driver = EncryptedDriver(key = key))
    .build()
