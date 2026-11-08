package gts.drivinglicencewallet.data.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import gts.drivinglicencewallet.data.database.entities.HistoryEventEntity
import gts.drivinglicencewallet.data.database.entities.LicenceApplicationEntity
import gts.drivinglicencewallet.data.database.entities.LicenceApplicationWithHistory
import kotlinx.coroutines.flow.Flow

@Dao
internal interface LicenceApplicationDao {

    @Insert
    suspend fun insertApplication(application: LicenceApplicationEntity)

    @Insert
    suspend fun insertHistory(events: List<HistoryEventEntity>)

    @Transaction
    @Query(value = "SELECT * FROM applications WHERE id = :id")
    suspend fun find(id: String): LicenceApplicationWithHistory?

    @Transaction
    @Query(value = "SELECT * FROM applications")
    suspend fun findAll(): List<LicenceApplicationWithHistory>

    @Transaction
    @Query(value = "SELECT * FROM applications WHERE id = :id")
    fun observe(id: String): Flow<LicenceApplicationWithHistory?>
}
