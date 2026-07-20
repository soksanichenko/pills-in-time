package app.zelgray.pills_in_time.data.repository

import android.content.Context
import androidx.room.withTransaction
import app.zelgray.pills_in_time.data.local.MedTrackerDatabase
import app.zelgray.pills_in_time.data.local.dao.IntakeLogConsumptionDao
import app.zelgray.pills_in_time.data.local.dao.StockBatchDao
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.IntakeLogConsumption
import app.zelgray.pills_in_time.domain.model.BatchDecrement
import app.zelgray.pills_in_time.domain.model.DoseComboPiece
import app.zelgray.pills_in_time.domain.model.DoseConsumptionResult
import app.zelgray.pills_in_time.domain.usecase.ResolveDoseConsumptionUseCase
import app.zelgray.pills_in_time.notification.LowStockCheckWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Applies/reverses the real per-batch stock consumption tied to a TAKEN
 * IntakeLog. Resolution (which batches, how much) is a pure read-only
 * computation over currently on-hand batches; applying/reversing are the
 * actual writes, recorded per-log in IntakeLogConsumption so they can be
 * undone exactly regardless of how stock has changed since.
 */
class StockConsumptionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MedTrackerDatabase,
    private val stockBatchDao: StockBatchDao,
    private val consumptionDao: IntakeLogConsumptionDao,
    private val resolveDoseConsumption: ResolveDoseConsumptionUseCase,
) {
    suspend fun resolve(
        drugId: Long,
        doseMode: DoseMode,
        doseValue: Double,
        doseAllocation: List<DoseComboPiece>?,
        pinnedBatchId: Long? = null,
    ): DoseConsumptionResult {
        val batches = stockBatchDao.getBatchesForDrug(drugId)
        return resolveDoseConsumption(doseMode, doseValue, doseAllocation, batches, pinnedBatchId)
    }

    suspend fun applyResolvedConsumption(logId: Long, decrements: List<BatchDecrement>) {
        if (decrements.isEmpty()) return
        database.withTransaction {
            decrements.forEach { decrement ->
                val batch = stockBatchDao.getById(decrement.batchId) ?: return@forEach
                stockBatchDao.update(batch.copy(quantity = batch.quantity - decrement.quantity))
            }
            consumptionDao.insertAll(
                decrements.map { IntakeLogConsumption(intakeLogId = logId, batchId = it.batchId, quantity = it.quantity) },
            )
        }
        LowStockCheckWorker.enqueueNow(context)
    }

    suspend fun reverseConsumption(logId: Long) {
        val consumptions = consumptionDao.getForLog(logId)
        if (consumptions.isEmpty()) return
        database.withTransaction {
            consumptions.forEach { consumption ->
                val batch = stockBatchDao.getById(consumption.batchId) ?: return@forEach
                stockBatchDao.update(batch.copy(quantity = batch.quantity + consumption.quantity))
            }
            consumptionDao.deleteForLog(logId)
        }
        LowStockCheckWorker.enqueueNow(context)
    }
}
