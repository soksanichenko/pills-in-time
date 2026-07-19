package app.zelgray.pills_in_time.di

import android.content.Context
import androidx.room.Room
import app.zelgray.pills_in_time.data.local.MIGRATION_1_2
import app.zelgray.pills_in_time.data.local.MIGRATION_2_3
import app.zelgray.pills_in_time.data.local.MIGRATION_3_4
import app.zelgray.pills_in_time.data.local.MIGRATION_4_5
import app.zelgray.pills_in_time.data.local.MIGRATION_5_6
import app.zelgray.pills_in_time.data.local.MedTrackerDatabase
import app.zelgray.pills_in_time.data.local.dao.DrugDao
import app.zelgray.pills_in_time.data.local.dao.IntakeLogConsumptionDao
import app.zelgray.pills_in_time.data.local.dao.IntakeLogDao
import app.zelgray.pills_in_time.data.local.dao.IntakeTimeDao
import app.zelgray.pills_in_time.data.local.dao.ScheduleDao
import app.zelgray.pills_in_time.data.local.dao.ScheduledAlarmDao
import app.zelgray.pills_in_time.data.local.dao.StockBatchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MedTrackerDatabase =
        Room.databaseBuilder(context, MedTrackerDatabase::class.java, MedTrackerDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideDrugDao(database: MedTrackerDatabase): DrugDao = database.drugDao()

    @Provides
    fun provideStockBatchDao(database: MedTrackerDatabase): StockBatchDao = database.stockBatchDao()

    @Provides
    fun provideScheduleDao(database: MedTrackerDatabase): ScheduleDao = database.scheduleDao()

    @Provides
    fun provideIntakeTimeDao(database: MedTrackerDatabase): IntakeTimeDao = database.intakeTimeDao()

    @Provides
    fun provideIntakeLogDao(database: MedTrackerDatabase): IntakeLogDao = database.intakeLogDao()

    @Provides
    fun provideScheduledAlarmDao(database: MedTrackerDatabase): ScheduledAlarmDao =
        database.scheduledAlarmDao()

    @Provides
    fun provideIntakeLogConsumptionDao(database: MedTrackerDatabase): IntakeLogConsumptionDao =
        database.intakeLogConsumptionDao()
}
