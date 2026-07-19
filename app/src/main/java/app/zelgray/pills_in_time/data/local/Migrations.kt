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

/**
 * Adds a fixed dose-to-batch-strength allocation on IntakeTime (chosen once at
 * period-setup time when a STRENGTH dose is ambiguous across on-hand
 * strengths) and a registry of what an IntakeLog actually decremented, so
 * editing/deleting a log or changing its status can reverse stock correctly.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE intake_times ADD COLUMN doseAllocation TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS intake_log_consumptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                intakeLogId INTEGER NOT NULL,
                batchId INTEGER NOT NULL,
                quantity REAL NOT NULL,
                FOREIGN KEY(intakeLogId) REFERENCES intake_logs(id) ON DELETE CASCADE,
                FOREIGN KEY(batchId) REFERENCES drug_stock_batches(id) ON DELETE CASCADE
            )
            """,
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_intake_log_consumptions_intakeLogId ON intake_log_consumptions(intakeLogId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_intake_log_consumptions_batchId ON intake_log_consumptions(batchId)")
    }
}

/**
 * Makes DrugStockBatch.strengthValue/strengthUnit optional — a drug that
 * doesn't track strength (capped at a single supply; see
 * AddEditStockViewModel) has no strength at all, rather than a fake
 * placeholder value — and adds ScheduledIntake.durationOccurrences
 * (EndMode.OCCURRENCES: the end date is computed by counting active cycle
 * days instead of a literal calendar-day span, so e.g. "8 occurrences" of a
 * once-a-week cycle spans ~8 weeks, not 8 calendar days).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite can't relax a column's NOT NULL via ALTER TABLE, so rebuild the table.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS drug_stock_batches_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                drugId INTEGER NOT NULL,
                quantity REAL NOT NULL,
                strengthValue REAL,
                strengthUnit TEXT,
                addedAt INTEGER NOT NULL,
                lowStockReminderDaysBefore INTEGER,
                lowStockReminderFiredForRunOutDate INTEGER,
                FOREIGN KEY(drugId) REFERENCES drugs(id) ON DELETE CASCADE
            )
            """,
        )
        db.execSQL(
            """
            INSERT INTO drug_stock_batches_new
            SELECT id, drugId, quantity, strengthValue, strengthUnit, addedAt, lowStockReminderDaysBefore, lowStockReminderFiredForRunOutDate
            FROM drug_stock_batches
            """,
        )
        db.execSQL("DROP TABLE drug_stock_batches")
        db.execSQL("ALTER TABLE drug_stock_batches_new RENAME TO drug_stock_batches")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_drug_stock_batches_drugId ON drug_stock_batches(drugId)")

        db.execSQL("ALTER TABLE scheduled_intakes ADD COLUMN durationOccurrences INTEGER")
    }
}

/**
 * Adds a units-remaining alternative to the existing days-before-run-out
 * low-stock reminder (mutually exclusive in the UI) — lowStockReminderUnitsBefore
 * plus its own dedup flag (lowStockReminderUnitsAlreadyFired), since a units
 * threshold has no forecast date to key a dedup off of.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drug_stock_batches ADD COLUMN lowStockReminderUnitsBefore REAL")
        db.execSQL("ALTER TABLE drug_stock_batches ADD COLUMN lowStockReminderUnitsAlreadyFired INTEGER NOT NULL DEFAULT 0")
    }
}
