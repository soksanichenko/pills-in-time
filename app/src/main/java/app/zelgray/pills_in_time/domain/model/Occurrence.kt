package app.zelgray.pills_in_time.domain.model

import app.zelgray.pills_in_time.data.local.entity.DoseMode
import java.time.LocalDate
import java.time.LocalTime

data class Occurrence(
    val scheduledIntakeId: Long,
    val intakeTimeId: Long,
    val drugId: Long,
    val occurrenceDate: LocalDate,
    val timeOfDay: LocalTime,
    val doseValue: Double,
    val doseMode: DoseMode,
    val status: OccurrenceStatus,
    val logId: Long?,
)

/**
 * MISSED is a past occurrence with no log — the app never fabricates a "taken"
 * status for it (deviation from the prototype, which auto-marks unlogged past
 * dates as taken; that would be wrong for real medical tracking).
 */
enum class OccurrenceStatus { UPCOMING, OVERDUE, TAKEN, SKIPPED, MISSED }
