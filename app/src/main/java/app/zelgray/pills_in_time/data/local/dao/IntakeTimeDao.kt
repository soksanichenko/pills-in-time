package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.zelgray.pills_in_time.data.local.entity.IntakeTime

@Dao
interface IntakeTimeDao {

    @Query("SELECT * FROM intake_times WHERE scheduledIntakeId = :scheduledIntakeId ORDER BY timeOfDay")
    suspend fun getTimesForSchedule(scheduledIntakeId: Long): List<IntakeTime>

    @Query("SELECT * FROM intake_times WHERE id = :id")
    suspend fun getById(id: Long): IntakeTime?

    @Query("SELECT * FROM intake_times")
    suspend fun getAllOnce(): List<IntakeTime>

    @Insert
    suspend fun insert(intakeTime: IntakeTime): Long

    @Insert
    suspend fun insertAll(intakeTimes: List<IntakeTime>): List<Long>

    @Update
    suspend fun update(intakeTime: IntakeTime)

    @Delete
    suspend fun delete(intakeTime: IntakeTime)

    @Query("DELETE FROM intake_times WHERE scheduledIntakeId = :scheduledIntakeId")
    suspend fun deleteAllForSchedule(scheduledIntakeId: Long)

    @Query("DELETE FROM intake_times")
    suspend fun deleteAllTimes()
}
