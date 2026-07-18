package app.zelgray.pills_in_time.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

/**
 * AlarmManager alarms don't survive a reboot — this re-arms them from the
 * persisted schedule data as soon as the device comes back up.
 */
@AndroidEntryPoint
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DailyRescheduleWorker.enqueueNow(context)
        }
    }
}
