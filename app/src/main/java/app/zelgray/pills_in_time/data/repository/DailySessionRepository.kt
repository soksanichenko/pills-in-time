package app.zelgray.pills_in_time.data.repository

import app.zelgray.pills_in_time.data.local.dao.DailySessionDao
import app.zelgray.pills_in_time.data.local.entity.DailySession
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class DailySessionRepository @Inject constructor(
    private val dailySessionDao: DailySessionDao,
) {
    suspend fun getForDate(scheduledIntakeId: Long, date: LocalDate): DailySession? =
        dailySessionDao.getForDate(scheduledIntakeId, date)

    fun observeInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DailySession>> =
        dailySessionDao.observeInRange(startDate, endDate)

    suspend fun getActiveForDateOnce(date: LocalDate): List<DailySession> =
        dailySessionDao.getActiveForDateOnce(date)

    suspend fun getInRangeOnce(startDate: LocalDate, endDate: LocalDate): List<DailySession> =
        dailySessionDao.getInRangeOnce(startDate, endDate)

    suspend fun start(scheduledIntakeId: Long, date: LocalDate, startedAt: Instant) {
        dailySessionDao.upsert(DailySession(scheduledIntakeId = scheduledIntakeId, date = date, startedAt = startedAt))
    }

    suspend fun end(scheduledIntakeId: Long, date: LocalDate, endedAt: Instant) {
        dailySessionDao.updateEndedAt(scheduledIntakeId, date, endedAt)
    }
}
