package app.zelgray.pills_in_time.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake

data class ScheduledIntakeWithTimes(
    @Embedded val scheduledIntake: ScheduledIntake,
    @Relation(
        parentColumn = "id",
        entityColumn = "scheduledIntakeId",
    )
    val times: List<IntakeTime>,
)
