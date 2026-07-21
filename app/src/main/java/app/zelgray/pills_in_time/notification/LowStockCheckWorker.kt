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
import app.zelgray.pills_in_time.data.repository.PatientRepository
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
    private val patientRepository: PatientRepository,
    private val checkLowStockReminders: CheckLowStockRemindersUseCase,
    private val nowProvider: NowProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val today = nowProvider.currentLocalDate()
        val batches = stockRepository.getAllBatchesOnce()
            .filter { it.lowStockReminderDaysBefore != null || it.lowStockReminderUnitsBefore != null }
        if (batches.isEmpty()) return Result.success()

        val periodsByDrugId = batches.map { it.drugId }.distinct()
            .associateWith { scheduleRepository.getPeriodsForDrugOnce(it) }

        val alerts = checkLowStockReminders(batches, periodsByDrugId, today)
        val patients = patientRepository.getAllOnce()

        alerts.forEach { alert ->
            val drug = drugRepository.getById(alert.drugId) ?: return@forEach
            val batch = stockRepository.getById(alert.batchId) ?: return@forEach
            val patient = patients.find { it.id == drug.patientId }
            LowStockNotifications.post(applicationContext, drug, batch, alert.runOutDate, patient, patients.size > 1)
            val updated = if (batch.lowStockReminderDaysBefore != null) {
                batch.copy(lowStockReminderFiredForRunOutDate = alert.runOutDate)
            } else {
                batch.copy(lowStockReminderUnitsAlreadyFired = true)
            }
            stockRepository.updateBatch(updated)
        }

        // A units-before reminder that already fired resets once quantity
        // recovers above its threshold (e.g. a restock), so it can fire again
        // next time it dips low — days-before reminders need no such reset
        // since their dedup key (the forecast date) already changes on its own.
        batches.filter { batch ->
            batch.lowStockReminderUnitsBefore != null &&
                batch.lowStockReminderUnitsAlreadyFired &&
                batch.quantity > batch.lowStockReminderUnitsBefore
        }.forEach { stockRepository.updateBatch(it.copy(lowStockReminderUnitsAlreadyFired = false)) }

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
