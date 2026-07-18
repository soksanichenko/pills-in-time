package app.zelgray.pills_in_time.ui.settings

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.remote.drive.DriveAuthResult
import app.zelgray.pills_in_time.data.repository.BackupRepository
import app.zelgray.pills_in_time.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class PendingDriveAction { CONNECT, BACKUP, RESTORE }

data class SettingsUiState(
    val snoozeMinutes: Int = SettingsRepository.DEFAULT_SNOOZE_MINUTES,
    val connected: Boolean = false,
    val lastBackupEpochMilli: Long? = null,
    val connectBusy: Boolean = false,
    val backupBusy: Boolean = false,
    val restoreBusy: Boolean = false,
    val pendingConsentIntent: PendingIntent? = null,
    val toastMessageRes: Int? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var pendingAction: PendingDriveAction? = null

    init {
        viewModelScope.launch {
            settingsRepository.snoozeMinutes.collect { minutes -> _uiState.update { it.copy(snoozeMinutes = minutes) } }
        }
        viewModelScope.launch {
            settingsRepository.driveConnected.collect { connected -> _uiState.update { it.copy(connected = connected) } }
        }
        viewModelScope.launch {
            settingsRepository.lastBackupEpochMilli.collect { last -> _uiState.update { it.copy(lastBackupEpochMilli = last) } }
        }
    }

    fun onSnoozeMinutesChange(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSnoozeMinutes(minutes) }
    }

    fun onConnectClick() = performDriveAction(PendingDriveAction.CONNECT)
    fun onBackupNowClick() = performDriveAction(PendingDriveAction.BACKUP)
    fun onRestoreConfirmed() = performDriveAction(PendingDriveAction.RESTORE)

    fun onConsentResult(data: Intent?) {
        val action = pendingAction ?: return
        _uiState.update { it.copy(pendingConsentIntent = null) }
        viewModelScope.launch {
            when (val result = backupRepository.resultFromIntent(data)) {
                is DriveAuthResult.Authorized -> completeAction(action, result.accessToken)
                is DriveAuthResult.NeedsConsent -> {
                    setBusy(action, false)
                    showToast(R.string.backup_error_generic)
                }
                is DriveAuthResult.Error -> {
                    setBusy(action, false)
                    showToast(R.string.backup_error_generic)
                }
            }
        }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessageRes = null) }
    }

    private fun performDriveAction(action: PendingDriveAction) {
        pendingAction = action
        setBusy(action, true)
        viewModelScope.launch {
            when (val result = backupRepository.requestAuthorization()) {
                is DriveAuthResult.Authorized -> completeAction(action, result.accessToken)
                is DriveAuthResult.NeedsConsent -> _uiState.update { it.copy(pendingConsentIntent = result.pendingIntent) }
                is DriveAuthResult.Error -> {
                    setBusy(action, false)
                    showToast(R.string.backup_error_generic)
                }
            }
        }
    }

    private suspend fun completeAction(action: PendingDriveAction, accessToken: String) {
        when (action) {
            PendingDriveAction.CONNECT -> {
                settingsRepository.setDriveConnected(true)
                setBusy(action, false)
                showToast(R.string.backup_connected_toast)
            }
            PendingDriveAction.BACKUP -> {
                try {
                    backupRepository.backup(accessToken)
                    settingsRepository.setDriveConnected(true)
                    settingsRepository.setLastBackupEpochMilli(System.currentTimeMillis())
                    showToast(R.string.backup_complete_toast)
                } catch (e: Exception) {
                    showToast(R.string.backup_error_generic)
                } finally {
                    setBusy(action, false)
                }
            }
            PendingDriveAction.RESTORE -> {
                try {
                    backupRepository.restore(accessToken)
                    settingsRepository.setDriveConnected(true)
                    showToast(R.string.restore_complete_toast)
                } catch (e: Exception) {
                    showToast(R.string.restore_error_generic)
                } finally {
                    setBusy(action, false)
                }
            }
        }
    }

    private fun setBusy(action: PendingDriveAction, busy: Boolean) {
        _uiState.update {
            when (action) {
                PendingDriveAction.CONNECT -> it.copy(connectBusy = busy)
                PendingDriveAction.BACKUP -> it.copy(backupBusy = busy)
                PendingDriveAction.RESTORE -> it.copy(restoreBusy = busy)
            }
        }
    }

    private fun showToast(res: Int) {
        _uiState.update { it.copy(toastMessageRes = res) }
    }
}
