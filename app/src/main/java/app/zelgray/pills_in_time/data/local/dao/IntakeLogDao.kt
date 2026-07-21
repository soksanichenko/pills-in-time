package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.relation.IntakeLogWithDrug
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface IntakeLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: IntakeLog): Long

    @Query(
        "SELECT * FROM intake_logs WHERE scheduledIntakeId = :scheduledIntakeId AND intakeTimeId = :intakeTimeId AND occurrenceDate = :date",
    )
    suspend fun getLogForOccurrence(scheduledIntakeId: Long, intakeTimeId: Long, date: LocalDate): IntakeLog?

    @Query("SELECT * FROM intake_logs WHERE occurrenceDate = :date")
    fun observeLogsForDate(date: LocalDate): Flow<List<IntakeLog>>

    @Query("SELECT * FROM intake_logs WHERE occurrenceDate = :date")
    suspend fun getLogsForDateOnce(date: LocalDate): List<IntakeLog>

    @Transaction
    @Query(
        """
        SELECT * FROM intake_logs
        WHERE drugId IN (SELECT id FROM drugs WHERE patientId = :patientId)
        AND occurrenceDate BETWEEN :from AND :to
        AND (:drugId IS NULL OR drugId = :drugId)
        ORDER BY occurrenceDate DESC, actualDateTime DESC
        """,
    )
    fun observeLogsInRange(patientId: Long, from: LocalDate, to: LocalDate, drugId: Long?): Flow<List<IntakeLogWithDrug>>

    @Transaction
    @Query(
        """
        SELECT * FROM intake_logs
        WHERE drugId IN (SELECT id FROM drugs WHERE patientId = :patientId)
        AND (:drugId IS NULL OR drugId = :drugId)
        ORDER BY occurrenceDate DESC, actualDateTime DESC
        """,
    )
    fun observeAllLogs(patientId: Long, drugId: Long?): Flow<List<IntakeLogWithDrug>>

    @Query("SELECT * FROM intake_logs")
    suspend fun getAllLogsOnce(): List<IntakeLog>

    @Insert
    suspend fun insertAll(logs: List<IntakeLog>): List<Long>

    @Update
    suspend fun update(log: IntakeLog)

    @Delete
    suspend fun delete(log: IntakeLog)

    @Query("SELECT * FROM intake_logs WHERE id = :id")
    suspend fun getById(id: Long): IntakeLog?

    @Query("DELETE FROM intake_logs")
    suspend fun deleteAllLogs()
}
