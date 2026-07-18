package app.zelgray.pills_in_time.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.IntakeLog

data class IntakeLogWithDrug(
    @Embedded val intakeLog: IntakeLog,
    @Relation(
        parentColumn = "drugId",
        entityColumn = "id",
    )
    val drug: Drug,
)
