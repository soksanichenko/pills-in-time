@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.ui.patients.PatientsViewModel

/**
 * Single top-bar action (right-aligned, same row as the screen title) showing
 * which patient is currently selected and letting the user switch — merged
 * into one tappable chip rather than a separate bar, so it never sits at the
 * status-bar/clock level where it isn't reachable.
 */
@Composable
fun PatientSwitcherAction(
    onManagePatients: () -> Unit,
    viewModel: PatientsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val current = state.patients.find { it.id == state.currentPatientId }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (current != null) {
                Box(modifier = Modifier.size(14.dp).background(Color(current.color), CircleShape))
                Text(
                    text = current.name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp, end = 2.dp),
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.manage_patients_action))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.patients.forEach { patient ->
                DropdownMenuItem(
                    text = { Text(patient.name) },
                    leadingIcon = { Box(modifier = Modifier.size(16.dp).background(Color(patient.color), CircleShape)) },
                    trailingIcon = {
                        if (patient.id == state.currentPatientId) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        viewModel.onSelectPatient(patient.id)
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_patients_action)) },
                onClick = {
                    expanded = false
                    onManagePatients()
                },
            )
        }
    }
}
