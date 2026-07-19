@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.drugs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.ui.common.ChipOption
import app.zelgray.pills_in_time.ui.common.ChipSelector

@Composable
fun AddEditStockScreen(
    drugId: Long,
    stockId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddEditStockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.edit_stock_title else R.string.add_stock_title,
                        ),
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
            OutlinedTextField(
                value = state.quantity,
                onValueChange = viewModel::onQuantityChange,
                label = { Text(stringResource(R.string.quantity_label)) },
                placeholder = { Text(stringResource(R.string.quantity_placeholder)) },
                isError = state.quantityError,
                supportingText = {
                    if (state.quantityError) Text(stringResource(R.string.quantity_error))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                text = stringResource(R.string.strength_per_unit_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.strengthValue,
                    onValueChange = viewModel::onStrengthValueChange,
                    label = { Text(stringResource(R.string.strength_value_label)) },
                    placeholder = { Text(stringResource(R.string.strength_value_placeholder)) },
                    isError = state.strengthError,
                    supportingText = {
                        if (state.strengthError) Text(stringResource(R.string.strength_error))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            if (state.requiresStrengthError) {
                Text(
                    text = stringResource(R.string.batch_requires_strength_error),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            ChipSelector(
                options = listOf(
                    ChipOption(StrengthUnit.MG, stringResource(R.string.unit_mg)),
                    ChipOption(StrengthUnit.MCG, stringResource(R.string.unit_mcg)),
                    ChipOption(StrengthUnit.IU, stringResource(R.string.unit_iu)),
                ),
                selected = state.strengthUnit,
                onSelect = viewModel::onStrengthUnitChange,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            ChipSelector(
                options = listOf(
                    ChipOption(LowStockReminderMode.DAYS_BEFORE, stringResource(R.string.low_stock_reminder_mode_days)),
                    ChipOption(LowStockReminderMode.UNITS_BEFORE, stringResource(R.string.low_stock_reminder_mode_units)),
                ),
                selected = state.lowStockReminderMode,
                onSelect = viewModel::onLowStockReminderModeChange,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )

            val isUnitsMode = state.lowStockReminderMode == LowStockReminderMode.UNITS_BEFORE
            OutlinedTextField(
                value = state.lowStockReminderValue,
                onValueChange = viewModel::onLowStockReminderValueChange,
                label = {
                    Text(
                        stringResource(
                            if (isUnitsMode) R.string.low_stock_reminder_units_label else R.string.low_stock_reminder_label,
                        ),
                    )
                },
                placeholder = {
                    Text(
                        stringResource(
                            if (isUnitsMode) R.string.low_stock_reminder_units_placeholder else R.string.low_stock_reminder_placeholder,
                        ),
                    )
                },
                isError = state.lowStockReminderError,
                supportingText = {
                    if (state.lowStockReminderError) {
                        Text(
                            stringResource(
                                if (isUnitsMode) R.string.low_stock_reminder_units_error else R.string.low_stock_reminder_error,
                            ),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = if (isUnitsMode) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
            )
        }
    }
}
