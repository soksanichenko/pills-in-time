package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.entity.isSession
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** One past dose to retroactively log as taken — see [BackfillHistoryUseCase]. */
data class BackfillLogEntry(
    val occurrenceDate: LocalDate,
    val intakeTimeId: Long,
    val timeOfDay: LocalTime,
    val doseValue: Double,
    val doseMode: DoseMode,
)

/**
 * Lists every past dose a freshly-created, backdated period should have had
 * — offered on save (AddEditPeriodScreen) so re-adding a period that was
 * deleted (which cascades away its whole IntakeLog history, see
 * ScheduledIntake's FK) doesn't leave a permanent gap for the days already
 * covered by the backdated start date. Session-type times (IntakeTime.isSession)
 * are excluded — they have no fixed daily dose count without a DailySession,
 * which a backdated period was never actually running to produce.
 */
class BackfillHistoryUseCase @Inject constructor() {

    operator fun invoke(
        schedule: ScheduledIntake,
        times: List<IntakeTime>,
        until: LocalDate,
    ): List<BackfillLogEntry> {
        val fixedTimes = times.filterNot { it.isSession }
        if (fixedTimes.isEmpty()) return emptyList()

        val lastDate = schedule.startDate.plusDays(MAX_BACKFILL_DAYS).let { cap -> if (until.isBefore(cap)) until else cap }

        val entries = mutableListOf<BackfillLogEntry>()
        var date = schedule.startDate
        while (date.isBefore(lastDate)) {
            if (isPeriodActiveOn(schedule, date)) {
                fixedTimes.forEach { time ->
                    entries += BackfillLogEntry(
                        occurrenceDate = date,
                        intakeTimeId = time.id,
                        timeOfDay = time.timeOfDay,
                        doseValue = time.doseValue,
                        doseMode = time.doseMode,
                    )
                }
            }
            date = date.plusDays(1)
        }
        return entries
    }

    private companion object {
        // Safety cap against an accidental far-past start date generating an
        // enormous backfill — well beyond any real "I forgot to add this
        // period on time" gap.
        const val MAX_BACKFILL_DAYS = 730L
    }
}
