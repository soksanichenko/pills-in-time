package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.domain.model.LowStockAlert
import app.zelgray.pills_in_time.domain.usecase.CheckLowStockRemindersUseCase
import app.zelgray.pills_in_time.util.NowProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Daily check for per-supply low-stock reminders: projects each batch that
 * has a reminder configured (see CheckLowStockRemindersUseCase) and posts a
 * notification for any batch whose forecast run-out date newly falls within
 * its notice window.
 */
@HiltWorker
class LowStockCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val stockRepository: StockRepository,
    private val scheduleRepository: ScheduleRepository,
    private val drugRepository: DrugRepository,
    private val checkLowStockReminders: CheckLowStockRemindersUseCase,
    private val nowProvider: NowProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val today = nowProvider.currentLocalDate()
        val batches = stockRepository.getAllBatchesOnce().filter { it.lowStockReminderDaysBefore != null }
        if (batches.isEmpty()) return Result.success()

        val periodsByDrugId = batches.map { it.drugId }.distinct()
            .associateWith { scheduleRepository.getPeriodsForDrugOnce(it) }

        val alerts = checkLowStockReminders(batches, periodsByDrugId, today)

        alerts.forEach { alert ->
            val drug = drugRepository.getById(alert.drugId) ?: return@forEach
            LowStockNotifications.post(applicationContext, alert.drugId, alert.batchId, drug.name, alert.runOutDate)

            val batch = stockRepository.getById(alert.batchId) ?: return@forEach
            stockRepository.updateBatch(batch.copy(lowStockReminderFiredForRunOutDate = alert.runOutDate))
        }

        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "low-stock-check"
        private const val ONE_TIME_WORK_NAME = "low-stock-check-now"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<LowStockCheckWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<LowStockCheckWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
