package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.domain.model.BackupPayload
import app.zelgray.pills_in_time.domain.model.toEntity
import javax.inject.Inject

data class ImportedBackupData(
    val drugs: List<Drug>,
    val stockBatches: List<DrugStockBatch>,
    val scheduledIntakes: List<ScheduledIntake>,
    val intakeTimes: List<IntakeTime>,
    val intakeLogs: List<IntakeLog>,
    // Null for backups made before this field existed.
    val snoozeMinutes: Int?,
)

class ImportBackupUseCase @Inject constructor() {

    operator fun invoke(payload: BackupPayload): ImportedBackupData = ImportedBackupData(
        drugs = payload.drugs.map { it.toEntity() },
        stockBatches = payload.stockBatches.map { it.toEntity() },
        scheduledIntakes = payload.scheduledIntakes.map { it.toEntity() },
        intakeTimes = payload.intakeTimes.map { it.toEntity() },
        intakeLogs = payload.intakeLogs.map { it.toEntity() },
        snoozeMinutes = payload.snoozeMinutes,
    )
}
