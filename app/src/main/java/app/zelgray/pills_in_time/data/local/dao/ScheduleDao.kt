package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ScheduleDao {

    @Transaction
    @Query("SELECT * FROM scheduled_intakes WHERE drugId = :drugId ORDER BY startDate")
    fun observePeriodsWithTimesForDrug(drugId: Long): Flow<List<ScheduledIntakeWithTimes>>

    @Transaction
    @Query("SELECT * FROM scheduled_intakes WHERE drugId IN (SELECT id FROM drugs WHERE patientId = :patientId)")
    fun observeAllPeriodsWithTimes(patientId: Long): Flow<List<ScheduledIntakeWithTimes>>

    @Transaction
    @Query("SELECT * FROM scheduled_intakes WHERE drugId IN (SELECT id FROM drugs WHERE patientId = :patientId)")
    suspend fun getAllPeriodsWithTimesForPatientOnce(patientId: Long): List<ScheduledIntakeWithTimes>

    @Transaction
    @Query("SELECT * FROM scheduled_intakes")
    suspend fun getAllPeriodsWithTimesOnce(): List<ScheduledIntakeWithTimes>

    @Transaction
    @Query("SELECT * FROM scheduled_intakes WHERE drugId = :drugId ORDER BY startDate")
    suspend fun getPeriodsWithTimesForDrugOnce(drugId: Long): List<ScheduledIntakeWithTimes>

    @Transaction
    @Query(
        "SELECT * FROM scheduled_intakes WHERE startDate <= :date AND (endDate IS NULL OR endDate >= :date)",
    )
    suspend fun getPeriodsActiveOnDate(date: LocalDate): List<ScheduledIntakeWithTimes>

    // "Previous period" for continue-from-previous: the latest period for the
    // drug by start date (regardless of whether it has ended) — matching the
    // prototype's semantics of sorting all periods by start and taking the
    // last one, then separately checking whether *that* period has an end.
    @Query(
        "SELECT * FROM scheduled_intakes WHERE drugId = :drugId AND id != :excludeId ORDER BY startDate DESC LIMIT 1",
    )
    suspend fun getLatestPeriodByStartDate(drugId: Long, excludeId: Long = -1): ScheduledIntake?

    @Query("SELECT * FROM scheduled_intakes WHERE id = :id")
    suspend fun getById(id: Long): ScheduledIntake?

    @Query("SELECT * FROM scheduled_intakes")
    suspend fun getAllOnce(): List<ScheduledIntake>

    @Insert
    suspend fun insert(scheduledIntake: ScheduledIntake): Long

    @Insert
    suspend fun insertAll(scheduledIntakes: List<ScheduledIntake>): List<Long>

    @Update
    suspend fun update(scheduledIntake: ScheduledIntake)

    @Delete
    suspend fun delete(scheduledIntake: ScheduledIntake)

    @Query("DELETE FROM scheduled_intakes")
    suspend fun deleteAllSchedules()
}
