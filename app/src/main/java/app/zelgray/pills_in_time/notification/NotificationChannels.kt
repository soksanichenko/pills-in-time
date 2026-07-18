package app.zelgray.pills_in_time.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.zelgray.pills_in_time.R

object NotificationChannels {
    const val MEDICATION_REMINDERS = "medication_reminders"
    const val LOW_STOCK_REMINDERS = "low_stock_reminders"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                MEDICATION_REMINDERS,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                LOW_STOCK_REMINDERS,
                context.getString(R.string.notification_low_stock_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_low_stock_channel_description)
            },
        )
    }
}
