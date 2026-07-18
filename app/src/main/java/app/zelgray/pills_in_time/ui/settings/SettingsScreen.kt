@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.ui.common.ChipOption
import app.zelgray.pills_in_time.ui.common.ChipSelector
import app.zelgray.pills_in_time.ui.common.ConfirmDialog
import app.zelgray.pills_in_time.ui.common.localizedDateTime
import java.time.Instant

private val SNOOZE_OPTIONS = listOf(5, 10, 15, 30)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(LanguageManager.current()) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConsentResult(result.data)
    }

    LaunchedEffect(state.pendingConsentIntent) {
        state.pendingConsentIntent?.let { pendingIntent ->
            consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }

    LaunchedEffect(state.toastMessageRes) {
        state.toastMessageRes?.let { res ->
            snackbarHostState.showSnackbar(message = context.getString(res))
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(16.dp)) {
            Text(
                text = stringResource(R.string.language_section_title),
                style = MaterialTheme.typography.titleLarge,
            )
            ExposedDropdownMenuBox(
                expanded = languageMenuExpanded,
                onExpandedChange = { languageMenuExpanded = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = stringResource(selectedLanguage.labelRes),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false },
                ) {
                    AppLanguage.entries.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(stringResource(language.labelRes)) },
                            onClick = {
                                selectedLanguage = language
                                LanguageManager.set(language)
                                languageMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.reminders_section_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                text = stringResource(R.string.snooze_minutes_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            ChipSelector(
                options = SNOOZE_OPTIONS.map { ChipOption(it, stringResource(R.string.snooze_minutes_value, it)) },
                selected = state.snoozeMinutes,
                onSelect = viewModel::onSnoozeMinutesChange,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.backup_section_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!state.connected) {
                        Text(
                            text = stringResource(R.string.backup_not_connected),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = viewModel::onConnectClick,
                            enabled = !state.connectBusy,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text(stringResource(R.string.backup_connect_action))
                        }
                    } else {
                        Text(
                            text = if (state.lastBackupEpochMilli != null) {
                                stringResource(
                                    R.string.backup_last_backup,
                                    localizedDateTime(Instant.ofEpochMilli(state.lastBackupEpochMilli!!)),
                                )
                            } else {
                                stringResource(R.string.backup_never_backed_up)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row(modifier = Modifier.padding(top = 12.dp)) {
                            Button(
                                onClick = viewModel::onBackupNowClick,
                                enabled = !state.backupBusy && !state.restoreBusy,
                            ) {
                                if (state.backupBusy) {
                                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                }
                                Text(stringResource(R.string.backup_now_action))
                            }
                            OutlinedButton(
                                onClick = { showRestoreConfirm = true },
                                enabled = !state.backupBusy && !state.restoreBusy,
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text(stringResource(R.string.restore_action))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRestoreConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            body = stringResource(R.string.restore_confirm_body),
            onConfirm = {
                showRestoreConfirm = false
                viewModel.onRestoreConfirmed()
            },
            onDismiss = { showRestoreConfirm = false },
        )
    }
}
