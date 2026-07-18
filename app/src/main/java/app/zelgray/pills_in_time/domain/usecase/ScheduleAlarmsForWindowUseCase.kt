package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.AlarmSpec
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Enumerates real AlarmManager-worthy occurrences over a rolling window
 * (spec 4.5): only occurrences still UPCOMING get an alarm — an occurrence
 * that's already OVERDUE/TAKEN/SKIPPED/MISSED needs no future-firing alarm.
 */
class ScheduleAlarmsForWindowUseCase @Inject constructor(
    private val generateOccurrences: GenerateOccurrencesForDateUseCase,
) {
    operator fun invoke(
        periods: List<ScheduledIntakeWithTimes>,
        logsByDate: Map<LocalDate, List<IntakeLog>>,
        today: LocalDate,
        now: LocalDateTime,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<AlarmSpec> {
        return (0 until windowDays).flatMap { offset ->
            val date = today.plusDays(offset.toLong())
            val logs = logsByDate[date].orEmpty()
            generateOccurrences(periods, logs, date, today, now)
                .filter { it.status == OccurrenceStatus.UPCOMING }
                .map { occurrence ->
                    AlarmSpec(
                        requestCode = computeRequestCode(
                            occurrence.scheduledIntakeId,
                            occurrence.intakeTimeId,
                            occurrence.occurrenceDate,
                        ),
                        scheduledIntakeId = occurrence.scheduledIntakeId,
                        intakeTimeId = occurrence.intakeTimeId,
                        drugId = occurrence.drugId,
                        occurrenceDate = occurrence.occurrenceDate,
                        timeOfDay = occurrence.timeOfDay,
                        triggerAtEpochMilli = LocalDateTime.of(occurrence.occurrenceDate, occurrence.timeOfDay)
                            .atZone(zoneId)
                            .toInstant()
                            .toEpochMilli(),
                        doseValue = occurrence.doseValue,
                        doseMode = occurrence.doseMode,
                    )
                }
        }
    }

    companion object {
        const val DEFAULT_WINDOW_DAYS = 3

        fun computeRequestCode(scheduledIntakeId: Long, intakeTimeId: Long, occurrenceDate: LocalDate): Int {
            var result = scheduledIntakeId.hashCode()
            result = 31 * result + intakeTimeId.hashCode()
            result = 31 * result + occurrenceDate.hashCode()
            return result
        }
    }
}
