package app.zelgray.pills_in_time.data.repository

import app.zelgray.pills_in_time.data.local.dao.IntakeLogDao
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeSource
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.relation.IntakeLogWithDrug
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class IntakeRepository @Inject constructor(
    private val intakeLogDao: IntakeLogDao,
) {
    fun observeLogsForDate(date: LocalDate): Flow<List<IntakeLog>> = intakeLogDao.observeLogsForDate(date)

    suspend fun getLogsForDateOnce(date: LocalDate): List<IntakeLog> = intakeLogDao.getLogsForDateOnce(date)

    fun observeLogsInRange(from: LocalDate, to: LocalDate, drugId: Long?): Flow<List<IntakeLogWithDrug>> =
        intakeLogDao.observeLogsInRange(from, to, drugId)

    fun observeAllLogs(drugId: Long?): Flow<List<IntakeLogWithDrug>> = intakeLogDao.observeAllLogs(drugId)

    suspend fun getById(logId: Long): IntakeLog? = intakeLogDao.getById(logId)

    suspend fun getLogForOccurrenceOnce(scheduledIntakeId: Long, intakeTimeId: Long, occurrenceDate: LocalDate): IntakeLog? =
        intakeLogDao.getLogForOccurrence(scheduledIntakeId, intakeTimeId, occurrenceDate)

    /**
     * Quick in-app actions (Home row check button, action-sheet Took it/Skipped)
     * are tagged source = REMINDER, distinct only from the retroactive manual
     * entry form (source = MANUAL) — confirmed decision, not a separate
     * "quick action" source value.
     */
    suspend fun recordQuickAction(
        drugId: Long,
        scheduledIntakeId: Long,
        intakeTimeId: Long,
        occurrenceDate: LocalDate,
        doseValue: Double,
        doseMode: DoseMode,
        status: IntakeStatus,
    ) {
        val now = Instant.now()
        val existing = intakeLogDao.getLogForOccurrence(scheduledIntakeId, intakeTimeId, occurrenceDate)
        intakeLogDao.upsertLog(
            IntakeLog(
                id = existing?.id ?: 0,
                drugId = drugId,
                scheduledIntakeId = scheduledIntakeId,
                intakeTimeId = intakeTimeId,
                occurrenceDate = occurrenceDate,
                status = status,
                actualDateTime = now,
                actualDoseValue = doseValue,
                actualDoseMode = doseMode,
                source = IntakeSource.REMINDER,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun recordManualEntry(
        drugId: Long,
        scheduledIntakeId: Long,
        intakeTimeId: Long,
        occurrenceDate: LocalDate,
        actualDateTime: Instant,
        doseValue: Double,
        doseMode: DoseMode,
        status: IntakeStatus,
    ) {
        val now = Instant.now()
        val existing = intakeLogDao.getLogForOccurrence(scheduledIntakeId, intakeTimeId, occurrenceDate)
        intakeLogDao.upsertLog(
            IntakeLog(
                id = existing?.id ?: 0,
                drugId = drugId,
                scheduledIntakeId = scheduledIntakeId,
                intakeTimeId = intakeTimeId,
                occurrenceDate = occurrenceDate,
                status = status,
                actualDateTime = actualDateTime,
                actualDoseValue = doseValue,
                actualDoseMode = doseMode,
                source = IntakeSource.MANUAL,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /** Edits an existing log's recorded details without changing which occurrence it belongs to. */
    suspend fun updateLogDetails(
        logId: Long,
        actualDateTime: Instant,
        doseValue: Double,
        doseMode: DoseMode,
        status: IntakeStatus,
    ) {
        val existing = intakeLogDao.getById(logId) ?: return
        intakeLogDao.update(
            existing.copy(
                actualDateTime = actualDateTime,
                actualDoseValue = doseValue,
                actualDoseMode = doseMode,
                status = status,
                updatedAt = Instant.now(),
            ),
        )
    }

    suspend fun deleteLog(log: IntakeLog) = intakeLogDao.delete(log)
}
