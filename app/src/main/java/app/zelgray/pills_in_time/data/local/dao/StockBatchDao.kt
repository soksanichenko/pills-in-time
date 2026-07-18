package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import kotlinx.coroutines.flow.Flow

@Dao
interface StockBatchDao {

    @Query("SELECT * FROM drug_stock_batches WHERE drugId = :drugId ORDER BY addedAt DESC")
    fun observeBatchesForDrug(drugId: Long): Flow<List<DrugStockBatch>>

    @Query("SELECT * FROM drug_stock_batches WHERE drugId = :drugId ORDER BY addedAt DESC")
    suspend fun getBatchesForDrug(drugId: Long): List<DrugStockBatch>

    @Query("SELECT * FROM drug_stock_batches WHERE drugId = :drugId ORDER BY addedAt DESC LIMIT 1")
    suspend fun getMostRecentBatch(drugId: Long): DrugStockBatch?

    @Query("SELECT * FROM drug_stock_batches WHERE id = :batchId")
    suspend fun getById(batchId: Long): DrugStockBatch?

    @Query("SELECT * FROM drug_stock_batches")
    suspend fun getAllOnce(): List<DrugStockBatch>

    @Query("SELECT * FROM drug_stock_batches")
    fun observeAll(): Flow<List<DrugStockBatch>>

    @Insert
    suspend fun insert(batch: DrugStockBatch): Long

    @Insert
    suspend fun insertAll(batches: List<DrugStockBatch>): List<Long>

    @Update
    suspend fun update(batch: DrugStockBatch)

    @Delete
    suspend fun delete(batch: DrugStockBatch)

    @Query("DELETE FROM drug_stock_batches")
    suspend fun deleteAllBatches()
}
