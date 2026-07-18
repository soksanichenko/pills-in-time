package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.Occurrence
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    ): List<Occurrence> {
        val logsByKey = existingLogs.associateBy {
            Triple(it.scheduledIntakeId, it.intakeTimeId, it.occurrenceDate)
        }

        return periods
            .filter { isActiveOn(it.scheduledIntake, date) }
            .flatMap { periodWithTimes ->
                periodWithTimes.times.map { time ->
                    val log = logsByKey[Triple(periodWithTimes.scheduledIntake.id, time.id, date)]
                    Occurrence(
                        scheduledIntakeId = periodWithTimes.scheduledIntake.id,
                        intakeTimeId = time.id,
                        drugId = periodWithTimes.scheduledIntake.drugId,
                        occurrenceDate = date,
                        timeOfDay = time.timeOfDay,
                        doseValue = log?.actualDoseValue ?: time.doseValue,
                        doseMode = log?.actualDoseMode ?: time.doseMode,
                        status = resolveStatus(log, date, today, time.timeOfDay, now, graceMinutes),
                        logId = log?.id,
                    )
                }
            }
            .sortedBy { it.timeOfDay }
    }

    private fun isActiveOn(period: ScheduledIntake, date: LocalDate): Boolean {
        if (date.isBefore(period.startDate)) return false
        val end = period.endDate
        if (end != null && date.isAfter(end)) return false
        return when (period.cycleType) {
            // CUSTOM is a purely descriptive label (spec/prototype parity) —
            // it has no computed effect and behaves like DAILY.
            CycleType.DAILY, CycleType.CUSTOM -> true
            CycleType.EVERY_OTHER_DAY -> ChronoUnit.DAYS.between(period.startDate, date) % 2 == 0L
            CycleType.SPECIFIC_DAYS -> period.specificDays?.contains(date.dayOfWeek) == true
            CycleType.DAYS_ON_OFF -> {
                val on = period.intakeDays
                val off = period.breakDays
                if (on == null || off == null || on <= 0 || off <= 0) {
                    false
                } else {
                    val daysSinceStart = ChronoUnit.DAYS.between(period.startDate, date)
                    daysSinceStart % (on + off) < on
                }
            }
        }
    }

    private fun resolveStatus(
        log: IntakeLog?,
        date: LocalDate,
        today: LocalDate,
        timeOfDay: LocalTime,
        now: LocalDateTime,
        graceMinutes: Long,
    ): OccurrenceStatus {
        if (log != null) {
            return if (log.status == IntakeStatus.TAKEN) OccurrenceStatus.TAKEN else OccurrenceStatus.SKIPPED
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
