@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.drugs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.ui.common.ChipOption
import app.zelgray.pills_in_time.ui.common.ChipSelector

@Composable
fun AddEditDrugScreen(
    drugId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: AddEditDrugViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.edit_drug_title else R.string.add_drug_title,
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
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.drug_name_label)) },
                placeholder = { Text(stringResource(R.string.drug_name_placeholder)) },
                isError = state.nameError,
                supportingText = {
                    if (state.nameError) Text(stringResource(R.string.drug_name_error))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                text = stringResource(R.string.drug_form_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            ChipSelector(
                options = listOf(
                    ChipOption(DrugForm.TABLET, stringResource(R.string.drug_form_tablet)),
                    ChipOption(DrugForm.CAPSULE, stringResource(R.string.drug_form_capsule)),
                    ChipOption(DrugForm.DROPS, stringResource(R.string.drug_form_drops)),
                    ChipOption(DrugForm.ML, stringResource(R.string.drug_form_ml)),
                    ChipOption(DrugForm.AMPOULE, stringResource(R.string.drug_form_ampoule)),
                    ChipOption(DrugForm.SACHET, stringResource(R.string.drug_form_sachet)),
                    ChipOption(DrugForm.OTHER, stringResource(R.string.drug_form_other)),
                ),
                selected = state.form,
                onSelect = viewModel::onFormChange,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.form == DrugForm.OTHER) {
                OutlinedTextField(
                    value = state.customFormText,
                    onValueChange = viewModel::onCustomFormTextChange,
                    label = { Text(stringResource(R.string.drug_form_other_label)) },
                    placeholder = { Text(stringResource(R.string.drug_form_other_placeholder)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                )
            }
        }
    }
}
