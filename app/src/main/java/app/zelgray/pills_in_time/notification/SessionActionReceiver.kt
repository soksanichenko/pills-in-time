package app.zelgray.pills_in_time.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint

/**
 * Target of the day-start-prompt's "День начат" action and the active-session
 * status notification's "Иду спать" action. Kept minimal like
 * IntakeActionReceiver/NotificationPostReceiver — the actual Room writes and
 * notification rebuilding happen in StartDayWorker/EndDayWorker via
 * SessionActionHandler.
 */
@AndroidEntryPoint
class SessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledIntakeId = intent.getLongExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
        val intakeTimeId = intent.getLongExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
        val occurrenceDateEpochDay = intent.getLongExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
        if (scheduledIntakeId < 0 || intakeTimeId < 0 || occurrenceDateEpochDay < 0) return

        val data = Data.Builder()
            .putLong(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
            .putLong(NotificationContracts.EXTRA_INTAKE_TIME_ID, intakeTimeId)
            .putLong(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, occurrenceDateEpochDay)
            .putLong(NotificationContracts.EXTRA_DRUG_ID, intent.getLongExtra(NotificationContracts.EXTRA_DRUG_ID, -1))
            .build()

        when (intent.action) {
            NotificationContracts.ACTION_START_DAY -> {
                WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<StartDayWorker>().setInputData(data).build())
            }
            NotificationContracts.ACTION_END_DAY -> {
                val notificationId = intent.getIntExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, -1)
                if (notificationId != -1) NotificationManagerCompat.from(context).cancel(notificationId)
                WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<EndDayWorker>().setInputData(data).build())
            }
        }
    }
}
