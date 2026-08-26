package app.zelgray.pills_in_time.domain.model

import app.zelgray.pills_in_time.data.local.entity.DoseMode
import java.time.LocalDate
import java.time.LocalTime

data class Occurrence(
    val scheduledIntakeId: Long,
    val intakeTimeId: Long,
    val drugId: Long,
    val occurrenceDate: LocalDate,
    // Null only for a session-based IntakeTime's pending dose (see
    // IntakeTime.isSession) — it has no fixed clock time, only a computed due
    // instant for HOURLY, or none at all for COUNT_PER_DAY.
    val timeOfDay: LocalTime?,
    val doseValue: Double,
    val doseMode: DoseMode,
    val doseAllocation: List<DoseComboPiece>?,
    val status: OccurrenceStatus,
    val logId: Long?,
    // 0 for an ordinary fixed-time occurrence; a session day's individual
    // doses (logged or still-pending) get a 1-based ordinal instead — see
    // IntakeLog.sessionSeq.
    val sessionSeq: Int = 0,
)

/**
 * MISSED is a past occurrence with no log — the app never fabricates a "taken"
 * status for it (deviation from the prototype, which auto-marks unlogged past
 * dates as taken; that would be wrong for real medical tracking).
 *
 * POSTPONED is a today/overdue occurrence the user explicitly snoozed
 * ("Remind later") — shown distinctly from OVERDUE until the snooze delay
 * elapses, since the user already acknowledged it rather than ignoring it.
 */
enum class OccurrenceStatus { UPCOMING, OVERDUE, POSTPONED, TAKEN, SKIPPED, MISSED }

/** Still awaiting a Take/Skip decision — the row's check action and group checklist rely on this. */
val OccurrenceStatus.isActionable: Boolean
    get() = this == OccurrenceStatus.UPCOMING || this == OccurrenceStatus.OVERDUE || this == OccurrenceStatus.POSTPONED
