package app.zelgray.pills_in_time.di

import android.content.Context
import androidx.room.Room
import app.zelgray.pills_in_time.data.local.MedTrackerDatabase
import app.zelgray.pills_in_time.data.local.dao.DrugDao
import app.zelgray.pills_in_time.data.local.dao.IntakeLogDao
import app.zelgray.pills_in_time.data.local.dao.IntakeTimeDao
import app.zelgray.pills_in_time.data.local.dao.ScheduleDao
import app.zelgray.pills_in_time.data.local.dao.ScheduledAlarmDao
import app.zelgray.pills_in_time.data.local.dao.StockBatchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Replaces the production DatabaseModule with a fresh in-memory Room DB per test process. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MedTrackerDatabase =
        Room.inMemoryDatabaseBuilder(context, MedTrackerDatabase::class.java).build()

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
}
