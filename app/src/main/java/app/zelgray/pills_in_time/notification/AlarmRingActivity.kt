package app.zelgray.pills_in_time.notification

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.domain.usecase.ResolveEffectiveStrengthUseCase
import app.zelgray.pills_in_time.ui.drugs.doseTextPlain
import app.zelgray.pills_in_time.ui.theme.MedTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalTime
import javax.inject.Inject

/**
 * Full-screen "ringing" surface for a time marked isAlarmClock — launched via
 * the reminder notification's full-screen intent (or by tapping it if the
 * screen was already on). Deliberately thin: it re-derives the same drug/dose
 * text PostNotificationWorker shows, and its 3 actions just replay the exact
 * broadcast IntakeActionReceiver already handles from the regular notification
 * buttons, so Take/Skip/Snooze behave identically either way.
 */
@AndroidEntryPoint
class AlarmRingActivity : AppCompatActivity() {

    @Inject lateinit var drugRepository: DrugRepository

    @Inject lateinit var stockRepository: StockRepository

    @Inject lateinit var scheduleRepository: ScheduleRepository

    @Inject lateinit var resolveEffectiveStrength: ResolveEffectiveStrengthUseCase

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        startRinging()

        val notificationId = intent.getIntExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, -1)
        val drugId = intent.getLongExtra(NotificationContracts.EXTRA_DRUG_ID, -1)
        val scheduledIntakeId = intent.getLongExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
        val intakeTimeId = intent.getLongExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
        val occurrenceDateEpochDay = intent.getLongExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
        val timeOfDaySecond = intent.getIntExtra(NotificationContracts.EXTRA_TIME_OF_DAY_SECOND, 0)
        val doseValue = intent.getDoubleExtra(NotificationContracts.EXTRA_DOSE_VALUE, 1.0)
        val doseMode = runCatching { DoseMode.valueOf(intent.getStringExtra(NotificationContracts.EXTRA_DOSE_MODE) ?: "") }
            .getOrDefault(DoseMode.UNITS)

        fun act(status: IntakeStatus?, snooze: Boolean) {
            stopRinging()
            // IntakeActionReceiver itself cancels the shown notification and its
            // pending 5-minute repeat once it receives this broadcast.
            val action = when {
                snooze -> NotificationContracts.ACTION_SNOOZE
                status == IntakeStatus.TAKEN -> NotificationContracts.ACTION_TAKE
                else -> NotificationContracts.ACTION_SKIP
            }
            sendBroadcast(
                Intent(this, IntakeActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(NotificationContracts.EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(NotificationContracts.EXTRA_DRUG_ID, drugId)
                    putExtra(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, scheduledIntakeId)
                    putExtra(NotificationContracts.EXTRA_INTAKE_TIME_ID, intakeTimeId)
                    putExtra(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, occurrenceDateEpochDay)
                    putExtra(NotificationContracts.EXTRA_TIME_OF_DAY_SECOND, timeOfDaySecond)
                    putExtra(NotificationContracts.EXTRA_DOSE_VALUE, doseValue)
                    putExtra(NotificationContracts.EXTRA_DOSE_MODE, doseMode.name)
                },
            )
            finish()
        }

        setContent {
            MedTrackerTheme {
                var title by remember { mutableStateOf("") }
                var subtitle by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    val drug = drugRepository.getById(drugId)
                    if (drug == null) {
                        finish()
                        return@LaunchedEffect
                    }
                    val batches = stockRepository.getBatchesForDrugOnce(drugId)
                    val strength = resolveEffectiveStrength(batches)
                    val doseAllocation = scheduleRepository.getTimeById(intakeTimeId)?.doseAllocation
                    val doseText = doseTextPlain(this@AlarmRingActivity, doseValue, doseMode, drug, batches, strength, doseAllocation)
                    val timeOfDay = LocalTime.ofSecondOfDay(timeOfDaySecond.toLong())
                    title = drug.name
                    subtitle = "%02d:%02d · %s".format(timeOfDay.hour, timeOfDay.minute, doseText)
                }

                var muted by remember { mutableStateOf(false) }

                AlarmRingScreen(
                    title = title,
                    subtitle = subtitle,
                    muted = muted,
                    onMute = {
                        stopRinging()
                        muted = true
                    },
                    onTake = { act(IntakeStatus.TAKEN, snooze = false) },
                    onSkip = { act(IntakeStatus.SKIPPED, snooze = false) },
                    onSnooze = { act(null, snooze = true) },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startRinging() {
        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        mediaPlayer = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmRingActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            } catch (e: Exception) {
                // Missing/unreadable alarm tone shouldn't crash the ring screen —
                // the full-screen UI and vibration still get the patient's attention.
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopRinging() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Already stopped/released — nothing to clean up.
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }
}

@Composable
private fun AlarmRingScreen(
    title: String,
    subtitle: String,
    muted: Boolean,
    onMute: () -> Unit,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Alarm,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(24.dp))
            if (!muted) {
                OutlinedButton(onClick = onMute, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_mute_alarm))
                }
                Spacer(modifier = Modifier.size(24.dp))
            }
            Button(onClick = onTake, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_took_it))
            }
            Spacer(modifier = Modifier.size(12.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_snooze))
            }
            Spacer(modifier = Modifier.size(12.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_skipped))
            }
        }
    }
}
