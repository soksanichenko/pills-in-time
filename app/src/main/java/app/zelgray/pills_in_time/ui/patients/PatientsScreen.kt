@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.Patient
import app.zelgray.pills_in_time.domain.model.PatientColorPalette
import app.zelgray.pills_in_time.ui.common.ColorSwatchPicker
import app.zelgray.pills_in_time.ui.common.ConfirmDialog

@Composable
fun PatientsScreen(
    onBack: () -> Unit,
    viewModel: PatientsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingPatient by remember { mutableStateOf<Patient?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogDefaultColor by remember { mutableStateOf(PatientColorPalette.colorForIndex(0)) }
    var deletingPatient by remember { mutableStateOf<Patient?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.patients_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                addDialogDefaultColor = PatientColorPalette.colorForIndex(state.patients.size)
                showAddDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_patient_title))
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 8.dp,
            ),
        ) {
            items(state.patients, key = { it.id }) { patient ->
                PatientRow(
                    patient = patient,
                    isCurrent = patient.id == state.currentPatientId,
                    canDelete = state.canDelete,
                    onSelect = { viewModel.onSelectPatient(patient.id) },
                    onEdit = { editingPatient = patient },
                    onDelete = { deletingPatient = patient },
                )
            }
        }
    }

    if (showAddDialog) {
        PatientEditDialog(
            title = stringResource(R.string.add_patient_title),
            initialName = "",
            initialColor = addDialogDefaultColor,
            onSave = { name, color ->
                viewModel.addPatient(name, color)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editingPatient?.let { patient ->
        PatientEditDialog(
            title = stringResource(R.string.edit_patient_title),
            initialName = patient.name,
            initialColor = patient.color,
            onSave = { name, color ->
                viewModel.updatePatient(patient, name, color)
                editingPatient = null
            },
            onDismiss = { editingPatient = null },
        )
    }

    deletingPatient?.let { patient ->
        ConfirmDialog(
            title = stringResource(R.string.delete_patient_title),
            body = stringResource(R.string.delete_patient_body),
            onConfirm = {
                viewModel.deletePatient(patient)
                deletingPatient = null
            },
            onDismiss = { deletingPatient = null },
        )
    }
}

@Composable
private fun PatientRow(
    patient: Patient,
    isCurrent: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(28.dp).background(Color(patient.color), CircleShape))
            Box(modifier = Modifier.padding(start = 12.dp).fillMaxWidth().weight(1f)) {
                Text(text = patient.name, style = MaterialTheme.typography.bodyLarge)
            }
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        }
    }
}

@Composable
private fun PatientEditDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    label = { Text(stringResource(R.string.patient_name_label)) },
                    placeholder = { Text(stringResource(R.string.patient_name_placeholder)) },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.patient_name_error)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.patient_color_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                ColorSwatchPicker(selectedColor = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    showError = true
                } else {
                    onSave(name.trim(), color)
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
