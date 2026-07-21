package app.zelgray.pills_in_time.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.zelgray.pills_in_time.MainActivity
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.Patient
import app.zelgray.pills_in_time.ui.common.localizedDatePlain
import app.zelgray.pills_in_time.ui.common.pluralUnitTextPlain
import app.zelgray.pills_in_time.ui.common.strengthUnitAbbreviationPlain
import app.zelgray.pills_in_time.util.formatPlainNumber
import java.time.LocalDate

/**
 * Shared by LowStockCheckWorker (first post) and SnoozeLowStockReminderWorker
 * (postponed re-post), so both hops build the exact same notification.
 */
object LowStockNotifications {

    fun post(
        context: Context,
        drug: Drug,
        batch: DrugStockBatch,
        runOutDate: LocalDate?,
        patient: Patient?,
        showPatientName: Boolean,
    ) {
        val notificationId = notificationIdFor(batch.id)

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                action = NotificationContracts.ACTION_VIEW_STOCK
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(NotificationContracts.EXTRA_DRUG_ID, drug.id)
                putExtra(NotificationContracts.EXTRA_STOCK_ID, batch.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(context, LowStockActionReceiver::class.java).apply {
            action = NotificationContracts.ACTION_SNOOZE_LOW_STOCK
            putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationContracts.EXTRA_DRUG_ID, drug.id)
            putExtra(NotificationContracts.EXTRA_STOCK_ID, batch.id)
            putExtra(NotificationContracts.EXTRA_RUN_OUT_DATE_EPOCH_DAY, runOutDate?.toEpochDay() ?: -1L)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val remainingText = pluralUnitTextPlain(context, drug.form, drug.customFormText, batch.quantity)
        val supplyLabel = batch.strengthValue?.let { value ->
            batch.strengthUnit?.let { unit -> "${formatPlainNumber(value)} ${strengthUnitAbbreviationPlain(context, unit)}" }
        }
        val bodyText = when {
            supplyLabel != null && runOutDate != null -> context.getString(
                R.string.low_stock_notification_text_with_supply,
                supplyLabel,
                remainingText,
                localizedDatePlain(runOutDate),
            )
            supplyLabel != null -> context.getString(
                R.string.low_stock_notification_text_with_supply_no_date,
                supplyLabel,
                remainingText,
            )
            runOutDate != null -> context.getString(R.string.low_stock_notification_text, remainingText, localizedDatePlain(runOutDate))
            else -> context.getString(R.string.low_stock_notification_text_no_date, remainingText)
        }

        val titleDrugName = if (showPatientName && patient != null) "${patient.name} — ${drug.name}" else drug.name
        val builder = NotificationCompat.Builder(context, NotificationChannels.LOW_STOCK_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.low_stock_notification_title, titleDrugName))
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.low_stock_snooze_action), snoozePendingIntent)

        patient?.let { builder.setColor(it.color) }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    // Distinct, negative range so these can never collide with the hash-derived
    // dose-reminder notification ids (ScheduleAlarmsForWindowUseCase.computeRequestCode).
    fun notificationIdFor(batchId: Long): Int = (-1_000_000 - batchId).toInt()
}
