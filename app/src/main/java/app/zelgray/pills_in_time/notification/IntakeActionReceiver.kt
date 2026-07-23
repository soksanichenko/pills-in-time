package app.zelgray.pills_in_time.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import dagger.hilt.android.AndroidEntryPoint

/**
 * Target of the 3 notification action buttons. Kept minimal like
 * NotificationPostReceiver — dismisses the notification synchronously, then
 * dispatches the actual DB write (or snooze reschedule) to a Worker.
 */
@AndroidEntryPoint
class IntakeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
            WorkManager.getInstance(context).cancelUniqueWork(NotificationContracts.repeatWorkName(notificationId))
        }

        val encodedMembers = intent.getStringExtra(NotificationContracts.EXTRA_GROUP_MEMBERS)

        when (intent.action) {
            NotificationContracts.ACTION_TAKE -> handleLogAction(context, intent, encodedMembers, IntakeStatus.TAKEN)
            NotificationContracts.ACTION_SKIP -> handleLogAction(context, intent, encodedMembers, IntakeStatus.SKIPPED)
            NotificationContracts.ACTION_SNOOZE -> enqueueSnooze(context, NotificationContracts.dataFromIntent(intent))
        }
    }

    /** For a merged notification (EXTRA_GROUP_MEMBERS present), applies the status to every member; otherwise just the one occurrence. */
    private fun handleLogAction(context: Context, intent: Intent, encodedMembers: String?, status: IntakeStatus) {
        val members = NotificationContracts.decodeGroupMembers(encodedMembers)
        if (members.isEmpty()) {
            enqueueLogAction(context, NotificationContracts.dataFromIntent(intent), status)
            return
        }
        val occurrenceDateEpochDay = intent.getLongExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
        members.forEach { member ->
            val data = Data.Builder()
                .putLong(NotificationContracts.EXTRA_DRUG_ID, member.drugId)
                .putLong(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, member.scheduledIntakeId)
                .putLong(NotificationContracts.EXTRA_INTAKE_TIME_ID, member.intakeTimeId)
                .putLong(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, occurrenceDateEpochDay)
                .putDouble(NotificationContracts.EXTRA_DOSE_VALUE, member.doseValue)
                .putString(NotificationContracts.EXTRA_DOSE_MODE, member.doseMode.name)
                .putString(NotificationContracts.EXTRA_STATUS, status.name)
                .build()
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<LogIntakeActionWorker>().setInputData(data).build())
        }
    }

    private fun enqueueLogAction(context: Context, baseData: Data, status: IntakeStatus) {
        val data = Data.Builder()
            .putAll(baseData)
            .putString(NotificationContracts.EXTRA_STATUS, status.name)
            .build()
        val request = OneTimeWorkRequestBuilder<LogIntakeActionWorker>().setInputData(data).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun enqueueSnooze(context: Context, baseData: Data) {
        val request = OneTimeWorkRequestBuilder<SnoozeWorker>().setInputData(baseData).build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
