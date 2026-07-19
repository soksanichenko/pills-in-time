package app.zelgray.pills_in_time.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zelgray.pills_in_time.MainActivity
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.domain.usecase.ResolveEffectiveStrengthUseCase
import app.zelgray.pills_in_time.domain.usecase.ScheduleAlarmsForWindowUseCase
import app.zelgray.pills_in_time.ui.drugs.doseTextPlain
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@HiltWorker
class PostNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val drugRepository: DrugRepository,
    private val stockRepository: StockRepository,
    private val intakeRepository: IntakeRepository,
    private val scheduleRepository: ScheduleRepository,
    private val resolveEffectiveStrength: ResolveEffectiveStrengthUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val drugId = inputData.getLong(NotificationContracts.EXTRA_DRUG_ID, -1)
        val scheduledIntakeId = inputData.getLong(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
        val intakeTimeId = inputData.getLong(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
        val occurrenceDateEpochDay = inputData.getLong(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
        val timeOfDaySecond = inputData.getInt(NotificationContracts.EXTRA_TIME_OF_DAY_SECOND, 0)
        val doseValue = inputData.getDouble(NotificationContracts.EXTRA_DOSE_VALUE, 1.0)
        val doseMode = runCatching { DoseMode.valueOf(inputData.getString(NotificationContracts.EXTRA_DOSE_MODE) ?: "") }
            .getOrDefault(DoseMode.UNITS)

        if (drugId < 0 || scheduledIntakeId < 0 || intakeTimeId < 0 || occurrenceDateEpochDay < 0) return Result.failure()

        val occurrenceDate = NotificationContracts.occurrenceDateOf(occurrenceDateEpochDay)

        // Occurrence may already be resolved (e.g. logged from the Home screen directly)
        // since the last repeat was scheduled — stop the chain here instead of nagging further.
        if (intakeRepository.getLogForOccurrenceOnce(scheduledIntakeId, intakeTimeId, occurrenceDate) != null) {
            return Result.success()
        }

        val drug = drugRepository.getById(drugId) ?: return Result.failure()
        val timeOfDay = LocalTime.ofSecondOfDay(timeOfDaySecond.toLong())
        val notificationId = ScheduleAlarmsForWindowUseCase.computeRequestCode(scheduledIntakeId, intakeTimeId, occurrenceDate)

        val batches = stockRepository.getBatchesForDrugOnce(drugId)
        val strength = resolveEffectiveStrength(batches)
        val doseAllocation = scheduleRepository.getTimeById(intakeTimeId)?.doseAllocation
        val doseText = doseTextPlain(applicationContext, doseValue, doseMode, drug, batches, strength, doseAllocation)
        val timeText = "%02d:%02d".format(timeOfDay.hour, timeOfDay.minute)

        val takeIntent = actionIntent(NotificationContracts.ACTION_TAKE, notificationId, drugId, scheduledIntakeId, intakeTimeId, occurrenceDateEpochDay, timeOfDaySecond, doseValue, doseMode)
        val skipIntent = actionIntent(NotificationContracts.ACTION_SKIP, notificationId, drugId, scheduledIntakeId, intakeTimeId, occurrenceDateEpochDay, timeOfDaySecond, doseValue, doseMode)
        val snoozeIntent = actionIntent(NotificationContracts.ACTION_SNOOZE, notificationId, drugId, scheduledIntakeId, intakeTimeId, occurrenceDateEpochDay, timeOfDaySecond, doseValue, doseMode)

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.MEDICATION_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(drug.name)
            .setContentText("$timeText · $doseText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(notificationId, drugId, scheduledIntakeId, intakeTimeId, occurrenceDateEpochDay))
            .addAction(0, applicationContext.getString(R.string.action_took_it), pendingIntentFor(notificationId * 10 + 1, takeIntent))
            .addAction(0, applicationContext.getString(R.string.action_skipped), pendingIntentFor(notificationId * 10 + 2, skipIntent))
            .addAction(0, applicationContext.getString(R.string.action_snooze), pendingIntentFor(notificationId * 10 + 3, snoozeIntent))
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        scheduleRepeat(notificationId)
        return Result.success()
    }

    /** Re-posts this same reminder every REPEAT_INTERVAL_MINUTES until Take/Skip/Snooze cancels or replaces this chain. */
    private fun scheduleRepeat(notificationId: Int) {
        val request = OneTimeWorkRequestBuilder<PostNotificationWorker>()
            .setInputData(inputData)
            .setInitialDelay(REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            NotificationContracts.repeatWorkName(notificationId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun contentIntent(
        notificationId: Int,
        drugId: Long,
        scheduledIntakeId: Long,
        intakeTimeId: Long,
        occurrenceDateEpochDay: Long,
    ): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = NotificationContracts.ACTION_VIEW_OCCURRENCE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(NotificationContracts.EXTRA_DRUG_ID, drugId)
            putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
            putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, intakeTimeId)
            putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, occurrenceDateEpochDay)
        }
        return PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(
        action: String,
        notificationId: Int,
        drugId: Long,
        scheduledIntakeId: Long,
        intakeTimeId: Long,
        occurrenceDateEpochDay: Long,
        timeOfDaySecond: Int,
        doseValue: Double,
        doseMode: DoseMode,
    ): Intent = Intent(applicationContext, IntakeActionReceiver::class.java).apply {
        this.action = action
        putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, notificationId)
        putExtra(NotificationContracts.EXTRA_DRUG_ID, drugId)
        putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
        putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, intakeTimeId)
        putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, occurrenceDateEpochDay)
        putExtra(NotificationContracts.EXTRA_TIME_OF_DAY_SECOND, timeOfDaySecond)
        putExtra(NotificationContracts.EXTRA_DOSE_VALUE, doseValue)
        putExtra(NotificationContracts.EXTRA_DOSE_MODE, doseMode.name)
    }

    private fun pendingIntentFor(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val REPEAT_INTERVAL_MINUTES = 5L
    }
}
