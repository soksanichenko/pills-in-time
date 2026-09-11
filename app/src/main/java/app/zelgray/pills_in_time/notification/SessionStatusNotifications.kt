package app.zelgray.pills_in_time.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import app.zelgray.pills_in_time.MainActivity
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.AlarmKind
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.Patient
import app.zelgray.pills_in_time.domain.usecase.ScheduleAlarmsForWindowUseCase
import java.time.LocalDate

/**
 * Shared by the day-start prompt, interval (HOURLY) dose reminders, and the
 * count-per-day running status, so every post/repost hop builds the exact
 * same notification — mirrors LowStockNotifications' shared-builder
 * convention. All three kinds for a given (scheduledIntakeId, intakeTimeId,
 * date) share one id, so starting the day naturally replaces the prompt in
 * place instead of needing an explicit cancel, and a session's reminders
 * across the day update/replace each other in turn instead of piling up.
 */
object SessionStatusNotifications {

    fun notificationId(scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate): Int =
        ScheduleAlarmsForWindowUseCase.computeRequestCode(scheduledIntakeId, intakeTimeId, date)

    /** Ongoing "start your day?" prompt (see IntakeTime.isSession) — disappears once "День начат" is pressed. */
    fun postPrompt(
        context: Context,
        drug: Drug,
        patient: Patient?,
        showPatientName: Boolean,
        scheduledIntakeId: Long,
        intakeTimeId: Long,
        date: LocalDate,
    ) {
        val id = notificationId(scheduledIntakeId, intakeTimeId, date)
        val title = if (showPatientName && patient != null) {
            "${patient.name} — ${context.getString(R.string.session_start_prompt_title)}"
        } else {
            context.getString(R.string.session_start_prompt_title)
        }

        val startIntent = Intent(context, SessionActionReceiver::class.java).apply {
            action = NotificationContracts.ACTION_START_DAY
            putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, id)
            putExtra(NotificationContracts.EXTRA_DRUG_ID, drug.id)
            putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
            putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, intakeTimeId)
            putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, date.toEpochDay())
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.SESSION_START_PROMPT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.session_start_prompt_text, drug.name))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(context, id))
            .addAction(0, context.getString(R.string.action_start_day), broadcastPendingIntent(context, id, startIntent))

        patient?.let { builder.setColor(it.color) }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /**
     * A single HOURLY dose reminder — behaves like a regular dose reminder
     * (dismissible, re-alerts on every post/5-minute repost — see
     * SessionActionHandler) rather than a persistent status, so it doesn't
     * sit in the shade for the whole interval between doses.
     */
    fun postIntervalReminder(
        context: Context,
        drug: Drug,
        patient: Patient?,
        showPatientName: Boolean,
        time: IntakeTime,
        scheduledIntakeId: Long,
        date: LocalDate,
        contentText: String,
    ) {
        val id = notificationId(scheduledIntakeId, time.id, date)
        val title = if (showPatientName && patient != null) "${patient.name} — ${drug.name}" else drug.name

        val takeIntent = doseActionIntent(context, NotificationContracts.ACTION_TAKE, id, drug.id, scheduledIntakeId, time, date, keepNotification = false)
        val skipIntent = doseActionIntent(context, NotificationContracts.ACTION_SKIP, id, drug.id, scheduledIntakeId, time, date, keepNotification = false)

        val builder = NotificationCompat.Builder(context, NotificationChannels.SESSION_INTERVAL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, id))
            .addAction(0, context.getString(R.string.action_took_it), broadcastPendingIntent(context, id * 10 + 1, takeIntent))
            .addAction(0, context.getString(R.string.action_skipped), broadcastPendingIntent(context, id * 10 + 2, skipIntent))
            .addAction(0, context.getString(R.string.action_end_day), activityPendingIntent(context, id * 10 + 3, endDayIntent(context, id, scheduledIntakeId, time.id, date)))

        patient?.let { builder.setColor(it.color) }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /**
     * The COUNT_PER_DAY running "N of M taken today" status — ongoing for
     * the whole day, updated in place as doses are logged.
     *
     * @param forceAlert re-alerts (sound/vibration) instead of a silent
     * content-only update — used only for the initial post at day-start.
     */
    fun postCountStatus(
        context: Context,
        drug: Drug,
        patient: Patient?,
        showPatientName: Boolean,
        time: IntakeTime,
        scheduledIntakeId: Long,
        date: LocalDate,
        loggedCount: Int,
        forceAlert: Boolean,
    ) {
        val id = notificationId(scheduledIntakeId, time.id, date)
        val titleName = if (showPatientName && patient != null) "${patient.name} — ${drug.name}" else drug.name
        val timesPerDay = time.sessionTimesPerDay ?: loggedCount
        val title = context.getString(R.string.session_count_status_title, titleName, loggedCount, timesPerDay)
        val text = if (loggedCount >= timesPerDay) context.getString(R.string.session_count_status_done, timesPerDay) else ""

        val takeIntent = doseActionIntent(context, NotificationContracts.ACTION_TAKE, id, drug.id, scheduledIntakeId, time, date, keepNotification = true)
        val skipIntent = doseActionIntent(context, NotificationContracts.ACTION_SKIP, id, drug.id, scheduledIntakeId, time, date, keepNotification = true)

        val builder = NotificationCompat.Builder(context, NotificationChannels.SESSION_COUNT_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(!forceAlert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(context, id))
            .addAction(0, context.getString(R.string.action_took_it), broadcastPendingIntent(context, id * 10 + 1, takeIntent))
            .addAction(0, context.getString(R.string.action_skipped), broadcastPendingIntent(context, id * 10 + 2, skipIntent))
            .addAction(0, context.getString(R.string.action_end_day), activityPendingIntent(context, id * 10 + 3, endDayIntent(context, id, scheduledIntakeId, time.id, date)))

        patient?.let { builder.setColor(it.color) }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(context: Context, scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate) {
        val id = notificationId(scheduledIntakeId, intakeTimeId, date)
        NotificationManagerCompat.from(context).cancel(id)
        WorkManager.getInstance(context).cancelUniqueWork(NotificationContracts.repeatWorkName(id))
    }

    private fun doseActionIntent(
        context: Context,
        action: String,
        notificationId: Int,
        drugId: Long,
        scheduledIntakeId: Long,
        time: IntakeTime,
        date: LocalDate,
        keepNotification: Boolean,
    ): Intent = Intent(context, IntakeActionReceiver::class.java).apply {
        this.action = action
        putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, notificationId)
        putExtra(NotificationContracts.EXTRA_DRUG_ID, drugId)
        putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
        putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, time.id)
        putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, date.toEpochDay())
        putExtra(NotificationContracts.EXTRA_DOSE_VALUE, time.doseValue)
        putExtra(NotificationContracts.EXTRA_DOSE_MODE, time.doseMode.name)
        // Marks this as a session dose for LogIntakeActionWorker — routes to
        // IntakeRepository.recordSessionDose instead of recordQuickAction.
        putExtra(NotificationContracts.EXTRA_KIND, AlarmKind.SESSION_TICK.name)
        putExtra(NotificationContracts.EXTRA_KEEP_NOTIFICATION, keepNotification)
    }

    // Opens a confirm dialog (EndDayConfirmActivity) instead of ending the day
    // directly — it sits right next to Took it/Skipped with no undo, so a
    // bare tap used to be able to silently end the whole day.
    private fun endDayIntent(context: Context, notificationId: Int, scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate): Intent =
        Intent(context, EndDayConfirmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
            putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, intakeTimeId)
            putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, date.toEpochDay())
        }

    private fun contentIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun broadcastPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun activityPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
