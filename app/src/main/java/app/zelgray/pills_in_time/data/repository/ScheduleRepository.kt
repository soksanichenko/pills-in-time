package app.zelgray.pills_in_time.data.repository

import android.content.Context
import androidx.room.withTransaction
import app.zelgray.pills_in_time.data.local.MedTrackerDatabase
import app.zelgray.pills_in_time.data.local.dao.IntakeTimeDao
import app.zelgray.pills_in_time.data.local.dao.ScheduleDao
import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.notification.DailyRescheduleWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * id == 0 means "new time slot to insert"; a non-zero id updates the existing
 * IntakeTime row in place so IntakeLog rows referencing it (past taken/skipped
 * entries) are preserved rather than cascade-deleted (spec 4.3: editing a
 * period must not disturb history that already carries an explicit status).
 */
data class IntakeTimeInput(
    val id: Long = 0,
    val timeOfDay: LocalTime,
    val doseMode: DoseMode,
    val doseValue: Double,
)

class ScheduleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MedTrackerDatabase,
    private val scheduleDao: ScheduleDao,
    private val intakeTimeDao: IntakeTimeDao,
) {
    fun observePeriodsForDrug(drugId: Long): Flow<List<ScheduledIntakeWithTimes>> =
        scheduleDao.observePeriodsWithTimesForDrug(drugId)

    suspend fun getPeriodsForDrugOnce(drugId: Long): List<ScheduledIntakeWithTimes> =
        scheduleDao.getPeriodsWithTimesForDrugOnce(drugId)

    fun observeAllPeriods(): Flow<List<ScheduledIntakeWithTimes>> =
        scheduleDao.observeAllPeriodsWithTimes()

    suspend fun getAllPeriodsWithTimesOnce(): List<ScheduledIntakeWithTimes> =
        scheduleDao.getAllPeriodsWithTimesOnce()

    suspend fun getById(id: Long): ScheduledIntake? = scheduleDao.getById(id)

    suspend fun getTimesForSchedule(scheduleId: Long) = intakeTimeDao.getTimesForSchedule(scheduleId)

    suspend fun getTimeById(timeId: Long) = intakeTimeDao.getById(timeId)

    suspend fun getLatestPeriodByStartDate(drugId: Long, excludeId: Long = -1): ScheduledIntake? =
        scheduleDao.getLatestPeriodByStartDate(drugId, excludeId)

    suspend fun savePeriod(
        scheduleId: Long?,
        drugId: Long,
        startDate: LocalDate,
        endMode: EndMode,
        endDate: LocalDate?,
        durationDays: Int?,
        cycleType: CycleType,
        specificDays: Set<DayOfWeek>?,
        customCycleText: String?,
        intakeDays: Int? = null,
        breakDays: Int? = null,
        times: List<IntakeTimeInput>,
    ): Long {
        val id = database.withTransaction {
            val savedId = if (scheduleId != null) {
                val existing = scheduleDao.getById(scheduleId)
                if (existing != null) {
                    scheduleDao.update(
                        existing.copy(
                            startDate = startDate,
                            endMode = endMode,
                            endDate = endDate,
                            durationDays = durationDays,
                            cycleType = cycleType,
                            specificDays = specificDays,
                            customCycleText = customCycleText,
                            intakeDays = intakeDays,
                            breakDays = breakDays,
                        ),
                    )
                }
                scheduleId
            } else {
                scheduleDao.insert(
                    ScheduledIntake(
                        drugId = drugId,
                        startDate = startDate,
                        endMode = endMode,
                        endDate = endDate,
                        durationDays = durationDays,
                        cycleType = cycleType,
                        specificDays = specificDays,
                        customCycleText = customCycleText,
                        intakeDays = intakeDays,
                        breakDays = breakDays,
                        createdAt = Instant.now(),
                    ),
                )
            }

            val existingTimes = intakeTimeDao.getTimesForSchedule(savedId)
            val desiredIds = times.mapNotNull { it.id.takeIf { rowId -> rowId != 0L } }.toSet()
            existingTimes.filter { it.id !in desiredIds }.forEach { intakeTimeDao.delete(it) }

            times.forEach { input ->
                if (input.id != 0L) {
                    intakeTimeDao.update(
                        IntakeTime(
                            id = input.id,
                            scheduledIntakeId = savedId,
                            timeOfDay = input.timeOfDay,
                            doseMode = input.doseMode,
                            doseValue = input.doseValue,
                        ),
                    )
                } else {
                    intakeTimeDao.insert(
                        IntakeTime(
                            scheduledIntakeId = savedId,
                            timeOfDay = input.timeOfDay,
                            doseMode = input.doseMode,
                            doseValue = input.doseValue,
                        ),
                    )
                }
            }

            savedId
        }

        // Spec 4.5: notifications recalculate on schedule changes.
        DailyRescheduleWorker.enqueueNow(context)
        return id
    }

    suspend fun deletePeriod(period: ScheduledIntake) {
        scheduleDao.delete(period)
        DailyRescheduleWorker.enqueueNow(context)
    }
}
