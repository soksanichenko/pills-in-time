package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.PatientRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/** Re-posts a low-stock reminder a fixed 24 hours after "Remind tomorrow" is tapped. */
@HiltWorker
class SnoozeLowStockReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val drugRepository: DrugRepository,
    private val stockRepository: StockRepository,
    private val patientRepository: PatientRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val drugId = inputData.getLong(NotificationContracts.EXTRA_DRUG_ID, -1)
        val batchId = inputData.getLong(NotificationContracts.EXTRA_STOCK_ID, -1)
        val runOutDateEpochDay = inputData.getLong(NotificationContracts.EXTRA_RUN_OUT_DATE_EPOCH_DAY, -1)

        if (drugId < 0 || batchId < 0) return Result.failure()

        val drug = drugRepository.getById(drugId) ?: return Result.failure()
        val batch = stockRepository.getById(batchId) ?: return Result.failure()
        val runOutDate = runOutDateEpochDay.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay)
        val patients = patientRepository.getAllOnce()
        val patient = patients.find { it.id == drug.patientId }

        LowStockNotifications.post(applicationContext, drug, batch, runOutDate, patient, patients.size > 1)
        return Result.success()
    }
}
