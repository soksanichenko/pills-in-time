@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.ui.common.ChipOption
import app.zelgray.pills_in_time.ui.common.ChipSelector
import app.zelgray.pills_in_time.ui.common.DatePickerField
import app.zelgray.pills_in_time.ui.common.DropdownSelectorField
import app.zelgray.pills_in_time.ui.common.TimePickerField
import app.zelgray.pills_in_time.ui.common.localizedDate
import app.zelgray.pills_in_time.ui.drugs.periodCycleLabel
import app.zelgray.pills_in_time.ui.drugs.periodDateRangeLabel
import java.time.format.DateTimeFormatter

@Composable
fun AddEditHistoryEntryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddEditHistoryEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (state.isEditing) R.string.edit_entry_title else R.string.add_entry_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            if (state.isEditing) {
                Text(text = state.editReadOnlyDrugName, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = state.editReadOnlyTimeOfDay?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty() +
                        "  ·  " + localizedDate(state.occurrenceDate),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            } else {
                DropdownSelectorField(
                    label = stringResource(R.string.entry_drug_label),
                    options = state.drugs.map { it.id to it.name },
                    selected = state.selectedDrugId,
                    onSelect = viewModel::onDrugSelected,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )

                DropdownSelectorField(
                    label = stringResource(R.string.entry_period_label),
                    options = state.periods.map { p -> p.scheduledIntake.id to (periodDateRangeLabel(p.scheduledIntake) + " · " + periodCycleLabel(p.scheduledIntake)) },
                    selected = state.selectedScheduleId,
                    onSelect = viewModel::onPeriodSelected,
                    enabled = state.selectedDrugId != null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )

                DropdownSelectorField(
                    label = stringResource(R.string.entry_time_label),
                    options = state.selectedPeriod?.times.orEmpty()
                        .sortedBy { it.timeOfDay }
                        .map { t -> t.id to t.timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm")) },
                    selected = state.selectedTimeId,
                    onSelect = viewModel::onTimeSelected,
                    enabled = state.selectedScheduleId != null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )

                DatePickerField(
                    label = stringResource(R.string.entry_occurrence_date_label),
                    date = state.occurrenceDate,
                    onDateChange = viewModel::onOccurrenceDateChange,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )

                if (state.selectionError) {
                    Text(
                        text = stringResource(R.string.entry_selection_error),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.entry_actual_section_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )

            DatePickerField(
                label = stringResource(R.string.entry_actual_date_label),
                date = state.actualDate,
                onDateChange = viewModel::onActualDateChange,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )

            TimePickerField(
                label = stringResource(R.string.entry_actual_time_label),
                time = state.actualTime,
                onTimeChange = viewModel::onActualTimeChange,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = state.doseValueText,
                onValueChange = viewModel::onDoseValueChange,
                label = { Text(stringResource(R.string.dose_value_label)) },
                isError = state.doseError,
                supportingText = {
                    if (state.doseError) Text(stringResource(R.string.strength_error))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                text = stringResource(R.string.entry_status_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            ChipSelector(
                options = listOf(
                    ChipOption(IntakeStatus.TAKEN, stringResource(R.string.status_taken)),
                    ChipOption(IntakeStatus.SKIPPED, stringResource(R.string.status_skipped)),
                ),
                selected = state.status,
                onSelect = viewModel::onStatusChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
