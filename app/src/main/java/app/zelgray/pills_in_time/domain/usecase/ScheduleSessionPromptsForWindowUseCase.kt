package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.AlarmKind
import app.zelgray.pills_in_time.data.local.entity.DailySession
import app.zelgray.pills_in_time.data.local.entity.isSession
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.AlarmSpec
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Enumerates the "start your day?" prompt alarms for every session-based
 * IntakeTime (see IntakeTime.isSession) over a rolling window — mirrors
 * ScheduleAlarmsForWindowUseCase's shape, but iterates session times
 * directly rather than through GenerateOccurrencesForDateUseCase, since a
 * day-start prompt isn't a dose occurrence. A date that already has a
 * DailySession (the day was already started, e.g. via the Home button before
 * the prompt fired) is skipped — nothing left to prompt for that day.
 *
 * Request codes are computed with the same
 * ScheduleAlarmsForWindowUseCase.computeRequestCode used for dose reminders —
 * safe to share, since a given IntakeTime is either session-type or not,
 * never both, so the two id spaces are drawn from disjoint intakeTimeIds.
 */
class ScheduleSessionPromptsForWindowUseCase @Inject constructor() {

    operator fun invoke(
        periods: List<ScheduledIntakeWithTimes>,
        sessionsByDate: Map<LocalDate, List<DailySession>>,
        today: LocalDate,
        windowDays: Int = ScheduleAlarmsForWindowUseCase.DEFAULT_WINDOW_DAYS,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<AlarmSpec> {
        return (0 until windowDays).flatMap { offset ->
            val date = today.plusDays(offset.toLong())
            val alreadyStarted = sessionsByDate[date].orEmpty().map { it.scheduledIntakeId }.toSet()

            periods
                .filter { isPeriodActiveOn(it.scheduledIntake, date) }
                .filter { it.scheduledIntake.id !in alreadyStarted }
                .flatMap { periodWithTimes ->
                    periodWithTimes.times
                        .filter { it.isSession }
                        .mapNotNull { time ->
                            val promptTime = time.sessionDayStartFrom ?: return@mapNotNull null
                            AlarmSpec(
                                requestCode = ScheduleAlarmsForWindowUseCase.computeRequestCode(
                                    periodWithTimes.scheduledIntake.id,
                                    time.id,
                                    date,
                                ),
                                scheduledIntakeId = periodWithTimes.scheduledIntake.id,
                                intakeTimeId = time.id,
                                drugId = periodWithTimes.scheduledIntake.drugId,
                                occurrenceDate = date,
                                timeOfDay = promptTime,
                                triggerAtEpochMilli = LocalDateTime.of(date, promptTime)
                                    .atZone(zoneId)
                                    .toInstant()
                                    .toEpochMilli(),
                                doseValue = time.doseValue,
                                doseMode = time.doseMode,
                                kind = AlarmKind.SESSION_START_PROMPT,
                            )
                        }
                }
        }
    }
}
