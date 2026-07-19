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
)
