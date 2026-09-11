package app.zelgray.pills_in_time.notification

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.res.stringResource
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.ui.common.ConfirmDialog
import app.zelgray.pills_in_time.ui.theme.MedTrackerTheme

/**
 * Confirmation step for the "Иду спать" action on a session status
 * notification (see SessionStatusNotifications) — that action sits right
 * next to Took it/Skipped with no undo, so a bare notification tap used to
 * be able to silently end the whole day. Confirming here replays the exact
 * ACTION_END_DAY broadcast SessionActionReceiver already handles, so the
 * actual end-day logic (SessionActionHandler.endDay via EndDayWorker) is
 * untouched.
 */
class EndDayConfirmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val notificationId = intent.getIntExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, -1)
        val scheduledIntakeId = intent.getLongExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
        val intakeTimeId = intent.getLongExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
        val occurrenceDateEpochDay = intent.getLongExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)

        setContent {
            MedTrackerTheme {
                ConfirmDialog(
                    title = stringResource(R.string.end_day_confirm_title),
                    body = stringResource(R.string.end_day_confirm_body),
                    confirmLabel = stringResource(R.string.action_end_day),
                    onConfirm = {
                        sendBroadcast(
                            Intent(this, SessionActionReceiver::class.java).apply {
                                action = NotificationContracts.ACTION_END_DAY
                                putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, notificationId)
                                putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
                                putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, intakeTimeId)
                                putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, occurrenceDateEpochDay)
                            },
                        )
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }
}
