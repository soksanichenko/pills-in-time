package app.zelgray.pills_in_time.domain.model

import app.zelgray.pills_in_time.data.local.entity.DoseMode
import java.time.LocalDate
import java.time.LocalTime

data class AlarmSpec(
    val requestCode: Int,
    val scheduledIntakeId: Long,
    val intakeTimeId: Long,
    val drugId: Long,
    val occurrenceDate: LocalDate,
    val timeOfDay: LocalTime,
    val triggerAtEpochMilli: Long,
    val doseValue: Double,
    val doseMode: DoseMode,
)
