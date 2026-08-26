package app.zelgray.pills_in_time.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.zelgray.pills_in_time.MainActivity
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.AlarmKind
import app.zelgray.pills_in_time.data.local.entity.DailySession
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.Patient
import app.zelgray.pills_in_time.domain.usecase.ScheduleAlarmsForWindowUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Shared by the day-start prompt and the active-session status posts (initial
 * post from StartDayWorker, boot recovery, and every tick/dose refresh from
 * SessionActionHandler), so every hop builds the exact same notification —
 * mirrors LowStockNotifications' shared-builder convention. Both notification
 * kinds for a given (scheduledIntakeId, intakeTimeId, date) share one id, so
 * starting the day naturally replaces the prompt in place instead of needing
 * an explicit cancel.
 */
object SessionStatusNotifications {

    fun notificationId(scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate): Int =
        ScheduleAlarmsForWindowUseCase.computeRequestCode(scheduledIntakeId, intakeTimeId, date)

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

        val builder = NotificationCompat.Builder(context, NotificationChannels.SESSION_REMINDERS)
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
     * @param forceAlert re-alerts (sound/vibration) instead of a silent
     * content-only update — used for an actual hourly due-moment tick, so it
     * isn't missed the way a silently updated ongoing notification would be.
     */
    fun postActiveStatus(
        context: Context,
        drug: Drug,
        patient: Patient?,
        showPatientName: Boolean,
        time: IntakeTime,
        scheduledIntakeId: Long,
        date: LocalDate,
        session: DailySession,
        loggedCount: Int,
        forceAlert: Boolean,
    ) {
        val id = notificationId(scheduledIntakeId, time.id, date)
        val titleName = if (showPatientName && patient != null) "${patient.name} — ${drug.name}" else drug.name

        val (title, text) = when {
            time.sessionIntervalHours != null -> {
                val dueAt = session.startedAt.plus(loggedCount * time.sessionIntervalHours.toLong(), ChronoUnit.HOURS)
                val dueLocal = LocalDateTime.ofInstant(dueAt, ZoneId.systemDefault())
                val body = if (LocalDateTime.now().isBefore(dueLocal)) {
                    context.getString(R.string.session_hourly_next_dose, "%02d:%02d".format(dueLocal.hour, dueLocal.minute))
                } else {
                    context.getString(R.string.session_hourly_dose_due)
                }
                context.getString(R.string.session_hourly_status_title, titleName) to body
            }
            else -> {
                val timesPerDay = time.sessionTimesPerDay ?: loggedCount
                val body = if (loggedCount >= timesPerDay) context.getString(R.string.session_count_status_done, timesPerDay) else ""
                context.getString(R.string.session_count_status_title, titleName, loggedCount, timesPerDay) to body
            }
        }

        val takeIntent = doseActionIntent(context, NotificationContracts.ACTION_TAKE, id, drug.id, scheduledIntakeId, time, date)
        val skipIntent = doseActionIntent(context, NotificationContracts.ACTION_SKIP, id, drug.id, scheduledIntakeId, time, date)
        val endIntent = Intent(context, SessionActionReceiver::class.java).apply {
            action = NotificationContracts.ACTION_END_DAY
            putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, id)
            putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
            putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, time.id)
            putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, date.toEpochDay())
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.SESSION_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(!forceAlert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(context, id))
            .addAction(0, context.getString(R.string.action_took_it), broadcastPendingIntent(context, id * 10 + 1, takeIntent))
            .addAction(0, context.getString(R.string.action_skipped), broadcastPendingIntent(context, id * 10 + 2, skipIntent))
            .addAction(0, context.getString(R.string.action_end_day), broadcastPendingIntent(context, id * 10 + 3, endIntent))

        patient?.let { builder.setColor(it.color) }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(context: Context, scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate) {
        NotificationManagerCompat.from(context).cancel(notificationId(scheduledIntakeId, intakeTimeId, date))
    }

    private fun doseActionIntent(
        context: Context,
        action: String,
        notificationId: Int,
        drugId: Long,
        scheduledIntakeId: Long,
        time: IntakeTime,
        date: LocalDate,
    ): Intent = Intent(context, IntakeActionReceiver::class.java).apply {
        this.action = action
        putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, notificationId)
        putExtra(NotificationContracts.EXTRA_DRUG_ID, drugId)
        putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
        putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, time.id)
        putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, date.toEpochDay())
        putExtra(NotificationContracts.EXTRA_DOSE_VALUE, time.doseValue)
        putExtra(NotificationContracts.EXTRA_DOSE_MODE, time.doseMode.name)
        // Marks this as a session dose for IntakeActionReceiver/LogIntakeActionWorker —
        // routes to IntakeRepository.recordSessionDose instead of recordQuickAction,
        // and keeps the ongoing notification up (only regular reminders auto-cancel).
        putExtra(NotificationContracts.EXTRA_KIND, AlarmKind.SESSION_TICK.name)
    }

    private fun contentIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun broadcastPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
