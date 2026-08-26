package app.zelgray.pills_in_time.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.zelgray.pills_in_time.data.local.entity.AlarmKind
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.domain.model.AlarmSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject

class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(spec: AlarmSpec) {
        val pendingIntent = pendingIntentFor(spec.requestCode) { intent ->
            intent.action = NotificationContracts.ACTION_POST_NOTIFICATION
            intent.putExtra(NotificationContracts.EXTRA_DRUG_ID, spec.drugId)
            intent.putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, spec.scheduledIntakeId)
            intent.putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, spec.intakeTimeId)
            intent.putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, spec.occurrenceDate.toEpochDay())
            intent.putExtra(NotificationContracts.EXTRA_TIME_OF_DAY_SECOND, spec.timeOfDay.toSecondOfDay())
            intent.putExtra(NotificationContracts.EXTRA_DOSE_VALUE, spec.doseValue)
            intent.putExtra(NotificationContracts.EXTRA_DOSE_MODE, spec.doseMode.name)
            intent.putExtra(NotificationContracts.EXTRA_KIND, spec.kind.name)
        }

        if (AlarmPermissions.canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, spec.triggerAtEpochMilli, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, spec.triggerAtEpochMilli, pendingIntent)
        }
    }

    /**
     * A session's next hourly tick — unlike [schedule], not driven by an
     * AlarmSpec/the rolling window: the request code and trigger time are
     * computed fresh each time by the caller (see
     * NotificationContracts.computeSessionTickRequestCode and
     * StartDayWorker/PostSessionStatusWorker), since a session's future tick
     * times aren't known until the day is actually started.
     */
    fun scheduleSessionTick(
        requestCode: Int,
        triggerAtEpochMilli: Long,
        drugId: Long,
        scheduledIntakeId: Long,
        intakeTimeId: Long,
        occurrenceDate: LocalDate,
        doseValue: Double,
        doseMode: DoseMode,
    ) {
        val pendingIntent = pendingIntentFor(requestCode) { intent ->
            intent.action = NotificationContracts.ACTION_POST_NOTIFICATION
            intent.putExtra(NotificationContracts.EXTRA_DRUG_ID, drugId)
            intent.putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
            intent.putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, intakeTimeId)
            intent.putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, occurrenceDate.toEpochDay())
            intent.putExtra(NotificationContracts.EXTRA_DOSE_VALUE, doseValue)
            intent.putExtra(NotificationContracts.EXTRA_DOSE_MODE, doseMode.name)
            intent.putExtra(NotificationContracts.EXTRA_KIND, AlarmKind.SESSION_TICK.name)
        }
        if (AlarmPermissions.canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMilli, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtEpochMilli, pendingIntent)
        }
    }

    fun cancel(requestCode: Int) {
        val pendingIntent = pendingIntentFor(requestCode) { it.action = NotificationContracts.ACTION_POST_NOTIFICATION }
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntentFor(requestCode: Int, configure: (Intent) -> Unit): PendingIntent {
        val intent = Intent(context, NotificationPostReceiver::class.java).also(configure)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
