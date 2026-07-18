package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "intake_logs",
    foreignKeys = [
        ForeignKey(
            entity = Drug::class,
            parentColumns = ["id"],
            childColumns = ["drugId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ScheduledIntake::class,
            parentColumns = ["id"],
            childColumns = ["scheduledIntakeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IntakeTime::class,
            parentColumns = ["id"],
            childColumns = ["intakeTimeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["scheduledIntakeId", "intakeTimeId", "occurrenceDate"], unique = true),
        Index("drugId"),
        Index("intakeTimeId"),
    ],
)
data class IntakeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drugId: Long,
    val scheduledIntakeId: Long,
    val intakeTimeId: Long,
    val occurrenceDate: LocalDate,
    val status: IntakeStatus,
    val actualDateTime: Instant,
    val actualDoseValue: Double,
    val actualDoseMode: DoseMode,
    val source: IntakeSource,
    val createdAt: Instant,
    val updatedAt: Instant,
)
