package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.zelgray.pills_in_time.data.local.entity.Drug
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugDao {

    @Query("SELECT * FROM drugs WHERE patientId = :patientId ORDER BY name")
    fun observeAllDrugs(patientId: Long): Flow<List<Drug>>

    @Query("SELECT * FROM drugs")
    suspend fun getAllOnce(): List<Drug>

    @Query("SELECT * FROM drugs WHERE patientId = :patientId")
    suspend fun getAllOnce(patientId: Long): List<Drug>

    @Query("SELECT * FROM drugs WHERE id = :drugId")
    suspend fun getById(drugId: Long): Drug?

    @Query("SELECT * FROM drugs WHERE id = :drugId")
    fun observeById(drugId: Long): Flow<Drug?>

    @Query("SELECT EXISTS(SELECT 1 FROM scheduled_intakes WHERE drugId = :drugId) OR EXISTS(SELECT 1 FROM drug_stock_batches WHERE drugId = :drugId)")
    suspend fun hasSchedulesOrStock(drugId: Long): Boolean

    @Insert
    suspend fun insert(drug: Drug): Long

    @Insert
    suspend fun insertAll(drugs: List<Drug>): List<Long>

    @Update
    suspend fun update(drug: Drug)

    @Delete
    suspend fun delete(drug: Drug)

    @Query("DELETE FROM drugs WHERE id = :drugId")
    suspend fun deleteById(drugId: Long)

    @Query("DELETE FROM drugs")
    suspend fun deleteAllDrugs()
}
