package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.zelgray.pills_in_time.data.local.entity.DailySession
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface DailySessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: DailySession): Long

    @Query("SELECT * FROM daily_sessions WHERE scheduledIntakeId = :scheduledIntakeId AND date = :date")
    suspend fun getForDate(scheduledIntakeId: Long, date: LocalDate): DailySession?

    @Query("SELECT * FROM daily_sessions WHERE date BETWEEN :startDate AND :endDate")
    fun observeInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailySession>>

    @Query("SELECT * FROM daily_sessions WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getInRangeOnce(startDate: LocalDate, endDate: LocalDate): List<DailySession>

    @Query("SELECT * FROM daily_sessions WHERE date = :date AND endedAt IS NULL")
    suspend fun getActiveForDateOnce(date: LocalDate): List<DailySession>

    @Query("UPDATE daily_sessions SET endedAt = :endedAt WHERE scheduledIntakeId = :scheduledIntakeId AND date = :date")
    suspend fun updateEndedAt(scheduledIntakeId: Long, date: LocalDate, endedAt: Instant)
}
