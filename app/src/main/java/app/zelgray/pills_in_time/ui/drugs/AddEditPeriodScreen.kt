@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.drugs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.notification.AlarmPermissions
import app.zelgray.pills_in_time.ui.common.ChipOption
import app.zelgray.pills_in_time.ui.common.ChipSelector
import app.zelgray.pills_in_time.ui.common.ConfirmDialog
import app.zelgray.pills_in_time.ui.common.DatePickerField
import app.zelgray.pills_in_time.ui.common.TimePickerField
import app.zelgray.pills_in_time.ui.common.localizedDate
import app.zelgray.pills_in_time.ui.common.strengthUnitAbbreviation
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AddEditPeriodScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddEditPeriodViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestBack = { if (viewModel.isDirty()) showDiscardDialog = true else onBack() }
    BackHandler(onBack = requestBack)

    if (showDiscardDialog) {
        ConfirmDialog(
            title = stringResource(R.string.unsaved_changes_title),
            body = stringResource(R.string.unsaved_changes_body),
            confirmLabel = stringResource(R.string.action_discard),
            dismissLabel = stringResource(R.string.action_keep_editing),
            onConfirm = { showDiscardDialog = false; onBack() },
            onDismiss = { showDiscardDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.edit_period_title else R.string.add_period_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            StartDateSection(state, viewModel)

            SectionTitle(stringResource(R.string.period_end_label))
            EndSection(state, viewModel)

            SectionTitle(stringResource(R.string.period_times_label))
            TimesSection(state, viewModel)

            SectionTitle(stringResource(R.string.period_cycle_label))
            CycleSection(state, viewModel)

            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun StartDateSection(state: AddEditPeriodUiState, viewModel: AddEditPeriodViewModel) {
    SectionTitle(stringResource(R.string.period_start_label))

    if (state.hasPreviousEndedPeriod) {
        ChipSelector(
            options = listOf(
                ChipOption(StartMode.CUSTOM, stringResource(R.string.period_start_custom)),
                ChipOption(StartMode.CONTINUE, stringResource(R.string.period_start_continue)),
            ),
            selected = state.startMode,
            onSelect = viewModel::onStartModeChange,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
    }

    if (state.startMode == StartMode.CONTINUE && state.previousPeriodEndDate != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(
                        R.string.period_continues_from,
                        localizedDate(state.previousPeriodEndDate),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        R.string.period_starts_on,
                        localizedDate(state.effectiveStartDate),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    } else {
        DatePickerField(
            label = stringResource(R.string.period_start_label),
            date = state.customStartDate,
            onDateChange = viewModel::onCustomStartDateChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EndSection(state: AddEditPeriodUiState, viewModel: AddEditPeriodViewModel) {
    ChipSelector(
        options = buildList {
            add(ChipOption(EndMode.DATE, stringResource(R.string.end_mode_date)))
            add(ChipOption(EndMode.DAYS, stringResource(R.string.end_mode_days)))
            // "N occurrences" only means something different from "N days" for
            // a cycle that isn't active every day.
            if (state.occurrenceDurationAvailable) {
                add(ChipOption(EndMode.OCCURRENCES, stringResource(R.string.end_mode_occurrences)))
            }
            add(ChipOption(EndMode.NONE, stringResource(R.string.end_mode_none)))
        },
        selected = state.endMode,
        onSelect = viewModel::onEndModeChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )

    when (state.endMode) {
        EndMode.DATE -> DatePickerField(
            label = stringResource(R.string.period_end_label),
            date = state.endDate,
            onDateChange = viewModel::onEndDateChange,
            modifier = Modifier.fillMaxWidth(),
        )
        EndMode.DAYS -> Column {
            OutlinedTextField(
                value = state.durationDaysText,
                onValueChange = viewModel::onDurationDaysChange,
                label = { Text(stringResource(R.string.duration_days_label)) },
                isError = state.durationDaysError,
                supportingText = {
                    if (state.durationDaysError) Text(stringResource(R.string.duration_days_error))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            state.computedEndDate?.let {
                Text(
                    text = stringResource(R.string.period_ends_on, localizedDate(it)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        EndMode.OCCURRENCES -> Column {
            OutlinedTextField(
                value = state.durationOccurrencesText,
                onValueChange = viewModel::onDurationOccurrencesChange,
                label = { Text(stringResource(R.string.duration_occurrences_label)) },
                isError = state.durationOccurrencesError,
                supportingText = {
                    if (state.durationOccurrencesError) Text(stringResource(R.string.duration_occurrences_error))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            state.computedEndDate?.let {
                Text(
                    text = stringResource(R.string.period_ends_on, localizedDate(it)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        EndMode.NONE -> Text(
            text = stringResource(R.string.end_mode_none_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimesSection(state: AddEditPeriodUiState, viewModel: AddEditPeriodViewModel) {
    val context = LocalContext.current
    Column {
        state.times.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimePickerField(
                            label = stringResource(R.string.time_label),
                            time = row.timeOfDay,
                            onTimeChange = { viewModel.onTimeOfDayChange(row.rowKey, it) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.onRemoveTimeRow(row.rowKey) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete))
                        }
                    }

                    FilterChip(
                        selected = row.isAlarmClock,
                        onClick = {
                            val turningOn = !row.isAlarmClock
                            viewModel.onTimeAlarmClockChange(row.rowKey, turningOn)
                            // Full-screen-intent access has no in-app request dialog on
                            // Android 14+ — jump straight to the settings screen for it
                            // right when the user opts in, instead of making them notice
                            // and tap a separate hint below.
                            if (turningOn && !AlarmPermissions.canUseFullScreenIntent(context)) {
                                context.startActivity(AlarmPermissions.fullScreenIntentSettingsIntent(context))
                            }
                        },
                        label = { Text(stringResource(R.string.time_alarm_style_label)) },
                        leadingIcon = if (row.isAlarmClock) {
                            { Icon(Icons.Filled.Alarm, contentDescription = null) }
                        } else {
                            null
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (row.isAlarmClock) {
                        FullScreenIntentHint()
                    }

                    ChipSelector(
                        options = listOf(
                            ChipOption(DoseMode.UNITS, stringResource(R.string.dose_mode_units)),
                            ChipOption(
                                DoseMode.STRENGTH,
                                stringResource(R.string.dose_mode_strength),
                                enabled = state.strengthModeAvailable,
                            ),
                        ),
                        selected = row.doseMode,
                        onSelect = { viewModel.onTimeDoseModeChange(row.rowKey, it) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )

                    OutlinedTextField(
                        value = row.doseValueText,
                        onValueChange = { viewModel.onTimeDoseValueChange(row.rowKey, it) },
                        label = { Text(stringResource(R.string.dose_value_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                    )

                    if (row.doseMode == DoseMode.UNITS && state.pinnedSupplyAvailable) {
                        SupplyChips(state, viewModel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }

                    viewModel.dosePreviewFor(row)?.let { preview ->
                        Text(
                            text = formatDosePreview(preview),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        // Only ask when the dose is genuinely ambiguous — more
                        // than one combo of on-hand strengths satisfies it.
                        if (preview is DosePreview.ExactCombos && preview.combos.size > 1) {
                            val unitSuffix = " " + strengthUnitAbbreviation(preview.unit)
                            ChipSelector(
                                options = preview.combos.map { combo ->
                                    ChipOption(combo, formatCombo(combo, unitSuffix))
                                },
                                selected = viewModel.selectedComboFor(row, preview.combos) ?: preview.combos.first(),
                                onSelect = { combo -> viewModel.onComboSelected(row.rowKey, combo) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        if (state.timesError) {
            Text(
                text = stringResource(R.string.times_error),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TimePickerField(
                label = stringResource(R.string.new_time_label),
                time = state.newTimeInput,
                onTimeChange = viewModel::onNewTimeInputChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(8.dp))
            TextButton(onClick = viewModel::onAddTimeRow) {
                Text(stringResource(R.string.add_time_action))
            }
        }
    }
}

@Composable
private fun FullScreenIntentHint() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(AlarmPermissions.canUseFullScreenIntent(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = AlarmPermissions.canUseFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!granted) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = stringResource(R.string.full_screen_intent_rationale),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { context.startActivity(AlarmPermissions.fullScreenIntentSettingsIntent(context)) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.full_screen_intent_enable))
            }
        }
    }
}

@Composable
private fun SupplyChips(state: AddEditPeriodUiState, viewModel: AddEditPeriodViewModel, modifier: Modifier = Modifier) {
    val drug = state.drug ?: return
    val anySupplyLabel = stringResource(R.string.period_supply_any)
    val options = buildList {
        add(ChipOption<Long?>(null, anySupplyLabel))
        state.stockBatches.forEach { batch ->
            add(ChipOption<Long?>(batch.id, stockRowSummaryText(batch, drug)))
        }
    }
    ChipSelector(
        options = options,
        selected = state.pinnedBatchId,
        onSelect = viewModel::onPinnedBatchChange,
        modifier = modifier,
    )
}

@Composable
private fun CycleSection(state: AddEditPeriodUiState, viewModel: AddEditPeriodViewModel) {
    ChipSelector(
        options = listOf(
            ChipOption(CycleType.DAILY, stringResource(R.string.cycle_daily)),
            ChipOption(CycleType.EVERY_OTHER_DAY, stringResource(R.string.cycle_every_other_day)),
            ChipOption(CycleType.SPECIFIC_DAYS, stringResource(R.string.cycle_specific_days)),
            ChipOption(CycleType.DAYS_ON_OFF, stringResource(R.string.cycle_days_on_off)),
            ChipOption(CycleType.CUSTOM, stringResource(R.string.cycle_custom)),
        ),
        selected = state.cycleType,
        onSelect = viewModel::onCycleTypeChange,
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.cycleType == CycleType.SPECIFIC_DAYS) {
        val locale = Locale.getDefault()
        Row(modifier = Modifier.padding(top = 12.dp)) {
            DayOfWeek.entries.forEach { day ->
                val selected = day in state.specificDays
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onToggleSpecificDay(day) },
                    label = { Text(day.getDisplayName(TextStyle.NARROW, locale)) },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
        if (state.specificDaysError) {
            Text(
                text = stringResource(R.string.specific_days_error),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (state.cycleType == CycleType.DAYS_ON_OFF) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            OutlinedTextField(
                value = state.intakeDaysText,
                onValueChange = viewModel::onIntakeDaysChange,
                label = { Text(stringResource(R.string.intake_days_label)) },
                isError = state.daysOnOffError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(modifier = Modifier.size(8.dp))
            OutlinedTextField(
                value = state.breakDaysText,
                onValueChange = viewModel::onBreakDaysChange,
                label = { Text(stringResource(R.string.break_days_label)) },
                isError = state.daysOnOffError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        if (state.daysOnOffError) {
            Text(
                text = stringResource(R.string.days_on_off_error),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (state.cycleType == CycleType.CUSTOM) {
        OutlinedTextField(
            value = state.customCycleText,
            onValueChange = viewModel::onCustomCycleTextChange,
            label = { Text(stringResource(R.string.cycle_custom_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true,
        )
    }
}
