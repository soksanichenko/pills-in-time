package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "drug_stock_batches",
    foreignKeys = [
        ForeignKey(
            entity = Drug::class,
            parentColumns = ["id"],
            childColumns = ["drugId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("drugId")],
)
data class DrugStockBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drugId: Long,
    val quantity: Double,
    // Null means this drug doesn't track dose strength at all — in that case
    // the drug is capped at a single supply (see AddEditStockViewModel),
    // since strength is what distinguishes/justifies more than one batch.
    val strengthValue: Double?,
    val strengthUnit: StrengthUnit?,
    val addedAt: Instant,
    // null = no low-stock reminder configured for this batch.
    val lowStockReminderDaysBefore: Int? = null,
    // The projected run-out date we last fired a notification for, so the
    // daily check doesn't re-notify on every run while the forecast is
    // unchanged. Re-fires automatically if the forecast shifts (e.g. the
    // drug's periods change) since that produces a different date.
    val lowStockReminderFiredForRunOutDate: LocalDate? = null,
)
