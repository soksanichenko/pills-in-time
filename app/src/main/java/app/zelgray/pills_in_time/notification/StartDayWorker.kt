package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class StartDayWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sessionActionHandler: SessionActionHandler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scheduledIntakeId = inputData.getLong(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
        val intakeTimeId = inputData.getLong(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
        val occurrenceDateEpochDay = inputData.getLong(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
        if (scheduledIntakeId < 0 || intakeTimeId < 0 || occurrenceDateEpochDay < 0) return Result.failure()

        sessionActionHandler.startDay(scheduledIntakeId, intakeTimeId, NotificationContracts.occurrenceDateOf(occurrenceDateEpochDay))
        return Result.success()
    }
}
