@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeSource
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.relation.IntakeLogWithDrug
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus
import app.zelgray.pills_in_time.ui.common.ChipOption
import app.zelgray.pills_in_time.ui.common.ChipSelector
import app.zelgray.pills_in_time.ui.common.ConfirmDialog
import app.zelgray.pills_in_time.ui.common.StatusPill
import app.zelgray.pills_in_time.ui.common.dayLabel
import app.zelgray.pills_in_time.ui.common.pluralUnitText
import app.zelgray.pills_in_time.util.formatPlainNumber
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    onAddEntryClick: () -> Unit,
    onEditEntryClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var logPendingDelete by remember { mutableStateOf<IntakeLog?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_history)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntryClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_entry_title))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ChipSelector(
                options = listOf(ChipOption<Long?>(null, stringResource(R.string.history_filter_all))) +
                    state.drugs.map { ChipOption<Long?>(it.id, it.name) },
                selected = state.selectedDrugId,
                onSelect = viewModel::onSelectDrugFilter,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.dayGroups.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    state.dayGroups.forEach { group ->
                        item(key = "header_${group.date}") {
                            Text(
                                text = dayLabel(group.date, state.today),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(group.entries, key = { it.intakeLog.id }) { entry ->
                            HistoryRow(
                                entry = entry,
                                onEdit = { onEditEntryClick(entry.intakeLog.id) },
                                onDelete = { logPendingDelete = entry.intakeLog },
                            )
                        }
                    }
                }
            }
        }
    }

    logPendingDelete?.let { log ->
        ConfirmDialog(
            title = stringResource(R.string.delete_entry_title),
            body = stringResource(R.string.delete_entry_body),
            onConfirm = {
                viewModel.deleteLog(log)
                logPendingDelete = null
            },
            onDismiss = { logPendingDelete = null },
        )
    }
}

@Composable
private fun HistoryRow(entry: IntakeLogWithDrug, onEdit: () -> Unit, onDelete: () -> Unit) {
    val log = entry.intakeLog
    val time = log.actualDateTime.atZone(ZoneId.systemDefault()).toLocalTime()

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = time.format(DateTimeFormatter.ofPattern("HH:mm")) + "  " + entry.drug.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = doseText(log, entry) + "  ·  " + sourceLabel(log.source),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                if (log.status == IntakeStatus.TAKEN) OccurrenceStatus.TAKEN else OccurrenceStatus.SKIPPED,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
private fun sourceLabel(source: IntakeSource): String = when (source) {
    IntakeSource.REMINDER -> stringResource(R.string.source_reminder)
    IntakeSource.MANUAL -> stringResource(R.string.source_manual)
}

@Composable
private fun doseText(log: IntakeLog, entry: IntakeLogWithDrug): String {
    val value = log.actualDoseValue
    return if (log.actualDoseMode == DoseMode.UNITS) {
        pluralUnitText(entry.drug.form, entry.drug.customFormText, value)
    } else {
        formatPlainNumber(value)
    }
}
