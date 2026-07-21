package app.zelgray.pills_in_time.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.zelgray.pills_in_time.data.local.converter.Converters
import app.zelgray.pills_in_time.data.local.dao.DrugDao
import app.zelgray.pills_in_time.data.local.dao.IntakeLogConsumptionDao
import app.zelgray.pills_in_time.data.local.dao.IntakeLogDao
import app.zelgray.pills_in_time.data.local.dao.IntakeTimeDao
import app.zelgray.pills_in_time.data.local.dao.ScheduleDao
import app.zelgray.pills_in_time.data.local.dao.ScheduledAlarmDao
import app.zelgray.pills_in_time.data.local.dao.StockBatchDao
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeLogConsumption
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledAlarm
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake

@Database(
    entities = [
        Drug::class,
        DrugStockBatch::class,
        ScheduledIntake::class,
        IntakeTime::class,
        IntakeLog::class,
        ScheduledAlarm::class,
        IntakeLogConsumption::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MedTrackerDatabase : RoomDatabase() {
    abstract fun drugDao(): DrugDao
    abstract fun stockBatchDao(): StockBatchDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun intakeTimeDao(): IntakeTimeDao
    abstract fun intakeLogDao(): IntakeLogDao
    abstract fun scheduledAlarmDao(): ScheduledAlarmDao
    abstract fun intakeLogConsumptionDao(): IntakeLogConsumptionDao

    companion object {
        const val DATABASE_NAME = "med_tracker.db"
    }
}
