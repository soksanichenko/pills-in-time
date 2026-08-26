package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DailySession
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.SnoozedOccurrence
import app.zelgray.pills_in_time.data.local.entity.isSession
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.Occurrence
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Generates the virtual list of intake occurrences for a single calendar date
 * from active periods/times, left-joined against any existing IntakeLog row
 * for that exact (scheduledIntakeId, intakeTimeId, occurrenceDate) triple.
 *
 * Two deliberate deviations from the prototype this ports from:
 *  - Overdue is computed for real (current time vs scheduled time + grace),
 *    not hardcoded via seed data.
 *  - A past date with no log is reported as MISSED, never fabricated as TAKEN.
 */
class GenerateOccurrencesForDateUseCase @Inject constructor() {

    operator fun invoke(
        periods: List<ScheduledIntakeWithTimes>,
        existingLogs: List<IntakeLog>,
        date: LocalDate,
        today: LocalDate,
        now: LocalDateTime,
        graceMinutes: Long = 0,
        snoozed: List<SnoozedOccurrence> = emptyList(),
        sessions: List<DailySession> = emptyList(),
    ): List<Occurrence> {
        val logsByTriple = existingLogs.groupBy {
            Triple(it.scheduledIntakeId, it.intakeTimeId, it.occurrenceDate)
        }
        val snoozedByKey = snoozed.associateBy {
            Triple(it.scheduledIntakeId, it.intakeTimeId, it.occurrenceDate)
        }
        val sessionByPeriod = sessions.associateBy { it.scheduledIntakeId to it.date }

        return periods
            .filter { isPeriodActiveOn(it.scheduledIntake, date) }
            .flatMap { periodWithTimes ->
                val scheduledIntakeId = periodWithTimes.scheduledIntake.id
                val drugId = periodWithTimes.scheduledIntake.drugId
                periodWithTimes.times.flatMap { time ->
                    if (time.isSession) {
                        sessionOccurrences(
                            scheduledIntakeId, drugId, time, date, now, graceMinutes,
                            logsByTriple[Triple(scheduledIntakeId, time.id, date)].orEmpty(),
                            sessionByPeriod[scheduledIntakeId to date],
                        )
                    } else {
                        val key = Triple(scheduledIntakeId, time.id, date)
                        val log = logsByTriple[key]?.firstOrNull()
                        val snoozedUntil = snoozedByKey[key]?.snoozedUntil
                        listOf(
                            Occurrence(
                                scheduledIntakeId = scheduledIntakeId,
                                intakeTimeId = time.id,
                                drugId = drugId,
                                occurrenceDate = date,
                                timeOfDay = time.timeOfDay,
                                doseValue = log?.actualDoseValue ?: time.doseValue,
                                doseMode = log?.actualDoseMode ?: time.doseMode,
                                doseAllocation = time.doseAllocation,
                                status = resolveStatus(log, snoozedUntil, date, today, time.timeOfDay, now, graceMinutes),
                                logId = log?.id,
                            ),
                        )
                    }
                }
            }
            .sortedWith(compareBy(nullsLast()) { it.timeOfDay })
    }

