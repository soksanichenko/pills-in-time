package app.zelgray.pills_in_time.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.zelgray.pills_in_time.data.local.MIGRATION_1_2
import app.zelgray.pills_in_time.data.local.MIGRATION_2_3
import app.zelgray.pills_in_time.data.local.MIGRATION_3_4
import app.zelgray.pills_in_time.data.local.MIGRATION_4_5
import app.zelgray.pills_in_time.data.local.MIGRATION_5_6
import app.zelgray.pills_in_time.data.local.MIGRATION_6_7
import app.zelgray.pills_in_time.data.local.MIGRATION_7_8
import app.zelgray.pills_in_time.data.local.MIGRATION_8_9
import app.zelgray.pills_in_time.data.local.MedTrackerDatabase
import app.zelgray.pills_in_time.data.local.dao.DrugDao
import app.zelgray.pills_in_time.data.local.dao.IntakeLogConsumptionDao
import app.zelgray.pills_in_time.data.local.dao.IntakeLogDao
import app.zelgray.pills_in_time.data.local.dao.IntakeTimeDao
import app.zelgray.pills_in_time.data.local.dao.PatientDao
import app.zelgray.pills_in_time.data.local.dao.ScheduleDao
import app.zelgray.pills_in_time.data.local.dao.ScheduledAlarmDao
import app.zelgray.pills_in_time.data.local.dao.StockBatchDao
import app.zelgray.pills_in_time.domain.model.PatientColorPalette
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
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            )
            // A brand-new install skips every Migration above and creates the
            // schema straight from the entities, so it needs its own default
            // patient seeded here instead (mirrors MIGRATION_8_9's seed row).
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL(
                        "INSERT INTO patients (name, color, createdAt) VALUES (?, ?, ?)",
                        arrayOf<Any>(PatientColorPalette.DEFAULT_NAME, PatientColorPalette.colorForIndex(0), System.currentTimeMillis()),
                    )
                }
            })
            .build()

    @Provides
    fun providePatientDao(database: MedTrackerDatabase): PatientDao = database.patientDao()

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
