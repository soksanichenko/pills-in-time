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
            intakeLogs = renumberLegacySessionLogs(payload.intakeLogs.map { it.toEntity() }),
            snoozeMinutes = payload.snoozeMinutes,
        )
    }

    /**
     * Backups made before IntakeLogDto carried sessionSeq (schema version <
     * 11) flattened every session dose of a day to sessionSeq = 0, so a day
     * with several real doses decodes as several logs all sharing the exact
     * same (scheduledIntakeId, intakeTimeId, occurrenceDate, sessionSeq)
     * key — which the live intake_logs unique index would otherwise reject
     * outright on restore. A live-written log can never actually collide
     * like this (upsertLog always keeps exactly one row per key), so seeing
     * more than one here only ever means an old export lost the real
     * ordinals — recover them by renumbering in the order they were taken.
     */
    private fun renumberLegacySessionLogs(logs: List<IntakeLog>): List<IntakeLog> =
        logs.groupBy { Triple(it.scheduledIntakeId, it.intakeTimeId, it.occurrenceDate) }
            .flatMap { (_, group) ->
                val zeroSeq = group.filter { it.sessionSeq == 0 }
                if (zeroSeq.size <= 1) {
                    group
                } else {
                    val renumbered = zeroSeq.sortedBy { it.actualDateTime }
                        .mapIndexed { index, log -> log.copy(sessionSeq = index + 1) }
                    group.filter { it.sessionSeq != 0 } + renumbered
                }
            }
}
