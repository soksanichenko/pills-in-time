package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
)
