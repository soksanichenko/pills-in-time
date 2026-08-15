package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Whether a period is active (within its own start/end bounds, and on-cycle)
 * on a given date. Shared by occurrence generation, stock projection, the
 * duration-by-occurrences end-date computation, and the "active now" period
 * badge, so cycle semantics can't drift between them.
 */
fun isPeriodActiveOn(period: ScheduledIntake, date: LocalDate): Boolean {
    if (date.isBefore(period.startDate)) return false
    val end = period.endDate
    if (end != null && date.isAfter(end)) return false
    val pausedUntil = period.pausedUntilDate
    if (pausedUntil != null && !date.isAfter(pausedUntil)) return false
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

/**
 * The end date for a period whose duration is "N active occurrences"
 * (EndMode.OCCURRENCES) rather than a literal calendar-day span — walks
 * forward from startDate counting only cycle-active days until N are
 * reached, so e.g. 8 occurrences of a once-a-week cycle span ~8 weeks, not 8
 * calendar days (which a literal "duration in days" would give). Capped at a
 * 10-year safety horizon; returns null if the cycle never activates within
 * it (only possible with a misconfigured cycle that other form validation
 * should already reject before this is ever called).
 */
fun computeEndDateForOccurrences(
    startDate: LocalDate,
    cycleType: CycleType,
    specificDays: Set<DayOfWeek>?,
    intakeDays: Int?,
    breakDays: Int?,
    occurrences: Int,
): LocalDate? {
    if (occurrences <= 0) return null
    val probe = ScheduledIntake(
        id = 0,
        drugId = 0,
        startDate = startDate,
        endMode = EndMode.NONE,
        endDate = null,
        durationDays = null,
        cycleType = cycleType,
        specificDays = specificDays,
        customCycleText = null,
        intakeDays = intakeDays,
        breakDays = breakDays,
        createdAt = Instant.EPOCH,
    )
    var count = 0
    var date = startDate
    val horizon = startDate.plusDays(OCCURRENCE_SEARCH_HORIZON_DAYS)
    while (!date.isAfter(horizon)) {
        if (isPeriodActiveOn(probe, date)) {
            count++
            if (count == occurrences) return date
        }
        date = date.plusDays(1)
    }
    return null
}

private const val OCCURRENCE_SEARCH_HORIZON_DAYS = 3650L

/** Sentinel for ScheduledIntake.pausedUntilDate meaning "paused manually, until resumed" rather than a fixed date. */
val INDEFINITE_PAUSE_DATE: LocalDate = LocalDate.of(9999, 12, 31)

/**
 * The last date a "pause for N occurrences" needs to cover to skip exactly
 * [occurrences] of this period's own upcoming active days, starting from
 * [from] (inclusive) — counted against the period's cycle/bounds with any
 * existing pause ignored, so pausing doesn't interact with itself. Returns
 * null if the period doesn't have that many active days left within the
 * horizon (e.g. it ends first).
 */
fun computePauseUntilDateForOccurrences(period: ScheduledIntake, from: LocalDate, occurrences: Int): LocalDate? {
    if (occurrences <= 0) return null
    val unpaused = period.copy(pausedUntilDate = null)
    var count = 0
    var date = from
    val horizon = from.plusDays(OCCURRENCE_SEARCH_HORIZON_DAYS)
    while (!date.isAfter(horizon)) {
        if (isPeriodActiveOn(unpaused, date)) {
            count++
            if (count == occurrences) return date
        }
        date = date.plusDays(1)
    }
    return null
}
