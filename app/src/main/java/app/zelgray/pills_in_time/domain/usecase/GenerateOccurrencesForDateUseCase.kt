package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.entity.SnoozedOccurrence
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.Occurrence
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
    ): List<Occurrence> {
        val logsByKey = existingLogs.associateBy {
            Triple(it.scheduledIntakeId, it.intakeTimeId, it.occurrenceDate)
        }
        val snoozedByKey = snoozed.associateBy {
            Triple(it.scheduledIntakeId, it.intakeTimeId, it.occurrenceDate)
        }

        return periods
            .filter { isPeriodActiveOn(it.scheduledIntake, date) }
            .flatMap { periodWithTimes ->
                periodWithTimes.times.map { time ->
                    val key = Triple(periodWithTimes.scheduledIntake.id, time.id, date)
                    val log = logsByKey[key]
                    val snoozedUntil = snoozedByKey[key]?.snoozedUntil
                    Occurrence(
                        scheduledIntakeId = periodWithTimes.scheduledIntake.id,
                        intakeTimeId = time.id,
                        drugId = periodWithTimes.scheduledIntake.drugId,
                        occurrenceDate = date,
                        timeOfDay = time.timeOfDay,
                        doseValue = log?.actualDoseValue ?: time.doseValue,
                        doseMode = log?.actualDoseMode ?: time.doseMode,
                        doseAllocation = time.doseAllocation,
                        status = resolveStatus(log, snoozedUntil, date, today, time.timeOfDay, now, graceMinutes),
                        logId = log?.id,
                    )
                }
            }
            .sortedBy { it.timeOfDay }
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
