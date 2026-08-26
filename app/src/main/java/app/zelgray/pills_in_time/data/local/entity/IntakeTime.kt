package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.zelgray.pills_in_time.domain.model.DoseComboPiece
import java.time.LocalTime

@Entity(
    tableName = "intake_times",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledIntake::class,
            parentColumns = ["id"],
            childColumns = ["scheduledIntakeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduledIntakeId")],
)
data class IntakeTime(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledIntakeId: Long,
    val timeOfDay: LocalTime,
    val doseMode: DoseMode,
    val doseValue: Double,
    // Fixed at period-setup time when doseMode == STRENGTH and more than one
    // combo of on-hand strengths could satisfy doseValue. Which specific
    // batch(es) actually get decremented at logging time is still resolved
    // fresh via FIFO (oldest addedAt first) among batches matching each
    // piece's strength — that part isn't a user decision, so it isn't stored
    // here. Null for UNITS-mode times (pure FIFO across any batch, no combo
    // concept) and for STRENGTH-mode times saved before this field existed.
    val doseAllocation: List<DoseComboPiece>? = null,
    // When true, the reminder for this time rings/behaves like a system alarm
    // (full-screen over the lock screen, alarm-stream sound) instead of a
    // regular notification — for doses that need to actually wake the patient.
    val isAlarmClock: Boolean = false,
    // Session-based dosing (e.g. hourly eye drops from wake to sleep, or a
    // fixed count per day with no fixed clock times): non-null
    // sessionIntervalHours/sessionTimesPerDay marks this row as session-type
    // instead of fixed-clock (see IntakeTime.isSession below). timeOfDay is
    // unused/kept at its placeholder default for such rows — SQLite can't add
    // a nullable column and relax an existing NOT NULL one without a table
    // rebuild, so it's simplest to just never read it for session rows rather
    // than migrate it to nullable.
    val sessionDayStartFrom: LocalTime? = null,
    val sessionIntervalHours: Int? = null,
    val sessionTimesPerDay: Int? = null,
)

/** Session-type rows have no fixed [IntakeTime.timeOfDay] — exactly one of the two cadence fields is set instead. */
val IntakeTime.isSession: Boolean
    get() = sessionIntervalHours != null || sessionTimesPerDay != null
