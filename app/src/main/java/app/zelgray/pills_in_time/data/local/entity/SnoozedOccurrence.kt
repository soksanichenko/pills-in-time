package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import java.time.Instant
import java.time.LocalDate

/**
 * Registry of "Remind later"-snoozed occurrences, keyed by the same composite
 * triple as IntakeLog, so GenerateOccurrencesForDateUseCase can report
 * OccurrenceStatus.POSTPONED instead of OVERDUE while snoozedUntil hasn't
 * passed yet. Snoozing itself never writes an IntakeLog (the occurrence stays
 * unresolved), so this is the only persisted trace of it.
 */
@Entity(tableName = "snoozed_occurrences", primaryKeys = ["scheduledIntakeId", "intakeTimeId", "occurrenceDate"])
data class SnoozedOccurrence(
    val scheduledIntakeId: Long,
    val intakeTimeId: Long,
    val occurrenceDate: LocalDate,
    val snoozedUntil: Instant,
)
