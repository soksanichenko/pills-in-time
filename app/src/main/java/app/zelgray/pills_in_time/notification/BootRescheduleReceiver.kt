package app.zelgray.pills_in_time.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint

/**
 * AlarmManager alarms don't survive a reboot — this re-arms them from the
 * persisted schedule data as soon as the device comes back up, and re-posts/
 * re-arms any session (see IntakeTime.isSession) that was still active.
 */
@AndroidEntryPoint
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DailyRescheduleWorker.enqueueNow(context)
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ResumeSessionsWorker>().build())
        }
    }
}
