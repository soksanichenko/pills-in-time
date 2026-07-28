package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.zelgray.pills_in_time.data.local.entity.SnoozedOccurrence
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SnoozedOccurrenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snoozed: SnoozedOccurrence)

    @Query("SELECT * FROM snoozed_occurrences WHERE occurrenceDate = :date")
    fun observeForDate(date: LocalDate): Flow<List<SnoozedOccurrence>>

    @Query("SELECT * FROM snoozed_occurrences WHERE occurrenceDate = :date")
    suspend fun getForDateOnce(date: LocalDate): List<SnoozedOccurrence>

    @Query(
        "DELETE FROM snoozed_occurrences " +
            "WHERE scheduledIntakeId = :scheduledIntakeId AND intakeTimeId = :intakeTimeId AND occurrenceDate = :occurrenceDate",
    )
    suspend fun delete(scheduledIntakeId: Long, intakeTimeId: Long, occurrenceDate: LocalDate)
}
