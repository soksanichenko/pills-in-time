package app.zelgray.pills_in_time.data.repository

import app.zelgray.pills_in_time.data.local.dao.ScheduledAlarmDao
import app.zelgray.pills_in_time.data.local.entity.ScheduledAlarm
import app.zelgray.pills_in_time.domain.model.AlarmSpec
import javax.inject.Inject

class ScheduledAlarmRepository @Inject constructor(
    private val scheduledAlarmDao: ScheduledAlarmDao,
) {
    suspend fun getAll(): List<ScheduledAlarm> = scheduledAlarmDao.getAll()

    suspend fun upsert(spec: AlarmSpec) = scheduledAlarmDao.upsert(
        ScheduledAlarm(
            requestCode = spec.requestCode,
            scheduledIntakeId = spec.scheduledIntakeId,
            intakeTimeId = spec.intakeTimeId,
            occurrenceDate = spec.occurrenceDate,
            triggerAtMillis = spec.triggerAtEpochMilli,
        ),
    )

    suspend fun deleteByRequestCode(requestCode: Int) = scheduledAlarmDao.deleteByRequestCode(requestCode)
}
