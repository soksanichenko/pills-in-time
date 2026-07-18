package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.ScheduledAlarmRepository
import app.zelgray.pills_in_time.domain.usecase.ScheduleAlarmsForWindowUseCase
import app.zelgray.pills_in_time.util.NowProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Reconciles the scheduled_alarms registry against a fresh 3-day occurrence
 * window (spec 4.5: notifications recalculate on schedule changes). Runs
 * periodically, and is also enqueued as a one-off after any period/time CRUD
 * or a device reboot (alarms don't survive reboot, see BootRescheduleReceiver).
 */
@HiltWorker
class DailyRescheduleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
    private val intakeRepository: IntakeRepository,
    private val scheduledAlarmRepository: ScheduledAlarmRepository,
    private val scheduleAlarmsForWindow: ScheduleAlarmsForWindowUseCase,
    private val alarmScheduler: AlarmScheduler,
    private val nowProvider: NowProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val today = nowProvider.currentLocalDate()
        val now = nowProvider.currentLocalDateTime()
        val periods = scheduleRepository.getAllPeriodsWithTimesOnce()

        val logsByDate: Map<LocalDate, List<IntakeLog>> =
            (0 until ScheduleAlarmsForWindowUseCase.DEFAULT_WINDOW_DAYS).associate { offset ->
                val date = today.plusDays(offset.toLong())
                date to intakeRepository.getLogsForDateOnce(date)
            }

        val desired = scheduleAlarmsForWindow(periods, logsByDate, today, now)
        val desiredByCode = desired.associateBy { it.requestCode }

        val existing = scheduledAlarmRepository.getAll()
        existing.filter { it.requestCode !in desiredByCode }.forEach { stale ->
            alarmScheduler.cancel(stale.requestCode)
            scheduledAlarmRepository.deleteByRequestCode(stale.requestCode)
        }

        desired.forEach { spec ->
            alarmScheduler.schedule(spec)
            scheduledAlarmRepository.upsert(spec)
        }

        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "daily-reschedule-alarms"
        private const val ONE_TIME_WORK_NAME = "reschedule-alarms-now"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyRescheduleWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailyRescheduleWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
