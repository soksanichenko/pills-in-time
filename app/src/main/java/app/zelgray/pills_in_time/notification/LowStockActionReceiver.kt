package app.zelgray.pills_in_time.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

/**
 * Target of the low-stock notification's "Remind tomorrow" action. Kept
 * minimal like IntakeActionReceiver — dismisses the notification
 * synchronously, then dispatches the delayed re-post to a Worker.
 */
@AndroidEntryPoint
class LowStockActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        if (intent.action != NotificationContracts.ACTION_SNOOZE_LOW_STOCK) return

        val data = Data.Builder()
            .putLong(NotificationContracts.EXTRA_DRUG_ID, intent.getLongExtra(NotificationContracts.EXTRA_DRUG_ID, -1))
            .putLong(NotificationContracts.EXTRA_STOCK_ID, intent.getLongExtra(NotificationContracts.EXTRA_STOCK_ID, -1))
            .putString(NotificationContracts.EXTRA_DRUG_NAME, intent.getStringExtra(NotificationContracts.EXTRA_DRUG_NAME))
            .putLong(
                NotificationContracts.EXTRA_RUN_OUT_DATE_EPOCH_DAY,
                intent.getLongExtra(NotificationContracts.EXTRA_RUN_OUT_DATE_EPOCH_DAY, -1),
            )
            .build()
        val request = OneTimeWorkRequestBuilder<SnoozeLowStockReminderWorker>()
            .setInputData(data)
            .setInitialDelay(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
