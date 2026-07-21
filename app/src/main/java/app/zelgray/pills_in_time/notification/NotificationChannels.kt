package app.zelgray.pills_in_time.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import app.zelgray.pills_in_time.R

object NotificationChannels {
    const val MEDICATION_REMINDERS = "medication_reminders"
    const val MEDICATION_REMINDERS_ALARM = "medication_reminders_alarm"
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
                MEDICATION_REMINDERS_ALARM,
                context.getString(R.string.notification_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_alarm_description)
                // Alarm-stream audio attributes so this notification's own sound
                // (a fallback if the full-screen alarm screen never gets shown)
                // rings on the alarm volume, same as the ring played by
                // AlarmRingActivity itself while it's on screen.
                val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(
                    alarmUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
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
