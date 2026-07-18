package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.domain.model.BackupPayload
import app.zelgray.pills_in_time.domain.model.toDto
import java.time.Instant
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor() {

    operator fun invoke(
        drugs: List<Drug>,
        stockBatches: List<DrugStockBatch>,
        scheduledIntakes: List<ScheduledIntake>,
        intakeTimes: List<IntakeTime>,
        intakeLogs: List<IntakeLog>,
        exportedAt: Instant,
    ): BackupPayload = BackupPayload(
        exportedAtEpochMilli = exportedAt.toEpochMilli(),
        drugs = drugs.map { it.toDto() },
        stockBatches = stockBatches.map { it.toDto() },
        scheduledIntakes = scheduledIntakes.map { it.toDto() },
        intakeTimes = intakeTimes.map { it.toDto() },
        intakeLogs = intakeLogs.map { it.toDto() },
    )
}
