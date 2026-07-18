package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import java.time.LocalDate

@Entity(tableName = "scheduled_alarms", primaryKeys = ["requestCode"])
data class ScheduledAlarm(
    val requestCode: Int,
    val scheduledIntakeId: Long,
    val intakeTimeId: Long,
    val occurrenceDate: LocalDate,
    val triggerAtMillis: Long,
)
