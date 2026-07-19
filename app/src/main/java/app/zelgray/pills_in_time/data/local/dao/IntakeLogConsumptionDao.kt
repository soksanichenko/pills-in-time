package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.zelgray.pills_in_time.data.local.entity.IntakeLogConsumption

@Dao
interface IntakeLogConsumptionDao {

    @Insert
    suspend fun insertAll(consumptions: List<IntakeLogConsumption>)

    @Query("SELECT * FROM intake_log_consumptions WHERE intakeLogId = :logId")
    suspend fun getForLog(logId: Long): List<IntakeLogConsumption>

    @Query("DELETE FROM intake_log_consumptions WHERE intakeLogId = :logId")
    suspend fun deleteForLog(logId: Long)
}
