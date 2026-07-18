package app.zelgray.pills_in_time.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint

/**
 * The alarm's target. Kept minimal (BroadcastReceiver.onReceive has a short
 * execution budget) — the real work of reading the drug and posting the
 * system notification happens in PostNotificationWorker.
 */
@AndroidEntryPoint
class NotificationPostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = NotificationContracts.dataFromIntent(intent)
        val request = OneTimeWorkRequestBuilder<PostNotificationWorker>().setInputData(data).build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
