package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/** Re-posts a low-stock reminder a fixed 24 hours after "Remind tomorrow" is tapped. */
@HiltWorker
class SnoozeLowStockReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val drugId = inputData.getLong(NotificationContracts.EXTRA_DRUG_ID, -1)
        val batchId = inputData.getLong(NotificationContracts.EXTRA_STOCK_ID, -1)
        val drugName = inputData.getString(NotificationContracts.EXTRA_DRUG_NAME)
        val runOutDateEpochDay = inputData.getLong(NotificationContracts.EXTRA_RUN_OUT_DATE_EPOCH_DAY, -1)

        if (drugId < 0 || batchId < 0 || drugName == null || runOutDateEpochDay < 0) return Result.failure()

        LowStockNotifications.post(
            applicationContext,
            drugId,
            batchId,
            drugName,
            LocalDate.ofEpochDay(runOutDateEpochDay),
        )
        return Result.success()
    }
}
