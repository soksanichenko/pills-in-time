package app.zelgray.pills_in_time.data.repository

import androidx.room.withTransaction
import app.zelgray.pills_in_time.data.local.MedTrackerDatabase
import app.zelgray.pills_in_time.data.local.dao.IntakeLogDao
import app.zelgray.pills_in_time.data.local.dao.IntakeTimeDao
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeSource
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.relation.IntakeLogWithDrug
import app.zelgray.pills_in_time.domain.model.DoseConsumptionResult
import app.zelgray.pills_in_time.domain.model.RecordLogResult
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

private class InsufficientStockException : Exception()

class IntakeRepository @Inject constructor(
    private val database: MedTrackerDatabase,
    private val intakeLogDao: IntakeLogDao,
    private val intakeTimeDao: IntakeTimeDao,
    private val stockConsumptionRepository: StockConsumptionRepository,
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
    ): RecordLogResult {
        val existing = intakeLogDao.getLogForOccurrence(scheduledIntakeId, intakeTimeId, occurrenceDate)
        return writeLog(
            existingLog = existing,
            drugId = drugId,
            scheduledIntakeId = scheduledIntakeId,
            intakeTimeId = intakeTimeId,
            occurrenceDate = occurrenceDate,
            actualDateTime = Instant.now(),
            doseValue = doseValue,
            doseMode = doseMode,
            status = status,
            source = IntakeSource.REMINDER,
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
    ): RecordLogResult {
        val existing = intakeLogDao.getLogForOccurrence(scheduledIntakeId, intakeTimeId, occurrenceDate)
        return writeLog(
            existingLog = existing,
            drugId = drugId,
            scheduledIntakeId = scheduledIntakeId,
            intakeTimeId = intakeTimeId,
            occurrenceDate = occurrenceDate,
            actualDateTime = actualDateTime,
            doseValue = doseValue,
            doseMode = doseMode,
            status = status,
            source = IntakeSource.MANUAL,
        )
    }

    /** Edits an existing log's recorded details without changing which occurrence it belongs to. */
    suspend fun updateLogDetails(
        logId: Long,
        actualDateTime: Instant,
        doseValue: Double,
        doseMode: DoseMode,
        status: IntakeStatus,
    ): RecordLogResult {
        val existing = intakeLogDao.getById(logId) ?: return RecordLogResult.Success
        return writeLog(
            existingLog = existing,
            drugId = existing.drugId,
            scheduledIntakeId = existing.scheduledIntakeId,
            intakeTimeId = existing.intakeTimeId,
            occurrenceDate = existing.occurrenceDate,
            actualDateTime = actualDateTime,
            doseValue = doseValue,
            doseMode = doseMode,
            status = status,
            source = existing.source,
        )
    }

    suspend fun deleteLog(log: IntakeLog) {
        database.withTransaction {
            if (log.status == IntakeStatus.TAKEN) {
                stockConsumptionRepository.reverseConsumption(log.id)
            }
            intakeLogDao.delete(log)
        }
    }

    /**
     * Single write path for insert-or-update: reverses any previous TAKEN
     * consumption before touching the log row, then — if the new status is
     * TAKEN — resolves and applies fresh consumption against the
     * now-restored batches. Insufficient stock throws inside the transaction
     * so everything (including the reversal) rolls back atomically and
     * nothing is written at all.
     */
    private suspend fun writeLog(
        existingLog: IntakeLog?,
        drugId: Long,
        scheduledIntakeId: Long,
        intakeTimeId: Long,
        occurrenceDate: LocalDate,
        actualDateTime: Instant,
        doseValue: Double,
        doseMode: DoseMode,
        status: IntakeStatus,
        source: IntakeSource,
    ): RecordLogResult = try {
        database.withTransaction {
            if (existingLog?.status == IntakeStatus.TAKEN) {
                stockConsumptionRepository.reverseConsumption(existingLog.id)
            }

            val now = Instant.now()
            val logId = intakeLogDao.upsertLog(
                IntakeLog(
                    id = existingLog?.id ?: 0,
                    drugId = drugId,
                    scheduledIntakeId = scheduledIntakeId,
                    intakeTimeId = intakeTimeId,
                    occurrenceDate = occurrenceDate,
                    status = status,
                    actualDateTime = actualDateTime,
                    actualDoseValue = doseValue,
                    actualDoseMode = doseMode,
                    source = source,
                    createdAt = existingLog?.createdAt ?: now,
                    updatedAt = now,
                ),
            )

            if (status == IntakeStatus.TAKEN) {
                val intakeTime = intakeTimeDao.getById(intakeTimeId)
                val allocation = intakeTime
                    ?.takeIf { it.doseMode == doseMode && it.doseValue == doseValue }
                    ?.doseAllocation
                val decrements = when (val result = stockConsumptionRepository.resolve(drugId, doseMode, doseValue, allocation)) {
                    is DoseConsumptionResult.Resolved -> result.decrements
                    DoseConsumptionResult.Insufficient -> throw InsufficientStockException()
                }
                stockConsumptionRepository.applyResolvedConsumption(logId, decrements)
            }

            RecordLogResult.Success
        }
    } catch (e: InsufficientStockException) {
        RecordLogResult.InsufficientStock
    }
}
