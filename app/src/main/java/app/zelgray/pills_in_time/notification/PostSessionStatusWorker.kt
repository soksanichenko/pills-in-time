package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Handles both an HOURLY session's tick alarm firing (see
 * SessionActionHandler.handleTick) and that same reminder's own 5-minute
 * repost re-invocation (see SessionActionHandler.handleIntervalRepost) —
 * distinguished by whether EXTRA_SESSION_SEQ is present, exactly like
 * PostNotificationWorker re-invokes itself for its own repeat chain.
 */
@HiltWorker
class PostSessionStatusWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sessionActionHandler: SessionActionHandler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val drugId = inputData.getLong(NotificationContracts.EXTRA_DRUG_ID, -1)
        val scheduledIntakeId = inputData.getLong(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
        val intakeTimeId = inputData.getLong(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
        val occurrenceDateEpochDay = inputData.getLong(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
        if (drugId < 0 || scheduledIntakeId < 0 || intakeTimeId < 0 || occurrenceDateEpochDay < 0) return Result.failure()
        val date = NotificationContracts.occurrenceDateOf(occurrenceDateEpochDay)

        val targetSeq = inputData.getInt(NotificationContracts.EXTRA_SESSION_SEQ, -1)
        if (targetSeq >= 0) {
            sessionActionHandler.handleIntervalRepost(scheduledIntakeId, intakeTimeId, drugId, date, targetSeq)
        } else {
            sessionActionHandler.handleTick(scheduledIntakeId, intakeTimeId, drugId, date)
        }
        return Result.success()
    }
}
