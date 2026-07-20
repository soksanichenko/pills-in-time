package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "scheduled_intakes",
    foreignKeys = [
        ForeignKey(
            entity = Drug::class,
            parentColumns = ["id"],
            childColumns = ["drugId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DrugStockBatch::class,
            parentColumns = ["id"],
            childColumns = ["pinnedBatchId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("drugId"), Index("pinnedBatchId")],
)
data class ScheduledIntake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drugId: Long,
    val startDate: LocalDate,
    val endMode: EndMode,
    val endDate: LocalDate?,
    val durationDays: Int?,
    // Only set when endMode == OCCURRENCES: the end date is computed by
    // counting this many active-cycle days forward from startDate, instead
    // of a literal calendar-day span (see durationDays).
    val durationOccurrences: Int? = null,
    val cycleType: CycleType,
    val specificDays: Set<DayOfWeek>?,
    val customCycleText: String?,
    // Only set when cycleType == DAYS_ON_OFF: take the drug for intakeDays,
    // then a intakeDays+breakDays repeats until the period's own end (date/
    // days/none — orthogonal to this cycle, same as every other cycle type).
    val intakeDays: Int? = null,
    val breakDays: Int? = null,
    val createdAt: Instant,
    // Only meaningful for UNITS-mode doses: fixes this whole period's
    // consumption to one specific supply instead of FIFO across every batch
    // the drug has. Null (the default) keeps the old FIFO-across-everything
    // behavior. SET_NULL on delete: removing the pinned batch just falls the
    // period back to unpinned rather than orphaning the reference.
    val pinnedBatchId: Long? = null,
)
