package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.zelgray.pills_in_time.data.local.entity.ScheduledAlarm

@Dao
interface ScheduledAlarmDao {

    @Query("SELECT * FROM scheduled_alarms")
    suspend fun getAll(): List<ScheduledAlarm>

    @Query("SELECT * FROM scheduled_alarms WHERE scheduledIntakeId = :scheduledIntakeId")
    suspend fun getForSchedule(scheduledIntakeId: Long): List<ScheduledAlarm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: ScheduledAlarm)

    @Delete
    suspend fun delete(alarm: ScheduledAlarm)

    @Query("DELETE FROM scheduled_alarms WHERE requestCode = :requestCode")
    suspend fun deleteByRequestCode(requestCode: Int)

    @Query("DELETE FROM scheduled_alarms WHERE scheduledIntakeId = :scheduledIntakeId")
    suspend fun deleteForSchedule(scheduledIntakeId: Long)

    @Query("DELETE FROM scheduled_alarms")
    suspend fun deleteAll()
}
