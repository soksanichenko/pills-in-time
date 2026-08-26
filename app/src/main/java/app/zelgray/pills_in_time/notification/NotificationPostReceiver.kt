package app.zelgray.pills_in_time.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.zelgray.pills_in_time.data.local.entity.AlarmKind
import dagger.hilt.android.AndroidEntryPoint

/**
 * The alarm's target. Kept minimal (BroadcastReceiver.onReceive has a short
 * execution budget) — the real work of reading the drug and posting the
 * system notification happens in a Worker, chosen by what kind of alarm this
 * is (a normal dose reminder, a session's day-start prompt, or an hourly
 * session tick).
 */
@AndroidEntryPoint
class NotificationPostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = NotificationContracts.dataFromIntent(intent)
        val request = when (NotificationContracts.kindOf(data)) {
            AlarmKind.DOSE_REMINDER -> OneTimeWorkRequestBuilder<PostNotificationWorker>().setInputData(data).build()
            AlarmKind.SESSION_START_PROMPT -> OneTimeWorkRequestBuilder<PostSessionPromptWorker>().setInputData(data).build()
            AlarmKind.SESSION_TICK -> OneTimeWorkRequestBuilder<PostSessionStatusWorker>().setInputData(data).build()
        }
        WorkManager.getInstance(context).enqueue(request)
    }
}
