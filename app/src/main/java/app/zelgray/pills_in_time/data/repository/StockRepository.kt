package app.zelgray.pills_in_time.data.repository

import android.content.Context
import app.zelgray.pills_in_time.data.local.dao.StockBatchDao
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.notification.LowStockCheckWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class StockRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stockBatchDao: StockBatchDao,
) {
    fun observeBatchesForDrug(drugId: Long): Flow<List<DrugStockBatch>> =
        stockBatchDao.observeBatchesForDrug(drugId)

    fun observeAllBatches(): Flow<List<DrugStockBatch>> = stockBatchDao.observeAll()

    suspend fun getMostRecentBatch(drugId: Long): DrugStockBatch? =
        stockBatchDao.getMostRecentBatch(drugId)

    suspend fun getBatchesForDrugOnce(drugId: Long): List<DrugStockBatch> =
        stockBatchDao.getBatchesForDrug(drugId)

    suspend fun getAllBatchesOnce(): List<DrugStockBatch> = stockBatchDao.getAllOnce()

    suspend fun getById(batchId: Long): DrugStockBatch? = stockBatchDao.getById(batchId)

    suspend fun createBatch(
        drugId: Long,
        quantity: Double,
        strengthValue: Double?,
        strengthUnit: StrengthUnit?,
        lowStockReminderDaysBefore: Int? = null,
    ): Long {
        val id = stockBatchDao.insert(
            DrugStockBatch(
                drugId = drugId,
                quantity = quantity,
                strengthValue = strengthValue,
                strengthUnit = strengthUnit,
                addedAt = Instant.now(),
                lowStockReminderDaysBefore = lowStockReminderDaysBefore,
            ),
        )
        LowStockCheckWorker.enqueueNow(context)
        return id
    }

    suspend fun updateBatch(batch: DrugStockBatch) {
        stockBatchDao.update(batch)
        LowStockCheckWorker.enqueueNow(context)
    }

    suspend fun deleteBatch(batch: DrugStockBatch) = stockBatchDao.delete(batch)
}
