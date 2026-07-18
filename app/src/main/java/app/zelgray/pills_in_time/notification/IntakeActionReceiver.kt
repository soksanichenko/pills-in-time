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
        }

        val baseData = NotificationContracts.dataFromIntent(intent)

        when (intent.action) {
            NotificationContracts.ACTION_TAKE -> enqueueLogAction(context, baseData, IntakeStatus.TAKEN)
            NotificationContracts.ACTION_SKIP -> enqueueLogAction(context, baseData, IntakeStatus.SKIPPED)
            NotificationContracts.ACTION_SNOOZE -> enqueueSnooze(context, baseData)
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
