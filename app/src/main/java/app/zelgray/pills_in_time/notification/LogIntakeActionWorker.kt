package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * "Automatic record creation on notification action" (spec 4.6) — writes the
 * IntakeLog off the main thread and outside the BroadcastReceiver's short
 * execution budget. Reuses IntakeRepository.recordQuickAction, so this is
 * tagged source = REMINDER just like the in-app Home quick actions.
 */
@HiltWorker
class LogIntakeActionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val intakeRepository: IntakeRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val drugId = inputData.getLong(NotificationContracts.EXTRA_DRUG_ID, -1)
        val scheduledIntakeId = inputData.getLong(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
        val intakeTimeId = inputData.getLong(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
        val occurrenceDateEpochDay = inputData.getLong(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
        val doseValue = inputData.getDouble(NotificationContracts.EXTRA_DOSE_VALUE, 1.0)
        val doseMode = runCatching { DoseMode.valueOf(inputData.getString(NotificationContracts.EXTRA_DOSE_MODE) ?: "") }
            .getOrDefault(DoseMode.UNITS)
        val status = runCatching { IntakeStatus.valueOf(inputData.getString(NotificationContracts.EXTRA_STATUS) ?: "") }
            .getOrNull() ?: return Result.failure()

        if (drugId < 0 || scheduledIntakeId < 0 || intakeTimeId < 0 || occurrenceDateEpochDay < 0) return Result.failure()

        // Result intentionally ignored: on InsufficientStock, nothing is written —
        // the occurrence stays unresolved and PostNotificationWorker's existing
        // repeat-every-5-minutes check (no log found yet) keeps re-alerting until
        // the user tops up stock and logs it from the app.
        intakeRepository.recordQuickAction(
            drugId = drugId,
            scheduledIntakeId = scheduledIntakeId,
            intakeTimeId = intakeTimeId,
            occurrenceDate = NotificationContracts.occurrenceDateOf(occurrenceDateEpochDay),
            doseValue = doseValue,
            doseMode = doseMode,
            status = status,
        )
        return Result.success()
    }
}
