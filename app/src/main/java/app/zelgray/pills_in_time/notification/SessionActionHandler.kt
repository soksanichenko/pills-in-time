package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.zelgray.pills_in_time.data.local.entity.DailySession
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.isSession
import app.zelgray.pills_in_time.data.repository.DailySessionRepository
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.repository.PatientRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.domain.usecase.ResolveEffectiveStrengthUseCase
import app.zelgray.pills_in_time.ui.drugs.doseTextPlain
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Orchestrates a session-based IntakeTime's day lifecycle (see
 * IntakeTime.isSession): starting/ending a DailySession, and keeping its
 * notifications in sync. Shared by the notification-action path
 * (StartDayWorker/EndDayWorker/PostSessionStatusWorker/LogIntakeActionWorker)
 * and the manual Start/End day buttons on DrugDetailScreen, so both paths
 * funnel through one implementation instead of duplicating this logic.
 *
 * The two cadence modes behave very differently on purpose:
 *  - HOURLY posts a single dismissible reminder per dose (like a regular
 *    fixed-time reminder, re-alerting every 5 minutes until acted on) —
 *    it would clutter the shade for hours if left pinned between doses.
 *  - COUNT_PER_DAY has no per-dose due moment, so it keeps one persistent
 *    "N of M today" status pinned for the whole day instead.
 *
 * Tick alarms (HOURLY) always re-arm for whenever the next undone dose is
 * actually due (startedAt + loggedCount * intervalHours — the same formula
 * GenerateOccurrencesForDateUseCase's pendingSessionOccurrence uses for the
 * UI), not a fixed wall-clock grid: logging a dose early — e.g. from Home,
 * where the pending occurrence is tappable before its reminder ever fires —
 * used to leave the old grid tick armed, so it could still fire and nag
 * about a dose that wasn't due for another interval yet.
 */
class SessionActionHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val drugRepository: DrugRepository,
    private val patientRepository: PatientRepository,
    private val scheduleRepository: ScheduleRepository,
    private val intakeRepository: IntakeRepository,
    private val stockRepository: StockRepository,
    private val resolveEffectiveStrength: ResolveEffectiveStrengthUseCase,
    private val dailySessionRepository: DailySessionRepository,
    private val alarmScheduler: AlarmScheduler,
) {
    suspend fun startDay(scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate) {
        val time = scheduleRepository.getTimeById(intakeTimeId) ?: return
        val schedule = scheduleRepository.getById(scheduledIntakeId) ?: return
        val drug = drugRepository.getById(schedule.drugId) ?: return
        val startedAt = Instant.now()
        dailySessionRepository.start(scheduledIntakeId, date, startedAt)
        val session = DailySession(scheduledIntakeId = scheduledIntakeId, date = date, startedAt = startedAt)

        if (time.sessionIntervalHours != null) {
            // Dose #1 is due immediately (0 elapsed hours) — posted directly
            // here rather than waiting for a tick alarm. The tick then arms
            // for dose #2, which is next in line regardless of whether #1
            // gets logged (its own re-nagging is the 5-minute repost chain).
            postIntervalDoseReminder(drug, time, scheduledIntakeId, date, session, targetSeq = 1)
            scheduleNextTick(drug.id, scheduledIntakeId, time, date, startedAt, nextSeq = 2)
        } else {
            postCountStatus(drug, time, scheduledIntakeId, date, loggedCount = 0, forceAlert = true)
        }
        // Drops the now-obsolete "start your day?" prompt alarm for today —
        // the next reconcile would exclude it anyway (a DailySession now
        // exists), but this makes the registry consistent immediately.
        DailyRescheduleWorker.enqueueNow(context)
    }

    suspend fun endDay(scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate) {
        val session = dailySessionRepository.getForDate(scheduledIntakeId, date)
        dailySessionRepository.end(scheduledIntakeId, date, Instant.now())
        // Also cancels any pending 5-minute interval-reminder repost.
        SessionStatusNotifications.cancel(context, scheduledIntakeId, intakeTimeId, date)

        val time = scheduleRepository.getTimeById(intakeTimeId)
        if (session != null && time?.sessionIntervalHours != null) {
            val loggedCount = intakeRepository.getSessionDoseCountOnce(scheduledIntakeId, intakeTimeId, date)
            alarmScheduler.cancel(NotificationContracts.computeSessionTickRequestCode(scheduledIntakeId, intakeTimeId, date, loggedCount + 1))
        }
    }

    /** Called right after a session dose is logged (Take/Skip). */
    suspend fun refreshAfterDose(scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate) {
        val session = dailySessionRepository.getForDate(scheduledIntakeId, date) ?: return
        if (session.endedAt != null) return
        val time = scheduleRepository.getTimeById(intakeTimeId) ?: return
        val loggedCount = intakeRepository.getSessionDoseCountOnce(scheduledIntakeId, intakeTimeId, date)

        val timesPerDay = time.sessionTimesPerDay
        if (timesPerDay != null) {
            if (loggedCount >= timesPerDay) {
                endDay(scheduledIntakeId, intakeTimeId, date)
                return
            }
            val schedule = scheduleRepository.getById(scheduledIntakeId) ?: return
            val drug = drugRepository.getById(schedule.drugId) ?: return
            postCountStatus(drug, time, scheduledIntakeId, date, loggedCount, forceAlert = false)
        } else {
            // HOURLY: the dose that was just logged is the one the current
            // reminder (if any) represents — cancel it and its pending
            // repost. Covers logging from Home too, where
            // IntakeActionReceiver never got a chance to do this itself.
            SessionStatusNotifications.cancel(context, scheduledIntakeId, intakeTimeId, date)
        }
    }

    /**
     * AlarmManager alarms and notifications don't survive a reboot — for
     * every DailySession still active when the device went down: COUNT_PER_DAY
     * re-posts its status immediately; HOURLY just re-arms the next tick
     * (posting a reminder immediately could be wrong if it isn't actually
     * due yet — it'll post fresh once it genuinely is).
     */
    suspend fun resumeActiveSessionsAfterBoot(today: LocalDate) {
        val activeSessions = dailySessionRepository.getActiveForDateOnce(today).associateBy { it.scheduledIntakeId }
        if (activeSessions.isEmpty()) return
        val periods = scheduleRepository.getAllPeriodsWithTimesOnce()
        periods.forEach { period ->
            val session = activeSessions[period.scheduledIntake.id] ?: return@forEach
            val time = period.times.find { it.isSession } ?: return@forEach
            val drug = drugRepository.getById(period.scheduledIntake.drugId) ?: return@forEach
            val loggedCount = intakeRepository.getSessionDoseCountOnce(period.scheduledIntake.id, time.id, today)
            if (time.sessionIntervalHours != null) {
                scheduleNextTick(drug.id, period.scheduledIntake.id, time, today, session.startedAt, nextSeq = loggedCount + 1)
            } else {
                postCountStatus(drug, time, period.scheduledIntake.id, today, loggedCount, forceAlert = false)
            }
        }
    }

    /** Called when an HOURLY tick alarm fires — posts the reminder for whichever dose is next undone, and arms the tick after it. */
    suspend fun handleTick(scheduledIntakeId: Long, intakeTimeId: Long, drugId: Long, date: LocalDate) {
        val session = dailySessionRepository.getForDate(scheduledIntakeId, date) ?: return
        if (session.endedAt != null) return
        val time = scheduleRepository.getTimeById(intakeTimeId) ?: return
        val intervalHours = time.sessionIntervalHours ?: return
        val drug = drugRepository.getById(drugId) ?: return
        val loggedCount = intakeRepository.getSessionDoseCountOnce(scheduledIntakeId, intakeTimeId, date)

        // Doses logged early (e.g. from Home, ahead of their reminder) can
        // leave the next undone dose not actually due yet — don't nag about
        // it before its own time, just re-arm for when it genuinely is.
        val dueAt = session.startedAt.plus(loggedCount * intervalHours.toLong(), ChronoUnit.HOURS)
        if (Instant.now().isBefore(dueAt)) {
            scheduleNextTick(drugId, scheduledIntakeId, time, date, session.startedAt, nextSeq = loggedCount + 1)
            return
        }

        postIntervalDoseReminder(drug, time, scheduledIntakeId, date, session, targetSeq = loggedCount + 1)
        // Arms for the dose after the one just posted — its own re-nagging is
        // the 5-minute repost chain, not another tick for the same ordinal.
        scheduleNextTick(drugId, scheduledIntakeId, time, date, session.startedAt, nextSeq = loggedCount + 2)
    }

    /** Called when an interval reminder's 5-minute repost fires — re-nags unless that specific dose has been resolved in the meantime. */
    suspend fun handleIntervalRepost(scheduledIntakeId: Long, intakeTimeId: Long, drugId: Long, date: LocalDate, targetSeq: Int) {
        val session = dailySessionRepository.getForDate(scheduledIntakeId, date) ?: return
        if (session.endedAt != null) return
        val loggedCount = intakeRepository.getSessionDoseCountOnce(scheduledIntakeId, intakeTimeId, date)
        if (loggedCount >= targetSeq) return
        val time = scheduleRepository.getTimeById(intakeTimeId) ?: return
        val drug = drugRepository.getById(drugId) ?: return
        postIntervalDoseReminder(drug, time, scheduledIntakeId, date, session, targetSeq)
    }

    private suspend fun postIntervalDoseReminder(
        drug: Drug,
        time: IntakeTime,
        scheduledIntakeId: Long,
        date: LocalDate,
        session: DailySession,
        targetSeq: Int,
    ) {
        val patients = patientRepository.getAllOnce()
        val patient = patients.find { it.id == drug.patientId }
        val batches = stockRepository.getBatchesForDrugOnce(drug.id)
        val strength = resolveEffectiveStrength(batches)
        val doseText = doseTextPlain(context, time.doseValue, time.doseMode, drug, batches, strength, time.doseAllocation)
        val intervalHours = time.sessionIntervalHours ?: 1
        val dueAt = session.startedAt.plus((targetSeq - 1) * intervalHours.toLong(), ChronoUnit.HOURS)
        val dueLocal = LocalDateTime.ofInstant(dueAt, ZoneId.systemDefault())
        val timeText = "%02d:%02d".format(dueLocal.hour, dueLocal.minute)

        SessionStatusNotifications.postIntervalReminder(
            context, drug, patient, patients.size > 1, time, scheduledIntakeId, date, "$timeText · $doseText",
        )
        scheduleIntervalRepeat(scheduledIntakeId, time.id, drug.id, date, targetSeq)
    }

    private fun scheduleIntervalRepeat(scheduledIntakeId: Long, intakeTimeId: Long, drugId: Long, date: LocalDate, targetSeq: Int) {
        val notificationId = SessionStatusNotifications.notificationId(scheduledIntakeId, intakeTimeId, date)
        val data = Data.Builder()
            .putLong(NotificationContracts.EXTRA_DRUG_ID, drugId)
            .putLong(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
            .putLong(NotificationContracts.EXTRA_INTAKE_TIME_ID, intakeTimeId)
            .putLong(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, date.toEpochDay())
            .putInt(NotificationContracts.EXTRA_SESSION_SEQ, targetSeq)
            .build()
        val request = OneTimeWorkRequestBuilder<PostSessionStatusWorker>()
            .setInputData(data)
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NotificationContracts.repeatWorkName(notificationId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private suspend fun postCountStatus(
        drug: Drug,
        time: IntakeTime,
        scheduledIntakeId: Long,
        date: LocalDate,
        loggedCount: Int,
        forceAlert: Boolean,
    ) {
        val patients = patientRepository.getAllOnce()
        val patient = patients.find { it.id == drug.patientId }
        SessionStatusNotifications.postCountStatus(
            context, drug, patient, patients.size > 1, time, scheduledIntakeId, date, loggedCount, forceAlert,
        )
    }

    /** Arms the tick for whenever dose ordinal [nextSeq] is actually due. */
    private fun scheduleNextTick(drugId: Long, scheduledIntakeId: Long, time: IntakeTime, date: LocalDate, startedAt: Instant, nextSeq: Int) {
        val intervalHours = time.sessionIntervalHours ?: return
        val requestCode = NotificationContracts.computeSessionTickRequestCode(scheduledIntakeId, time.id, date, nextSeq)
        val dueAt = startedAt.plus((nextSeq - 1) * intervalHours.toLong(), ChronoUnit.HOURS)
        alarmScheduler.scheduleSessionTick(
            requestCode = requestCode,
            triggerAtEpochMilli = dueAt.toEpochMilli(),
            drugId = drugId,
            scheduledIntakeId = scheduledIntakeId,
            intakeTimeId = time.id,
            occurrenceDate = date,
            doseValue = time.doseValue,
            doseMode = time.doseMode,
        )
    }
}
