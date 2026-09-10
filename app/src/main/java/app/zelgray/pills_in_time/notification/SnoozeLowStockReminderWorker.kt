package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.PatientRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.domain.usecase.CheckLowStockRemindersUseCase
import app.zelgray.pills_in_time.domain.usecase.ProjectDrugStockUseCase
import app.zelgray.pills_in_time.util.NowProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Re-posts a low-stock reminder a fixed 24 hours after "Remind tomorrow" is tapped — unless the supply was restocked above its threshold in the meantime. */
@HiltWorker
class SnoozeLowStockReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val drugRepository: DrugRepository,
    private val stockRepository: StockRepository,
    private val scheduleRepository: ScheduleRepository,
    private val patientRepository: PatientRepository,
    private val projectDrugStock: ProjectDrugStockUseCase,
    private val checkLowStockReminders: CheckLowStockRemindersUseCase,
    private val nowProvider: NowProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val drugId = inputData.getLong(NotificationContracts.EXTRA_DRUG_ID, -1)
        val batchId = inputData.getLong(NotificationContracts.EXTRA_STOCK_ID, -1)

        if (drugId < 0 || batchId < 0) return Result.failure()

        val drug = drugRepository.getById(drugId) ?: return Result.failure()
        val batch = stockRepository.getById(batchId) ?: return Result.failure()

        val today = nowProvider.currentLocalDate()
        val periods = scheduleRepository.getPeriodsForDrugOnce(drugId)
        val projection = projectDrugStock(periods, stockRepository.getBatchesForDrugOnce(drugId), today)
        val runOutDate = projection.batchExhaustionDates[batch.id]
        // A restock between the snooze and this re-post already resolved the
        // shortage, so the postponed reminder no longer applies.
        if (!checkLowStockReminders.isLow(batch, runOutDate, today)) return Result.success()

        val patients = patientRepository.getAllOnce()
        val patient = patients.find { it.id == drug.patientId }

        LowStockNotifications.post(applicationContext, drug, batch, runOutDate, patient, patients.size > 1)
        return Result.success()
    }
}
