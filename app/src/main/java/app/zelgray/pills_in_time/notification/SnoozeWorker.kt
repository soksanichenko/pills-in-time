package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Snooze re-posts the same notification after a delay rather than registering
 * a fresh exact AlarmManager alarm — a "remind me in N minutes" doesn't need
 * to-the-second precision, and WorkManager's delayed execution already
 * survives reboot/process death without the exact-alarm permission dance.
 */
@HiltWorker
class SnoozeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val intakeRepository: IntakeRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val snoozeMinutes = settingsRepository.getSnoozeMinutesOnce()
        val notificationId = inputData.getInt(NotificationContracts.EXTRA_NOTIFICATION_ID, -1)
        val request = OneTimeWorkRequestBuilder<PostNotificationWorker>()
            .setInputData(inputData)
            .setInitialDelay(snoozeMinutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            NotificationContracts.repeatWorkName(notificationId),
            ExistingWorkPolicy.REPLACE,
            request,
        )

        val occurrenceDateEpochDay = inputData.getLong(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
        val snoozedUntil = Instant.now().plusSeconds(snoozeMinutes * 60L)
        val groupMembers = NotificationContracts.decodeGroupMembers(inputData.getString(NotificationContracts.EXTRA_GROUP_MEMBERS))
        if (occurrenceDateEpochDay >= 0) {
            val occurrenceDate = NotificationContracts.occurrenceDateOf(occurrenceDateEpochDay)
            if (groupMembers.isNotEmpty()) {
                groupMembers.forEach { member ->
                    intakeRepository.recordSnooze(member.scheduledIntakeId, member.intakeTimeId, occurrenceDate, snoozedUntil)
                }
            } else {
                val scheduledIntakeId = inputData.getLong(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
                val intakeTimeId = inputData.getLong(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
                if (scheduledIntakeId >= 0 && intakeTimeId >= 0) {
                    intakeRepository.recordSnooze(scheduledIntakeId, intakeTimeId, occurrenceDate, snoozedUntil)
                }
            }
        }
        return Result.success()
    }
}
