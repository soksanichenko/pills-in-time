package app.zelgray.pills_in_time.notification

import android.content.Intent
import androidx.work.Data
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.domain.model.AlarmSpec
import app.zelgray.pills_in_time.domain.model.Occurrence
import java.time.LocalDate

/**
 * Shared action strings / extras / WorkManager Data keys used across the
 * scheduling -> posting -> action pipeline (AlarmScheduler -> NotificationPostReceiver
 * -> PostNotificationWorker -> notification action PendingIntents -> IntakeActionReceiver
 * -> LogIntakeActionWorker / SnoozeWorker), so every hop agrees on the same key names.
 */
object NotificationContracts {
    const val ACTION_POST_NOTIFICATION = "app.zelgray.pills_in_time.action.POST_NOTIFICATION"
    const val ACTION_TAKE = "app.zelgray.pills_in_time.action.TAKE"
    const val ACTION_SKIP = "app.zelgray.pills_in_time.action.SKIP"
    const val ACTION_SNOOZE = "app.zelgray.pills_in_time.action.SNOOZE"
    const val ACTION_VIEW_OCCURRENCE = "app.zelgray.pills_in_time.action.VIEW_OCCURRENCE"
    const val ACTION_VIEW_STOCK = "app.zelgray.pills_in_time.action.VIEW_STOCK"
    const val ACTION_SNOOZE_LOW_STOCK = "app.zelgray.pills_in_time.action.SNOOZE_LOW_STOCK"

    const val EXTRA_DRUG_ID = "extra_drug_id"
    const val EXTRA_SCHEDULED_INTAKE_ID = "extra_scheduled_intake_id"
    const val EXTRA_INTAKE_TIME_ID = "extra_intake_time_id"
    const val EXTRA_OCCURRENCE_DATE_EPOCH_DAY = "extra_occurrence_date_epoch_day"
    const val EXTRA_TIME_OF_DAY_SECOND = "extra_time_of_day_second"
    const val EXTRA_DOSE_VALUE = "extra_dose_value"
    const val EXTRA_DOSE_MODE = "extra_dose_mode"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_STATUS = "extra_status"
    const val EXTRA_STOCK_ID = "extra_stock_id"
    const val EXTRA_RUN_OUT_DATE_EPOCH_DAY = "extra_run_out_date_epoch_day"

    fun dataFromSpec(spec: AlarmSpec): Data = Data.Builder()
        .putLong(EXTRA_DRUG_ID, spec.drugId)
        .putLong(EXTRA_SCHEDULED_INTAKE_ID, spec.scheduledIntakeId)
        .putLong(EXTRA_INTAKE_TIME_ID, spec.intakeTimeId)
        .putLong(EXTRA_OCCURRENCE_DATE_EPOCH_DAY, spec.occurrenceDate.toEpochDay())
        .putInt(EXTRA_TIME_OF_DAY_SECOND, spec.timeOfDay.toSecondOfDay())
        .putDouble(EXTRA_DOSE_VALUE, spec.doseValue)
        .putString(EXTRA_DOSE_MODE, spec.doseMode.name)
        .build()

    fun dataFromIntent(intent: Intent): Data = Data.Builder()
        .putLong(EXTRA_DRUG_ID, intent.getLongExtra(EXTRA_DRUG_ID, -1))
        .putLong(EXTRA_SCHEDULED_INTAKE_ID, intent.getLongExtra(EXTRA_SCHEDULED_INTAKE_ID, -1))
        .putLong(EXTRA_INTAKE_TIME_ID, intent.getLongExtra(EXTRA_INTAKE_TIME_ID, -1))
        .putLong(EXTRA_OCCURRENCE_DATE_EPOCH_DAY, intent.getLongExtra(EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1))
        .putInt(EXTRA_TIME_OF_DAY_SECOND, intent.getIntExtra(EXTRA_TIME_OF_DAY_SECOND, 0))
        .putDouble(EXTRA_DOSE_VALUE, intent.getDoubleExtra(EXTRA_DOSE_VALUE, 1.0))
        .putString(EXTRA_DOSE_MODE, intent.getStringExtra(EXTRA_DOSE_MODE) ?: DoseMode.UNITS.name)
        .putInt(EXTRA_NOTIFICATION_ID, intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
        .build()

    fun dataFromOccurrence(occurrence: Occurrence): Data = Data.Builder()
        .putLong(EXTRA_DRUG_ID, occurrence.drugId)
        .putLong(EXTRA_SCHEDULED_INTAKE_ID, occurrence.scheduledIntakeId)
        .putLong(EXTRA_INTAKE_TIME_ID, occurrence.intakeTimeId)
        .putLong(EXTRA_OCCURRENCE_DATE_EPOCH_DAY, occurrence.occurrenceDate.toEpochDay())
        .putInt(EXTRA_TIME_OF_DAY_SECOND, occurrence.timeOfDay.toSecondOfDay())
        .putDouble(EXTRA_DOSE_VALUE, occurrence.doseValue)
        .putString(EXTRA_DOSE_MODE, occurrence.doseMode.name)
        .build()

    fun occurrenceDateOf(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /** Unique WorkManager work name for the repeat/snooze chain of a given occurrence's notification. */
    fun repeatWorkName(notificationId: Int): String = "post_notification_$notificationId"
}

/** Identifies a single occurrence to jump straight to when the app is opened from a notification tap. */
data class OccurrenceRequest(
    val drugId: Long,
    val scheduledIntakeId: Long,
    val intakeTimeId: Long,
    val occurrenceDate: LocalDate,
)

fun Intent.toOccurrenceRequestOrNull(): OccurrenceRequest? {
    if (action != NotificationContracts.ACTION_VIEW_OCCURRENCE) return null
    val drugId = getLongExtra(NotificationContracts.EXTRA_DRUG_ID, -1)
    val scheduledIntakeId = getLongExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
    val intakeTimeId = getLongExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
    val occurrenceDateEpochDay = getLongExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
    if (drugId < 0 || scheduledIntakeId < 0 || intakeTimeId < 0 || occurrenceDateEpochDay < 0) return null
    return OccurrenceRequest(drugId, scheduledIntakeId, intakeTimeId, NotificationContracts.occurrenceDateOf(occurrenceDateEpochDay))
}

/** Identifies a drug to jump straight to its detail screen when opened from a low-stock notification tap. */
data class StockRequest(val drugId: Long)

fun Intent.toStockRequestOrNull(): StockRequest? {
    if (action != NotificationContracts.ACTION_VIEW_STOCK) return null
    val drugId = getLongExtra(NotificationContracts.EXTRA_DRUG_ID, -1)
    val stockId = getLongExtra(NotificationContracts.EXTRA_STOCK_ID, -1)
    if (drugId < 0 || stockId < 0) return null
    return StockRequest(drugId)
}
