package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.Patient
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.domain.model.BackupPayload
import app.zelgray.pills_in_time.domain.model.PatientColorPalette
import app.zelgray.pills_in_time.domain.model.toEntity
import java.time.Instant
import javax.inject.Inject

data class ImportedBackupData(
    val patients: List<Patient>,
    val drugs: List<Drug>,
    val stockBatches: List<DrugStockBatch>,
    val scheduledIntakes: List<ScheduledIntake>,
    val intakeTimes: List<IntakeTime>,
    val intakeLogs: List<IntakeLog>,
    // Null for backups made before this field existed.
    val snoozeMinutes: Int?,
)

class ImportBackupUseCase @Inject constructor() {

    operator fun invoke(payload: BackupPayload): ImportedBackupData {
        // Backups made before multi-patient support existed carry no patients
        // at all — synthesize one default so their drugs have somewhere to go.
        val patients = if (payload.patients.isNotEmpty()) {
            payload.patients.map { it.toEntity() }
        } else {
            listOf(
                Patient(
                    id = 1L,
                    name = PatientColorPalette.DEFAULT_NAME,
                    color = PatientColorPalette.colorForIndex(0),
                    createdAt = Instant.now(),
                ),
            )
        }
        val fallbackPatientId = patients.first().id

        return ImportedBackupData(
            patients = patients,
            drugs = payload.drugs.map { it.toEntity(fallbackPatientId) },
            stockBatches = payload.stockBatches.map { it.toEntity() },
            scheduledIntakes = payload.scheduledIntakes.map { it.toEntity() },
            intakeTimes = payload.intakeTimes.map { it.toEntity() },
            intakeLogs = payload.intakeLogs.map { it.toEntity() },
            snoozeMinutes = payload.snoozeMinutes,
        )
    }
}
