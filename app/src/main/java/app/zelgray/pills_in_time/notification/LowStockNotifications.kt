package app.zelgray.pills_in_time.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.zelgray.pills_in_time.MainActivity
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.ui.common.localizedDatePlain
import java.time.LocalDate

/**
 * Shared by LowStockCheckWorker (first post) and SnoozeLowStockReminderWorker
 * (postponed re-post), so both hops build the exact same notification.
 */
object LowStockNotifications {

    fun post(context: Context, drugId: Long, batchId: Long, drugName: String, runOutDate: LocalDate) {
        val notificationId = notificationIdFor(batchId)

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                action = NotificationContracts.ACTION_VIEW_STOCK
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(NotificationContracts.EXTRA_DRUG_ID, drugId)
                putExtra(NotificationContracts.EXTRA_STOCK_ID, batchId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(context, LowStockActionReceiver::class.java).apply {
            action = NotificationContracts.ACTION_SNOOZE_LOW_STOCK
            putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationContracts.EXTRA_DRUG_ID, drugId)
            putExtra(NotificationContracts.EXTRA_STOCK_ID, batchId)
            putExtra(NotificationContracts.EXTRA_DRUG_NAME, drugName)
            putExtra(NotificationContracts.EXTRA_RUN_OUT_DATE_EPOCH_DAY, runOutDate.toEpochDay())
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.LOW_STOCK_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.low_stock_notification_title, drugName))
            .setContentText(context.getString(R.string.low_stock_notification_text, localizedDatePlain(runOutDate)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.low_stock_snooze_action), snoozePendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    // Distinct, negative range so these can never collide with the hash-derived
    // dose-reminder notification ids (ScheduleAlarmsForWindowUseCase.computeRequestCode).
    fun notificationIdFor(batchId: Long): Int = (-1_000_000 - batchId).toInt()
}
