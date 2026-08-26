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
        Index(value = ["scheduledIntakeId", "intakeTimeId", "occurrenceDate", "sessionSeq"], unique = true),
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
    // 0 for an ordinary fixed-time occurrence (the vast majority of logs).
    // For a session-based IntakeTime, each dose within a day gets its own
    // 1-based ordinal, since a session day can produce many logs against the
    // same (scheduledIntakeId, intakeTimeId, occurrenceDate) triple — the
    // uniqueness index above only holds one row per triple otherwise.
    // Deliberately non-nullable: SQLite's UNIQUE index treats every NULL as
    // distinct, so a nullable column here would silently stop deduplicating
    // ordinary logs.
    val sessionSeq: Int = 0,
)
