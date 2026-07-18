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
    ],
    indices = [Index("drugId")],
)
data class ScheduledIntake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drugId: Long,
    val startDate: LocalDate,
    val endMode: EndMode,
    val endDate: LocalDate?,
    val durationDays: Int?,
    val cycleType: CycleType,
    val specificDays: Set<DayOfWeek>?,
    val customCycleText: String?,
    // Only set when cycleType == DAYS_ON_OFF: take the drug for intakeDays,
    // then a intakeDays+breakDays repeats until the period's own end (date/
    // days/none — orthogonal to this cycle, same as every other cycle type).
    val intakeDays: Int? = null,
    val breakDays: Int? = null,
    val createdAt: Instant,
)
