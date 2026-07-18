package app.zelgray.pills_in_time.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the "N days on / M days off" cycle type (CycleType.DAYS_ON_OFF). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scheduled_intakes ADD COLUMN intakeDays INTEGER")
        db.execSQL("ALTER TABLE scheduled_intakes ADD COLUMN breakDays INTEGER")
    }
}

/** Adds per-supply low-stock reminders (custom days-before-run-out notice). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drug_stock_batches ADD COLUMN lowStockReminderDaysBefore INTEGER")
        db.execSQL("ALTER TABLE drug_stock_batches ADD COLUMN lowStockReminderFiredForRunOutDate INTEGER")
    }
}