    /**
     * A session-type time (see IntakeTime.isSession) has no fixed clock time
     * and can produce many occurrences for one date instead of exactly one:
     * one per already-logged dose (ordered by sessionSeq), plus — while the
     * day's DailySession is still open — one synthetic pending occurrence for
     * the next dose. No DailySession yet for this date means the day hasn't
     * been started, so nothing is due at all.
     */
    private fun sessionOccurrences(
        scheduledIntakeId: Long,
        drugId: Long,
        time: IntakeTime,
        date: LocalDate,
        now: LocalDateTime,
        graceMinutes: Long,
        logsForTime: List<IntakeLog>,
        session: DailySession?,
    ): List<Occurrence> {
        if (session == null) return emptyList()

        val loggedDoses = logsForTime.filter { it.sessionSeq > 0 }.sortedBy { it.sessionSeq }
        val intervalHours = time.sessionIntervalHours
        val loggedOccurrences = loggedDoses.map { log ->
            // HOURLY doses sit on the same fixed grid the pending occurrence
            // below computes from — gives them a real sort position instead of
            // sorting after every real-time pending occurrence via nullsLast.
            // COUNT_PER_DAY has no clock concept, so stays null (its seq order
            // is preserved by sortedWith's stability instead).
            val loggedTimeOfDay = intervalHours?.let {
                val dueAt = session.startedAt.plus((log.sessionSeq - 1) * it.toLong(), ChronoUnit.HOURS)
                LocalDateTime.ofInstant(dueAt, ZoneId.systemDefault()).toLocalTime()
            }
            Occurrence(
                scheduledIntakeId = scheduledIntakeId,
                intakeTimeId = time.id,
                drugId = drugId,
                occurrenceDate = date,
                timeOfDay = loggedTimeOfDay,
                doseValue = log.actualDoseValue,
                doseMode = log.actualDoseMode,
                doseAllocation = time.doseAllocation,
                status = if (log.status == IntakeStatus.TAKEN) OccurrenceStatus.TAKEN else OccurrenceStatus.SKIPPED,
                logId = log.id,
                sessionSeq = log.sessionSeq,
            )
        }

        val pending = if (session.endedAt == null) {
            pendingSessionOccurrence(scheduledIntakeId, drugId, time, date, now, graceMinutes, session, loggedDoses.size)
        } else {
            null
        }

        return loggedOccurrences + listOfNotNull(pending)
    }

    private fun pendingSessionOccurrence(
        scheduledIntakeId: Long,
        drugId: Long,
        time: IntakeTime,
        date: LocalDate,
        now: LocalDateTime,
        graceMinutes: Long,
        session: DailySession,
        loggedCount: Int,
    ): Occurrence? {
        val nextSeq = loggedCount + 1
        val intervalHours = time.sessionIntervalHours
        val timesPerDay = time.sessionTimesPerDay
        return when {
            intervalHours != null -> {
                val dueAt: Instant = session.startedAt.plus(loggedCount * intervalHours.toLong(), ChronoUnit.HOURS)
                val dueDateTime = LocalDateTime.ofInstant(dueAt, ZoneId.systemDefault())
                val status = if (now.isAfter(dueDateTime.plusMinutes(graceMinutes))) OccurrenceStatus.OVERDUE else OccurrenceStatus.UPCOMING
                Occurrence(
                    scheduledIntakeId = scheduledIntakeId,
                    intakeTimeId = time.id,
                    drugId = drugId,
                    occurrenceDate = date,
                    timeOfDay = dueDateTime.toLocalTime(),
                    doseValue = time.doseValue,
                    doseMode = time.doseMode,
                    doseAllocation = time.doseAllocation,
                    status = status,
                    logId = null,
                    sessionSeq = nextSeq,
                )
            }
            timesPerDay != null && loggedCount < timesPerDay -> Occurrence(
                scheduledIntakeId = scheduledIntakeId,
                intakeTimeId = time.id,
                drugId = drugId,
                occurrenceDate = date,
                timeOfDay = null,
                doseValue = time.doseValue,
                doseMode = time.doseMode,
                doseAllocation = time.doseAllocation,
                // No clock to be "overdue" against — a COUNT_PER_DAY dose is
                // simply due the moment the day is active.
                status = OccurrenceStatus.OVERDUE,
                logId = null,
                sessionSeq = nextSeq,
            )
            else -> null
        }
    }

    private fun resolveStatus(
        log: IntakeLog?,
        snoozedUntil: Instant?,
        date: LocalDate,
        today: LocalDate,
        timeOfDay: LocalTime,
        now: LocalDateTime,
        graceMinutes: Long,
    ): OccurrenceStatus {
        if (log != null) {
            return if (log.status == IntakeStatus.TAKEN) OccurrenceStatus.TAKEN else OccurrenceStatus.SKIPPED
        }
        if (snoozedUntil != null && now.isBefore(LocalDateTime.ofInstant(snoozedUntil, ZoneId.systemDefault()))) {
            return OccurrenceStatus.POSTPONED
        }
        return when {
            date.isAfter(today) -> OccurrenceStatus.UPCOMING
            date.isBefore(today) -> OccurrenceStatus.MISSED
            else -> {
                val scheduledDateTime = LocalDateTime.of(date, timeOfDay).plusMinutes(graceMinutes)
                if (now.isAfter(scheduledDateTime)) OccurrenceStatus.OVERDUE else OccurrenceStatus.UPCOMING
            }
        }
    }
}
