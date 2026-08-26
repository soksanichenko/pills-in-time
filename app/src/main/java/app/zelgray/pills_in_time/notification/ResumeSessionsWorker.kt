package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import app.zelgray.pills_in_time.util.NowProvider

/** Re-posts/re-arms any still-active session after a reboot (see BootRescheduleReceiver — AlarmManager alarms and notifications don't survive one). */
@HiltWorker
class ResumeSessionsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sessionActionHandler: SessionActionHandler,
    private val nowProvider: NowProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        sessionActionHandler.resumeActiveSessionsAfterBoot(nowProvider.currentLocalDate())
        return Result.success()
    }
}
