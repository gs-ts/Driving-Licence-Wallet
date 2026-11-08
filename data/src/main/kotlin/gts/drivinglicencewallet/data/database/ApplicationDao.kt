package gts.drivinglicencewallet.data.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import gts.drivinglicencewallet.data.database.entities.ApplicationEntity
import gts.drivinglicencewallet.data.database.entities.ApplicationWithHistory
import gts.drivinglicencewallet.data.database.entities.HistoryEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ApplicationDao {

    @Insert
    suspend fun insertApplication(application: ApplicationEntity)

    @Insert
    suspend fun insertHistory(events: List<HistoryEventEntity>)

    @Transaction
    @Query(value = "SELECT * FROM applications WHERE id = :id")
    suspend fun find(id: String): ApplicationWithHistory?

    @Transaction
    @Query(value = "SELECT * FROM applications")
    suspend fun findAll(): List<ApplicationWithHistory>

    @Transaction
    @Query(value = "SELECT * FROM applications WHERE id = :id")
    fun observe(id: String): Flow<ApplicationWithHistory?>
}
