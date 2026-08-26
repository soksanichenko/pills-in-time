package app.zelgray.pills_in_time.notification

import android.content.Context
import app.zelgray.pills_in_time.data.local.entity.DailySession
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.isSession
import app.zelgray.pills_in_time.data.repository.DailySessionRepository
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.repository.PatientRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Orchestrates a session-based IntakeTime's day lifecycle (see
 * IntakeTime.isSession): starting/ending a DailySession, keeping the ongoing
 * status notification in sync, and scheduling the HOURLY tick chain. Shared
 * by the notification-action path (StartDayWorker/EndDayWorker/
 * PostSessionStatusWorker/LogIntakeActionWorker) and the manual Start/End
 * day buttons on DrugDetailScreen, so both paths funnel through one
 * implementation instead of duplicating this logic.
 *
 * Tick alarms are purely a wall-clock cadence (every intervalHours from
 * startedAt) independent of how many doses have actually been logged —
 * logging early or late (or not at all) never needs to touch the tick chain,
 * it just changes what the next tick's content shows when it fires.
 */
class SessionActionHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val drugRepository: DrugRepository,
    private val patientRepository: PatientRepository,
    private val scheduleRepository: ScheduleRepository,
    private val intakeRepository: IntakeRepository,
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

        postStatus(drug, time, scheduledIntakeId, date, session, loggedCount = 0, forceAlert = true)
        scheduleNextTick(drug.id, scheduledIntakeId, time, date, startedAt)
        // Drops the now-obsolete "start your day?" prompt alarm for today —
        // the next reconcile would exclude it anyway (a DailySession now
        // exists), but this makes the registry consistent immediately.
        DailyRescheduleWorker.enqueueNow(context)
    }

    suspend fun endDay(scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate) {
        val session = dailySessionRepository.getForDate(scheduledIntakeId, date)
        dailySessionRepository.end(scheduledIntakeId, date, Instant.now())
        SessionStatusNotifications.cancel(context, scheduledIntakeId, intakeTimeId, date)

        val time = scheduleRepository.getTimeById(intakeTimeId)
        val intervalHours = time?.sessionIntervalHours
        if (session != null && intervalHours != null) {
            alarmScheduler.cancel(nextTickRequestCode(scheduledIntakeId, intakeTimeId, date, session.startedAt, intervalHours))
        }
    }

    /** Called right after a session dose is logged (Take/Skip) — refreshes the ongoing status, or auto-ends a COUNT_PER_DAY day whose target is now reached. */
    suspend fun refreshAfterDose(scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate) {
        val session = dailySessionRepository.getForDate(scheduledIntakeId, date) ?: return
        if (session.endedAt != null) return
        val time = scheduleRepository.getTimeById(intakeTimeId) ?: return
        val loggedCount = intakeRepository.getSessionDoseCountOnce(scheduledIntakeId, intakeTimeId, date)

        val timesPerDay = time.sessionTimesPerDay
        if (timesPerDay != null && loggedCount >= timesPerDay) {
            endDay(scheduledIntakeId, intakeTimeId, date)
            return
        }

        val schedule = scheduleRepository.getById(scheduledIntakeId) ?: return
        val drug = drugRepository.getById(schedule.drugId) ?: return
        postStatus(drug, time, scheduledIntakeId, date, session, loggedCount, forceAlert = false)
    }

    /**
     * AlarmManager alarms (and the ongoing status notification) don't survive
     * a reboot — re-posts the status and, for HOURLY, re-arms the next tick
     * from wherever it's actually due now, for every DailySession that was
     * still active when the device went down.
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
            postStatus(drug, time, period.scheduledIntake.id, today, session, loggedCount, forceAlert = false)
            scheduleNextTick(drug.id, period.scheduledIntake.id, time, today, session.startedAt)
        }
    }

    /** Called when an hourly tick alarm fires — refreshes the status with a real alert, and arms the next tick. */
    suspend fun handleTick(scheduledIntakeId: Long, intakeTimeId: Long, drugId: Long, date: LocalDate) {
        val session = dailySessionRepository.getForDate(scheduledIntakeId, date) ?: return
        if (session.endedAt != null) return
        val time = scheduleRepository.getTimeById(intakeTimeId) ?: return
        val drug = drugRepository.getById(drugId) ?: return
        val loggedCount = intakeRepository.getSessionDoseCountOnce(scheduledIntakeId, intakeTimeId, date)

        postStatus(drug, time, scheduledIntakeId, date, session, loggedCount, forceAlert = true)
        scheduleNextTick(drugId, scheduledIntakeId, time, date, session.startedAt)
    }

    private suspend fun postStatus(
        drug: Drug,
        time: IntakeTime,
        scheduledIntakeId: Long,
        date: LocalDate,
        session: DailySession,
        loggedCount: Int,
        forceAlert: Boolean,
    ) {
        val patients = patientRepository.getAllOnce()
        val patient = patients.find { it.id == drug.patientId }
        SessionStatusNotifications.postActiveStatus(
            context, drug, patient, patients.size > 1, time, scheduledIntakeId, date, session, loggedCount, forceAlert,
        )
    }

    private fun scheduleNextTick(drugId: Long, scheduledIntakeId: Long, time: IntakeTime, date: LocalDate, startedAt: Instant) {
        val intervalHours = time.sessionIntervalHours ?: return
        val requestCode = nextTickRequestCode(scheduledIntakeId, time.id, date, startedAt, intervalHours)
        val nextTickNumber = ChronoUnit.HOURS.between(startedAt, Instant.now()) / intervalHours + 1
        val dueAt = startedAt.plus(nextTickNumber * intervalHours, ChronoUnit.HOURS)
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

    /** The request code of whichever tick would next fire right now — same formula used to schedule it, so it can be recomputed to cancel. */
    private fun nextTickRequestCode(scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate, startedAt: Instant, intervalHours: Int): Int {
        val nextTickNumber = (ChronoUnit.HOURS.between(startedAt, Instant.now()) / intervalHours + 1).toInt()
        return NotificationContracts.computeSessionTickRequestCode(scheduledIntakeId, intakeTimeId, date, nextTickNumber)
    }
}
