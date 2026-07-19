package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Snapshot of exactly which batch(es) a TAKEN IntakeLog decremented and by how
 * much — needed to reverse stock correctly if the log is later edited,
 * deleted, or its status changes away from TAKEN. A registry (not a spec
 * concept), mirroring how ScheduledAlarm registers alarms rather than
 * re-deriving them on demand.
 */
@Entity(
    tableName = "intake_log_consumptions",
    foreignKeys = [
        ForeignKey(
            entity = IntakeLog::class,
            parentColumns = ["id"],
            childColumns = ["intakeLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DrugStockBatch::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("intakeLogId"), Index("batchId")],
)
data class IntakeLogConsumption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val intakeLogId: Long,
    val batchId: Long,
    val quantity: Double,
)
