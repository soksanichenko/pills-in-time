@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.drugs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.model.PeriodStockProjection
import app.zelgray.pills_in_time.ui.common.ConfirmDialog
import app.zelgray.pills_in_time.ui.common.pluralUnitText
import app.zelgray.pills_in_time.util.ValidationUtils
import app.zelgray.pills_in_time.util.formatPlainNumber
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun DrugDetailScreen(
    drugId: Long,
    onBack: () -> Unit,
    onEditDrug: (Long) -> Unit,
    onAddStock: (Long) -> Unit,
    onEditStock: (Long, Long) -> Unit,
    onAddPeriod: (Long) -> Unit,
    onEditPeriod: (Long, Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: DrugDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var showDeleteDrugDialog by remember { mutableStateOf(false) }
    var deleteDrugHasDependents by remember { mutableStateOf(false) }
    var stockPendingDelete by remember { mutableStateOf<DrugStockBatch?>(null) }
    var periodPendingDelete by remember { mutableStateOf<ScheduledIntakeWithTimes?>(null) }
    var restockBatch by remember { mutableStateOf<DrugStockBatch?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.drug?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onEditDrug(drugId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            deleteDrugHasDependents = viewModel.hasSchedulesOrStock()
                            showDeleteDrugDialog = true
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = drugFormLabel(state.drug!!.form, state.drug!!.customFormText),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.supplies_section_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TextButton(onClick = { onAddStock(drugId) }) {
                            Text(stringResource(R.string.add_stock_action))
                        }
                    }
                }

                if (state.stockBatches.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.stock_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.stockBatches, key = { "stock_${it.id}" }) { batch ->
                        StockRow(
                            batch = batch,
                            drug = state.drug!!,
                            onRestock = { restockBatch = batch },
                            onEdit = { onEditStock(drugId, batch.id) },
                            onDelete = { stockPendingDelete = batch },
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.periods_section_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TextButton(onClick = { onAddPeriod(drugId) }) {
                            Text(stringResource(R.string.add_period_action))
                        }
                    }
                }

                if (state.periods.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.periods_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.stockProjection?.let { projection ->
                        item {
                            Text(
                                text = stockOverallProjectionText(projection.overall, state.drug!!),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    items(state.periods, key = { "period_${it.scheduledIntake.id}" }) { periodWithTimes ->
                        PeriodCard(
                            periodWithTimes = periodWithTimes,
                            drug = state.drug!!,
                            stockBatches = state.stockBatches,
                            effectiveStrength = state.effectiveStrength,
                            stockProjection = state.stockProjection?.periodProjections
                                ?.get(periodWithTimes.scheduledIntake.id),
                            onEdit = { onEditPeriod(drugId, periodWithTimes.scheduledIntake.id) },
                            onDelete = { periodPendingDelete = periodWithTimes },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDrugDialog) {
        ConfirmDialog(
            title = stringResource(R.string.delete_drug_title),
            body = if (deleteDrugHasDependents) {
                stringResource(R.string.delete_drug_body_cascade)
            } else {
                stringResource(R.string.delete_drug_body_plain)
            },
            onConfirm = {
                showDeleteDrugDialog = false
                viewModel.deleteDrug(onDeleted)
            },
            onDismiss = { showDeleteDrugDialog = false },
        )
    }

    stockPendingDelete?.let { batch ->
        ConfirmDialog(
            title = stringResource(R.string.delete_stock_title),
            body = stringResource(R.string.delete_stock_body),
            onConfirm = {
                viewModel.deleteStockBatch(batch)
                stockPendingDelete = null
            },
            onDismiss = { stockPendingDelete = null },
        )
    }

    periodPendingDelete?.let { period ->
        ConfirmDialog(
            title = stringResource(R.string.delete_period_title),
            body = stringResource(R.string.delete_period_body),
            onConfirm = {
                viewModel.deletePeriod(period)
                periodPendingDelete = null
            },
            onDismiss = { periodPendingDelete = null },
        )
    }

    restockBatch?.let { batch ->
        RestockDialog(
            batch = batch,
            drug = state.drug!!,
            onConfirm = { amount ->
                viewModel.restockBatch(batch, amount)
                restockBatch = null
            },
            onDismiss = { restockBatch = null },
        )
    }
}

@Composable
private fun RestockDialog(batch: DrugStockBatch, drug: Drug, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restock_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.stock_row_summary,
                        pluralUnitText(drug.form, drug.customFormText, batch.quantity),
                        formatPlainNumber(batch.strengthValue),
                        batch.strengthUnit.name.lowercase(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    label = { Text(stringResource(R.string.restock_quantity_label)) },
                    isError = error,
                    supportingText = {
                        if (error) Text(stringResource(R.string.restock_quantity_error))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = ValidationUtils.parsePositiveDouble(text)
                if (amount == null) {
                    error = true
                } else {
                    onConfirm(amount)
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PeriodCard(
    periodWithTimes: ScheduledIntakeWithTimes,
    drug: Drug,
    stockBatches: List<DrugStockBatch>,
    effectiveStrength: EffectiveStrength?,
    stockProjection: PeriodStockProjection?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val period = periodWithTimes.scheduledIntake
    val depleted = stockProjection?.stockDepleted == true
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = if (depleted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = periodDateRangeLabel(period), style = MaterialTheme.typography.bodyLarge)
                if (isPeriodActiveOn(period, LocalDate.now())) {
                    Text(
                        text = stringResource(R.string.period_active_now),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            PeriodTimesList(
                periodWithTimes = periodWithTimes,
                drug = drug,
                stockBatches = stockBatches,
                effectiveStrength = effectiveStrength,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = periodCycleLabel(period),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (stockProjection != null) {
                val stockTextColor = if (depleted) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = periodStockAtStartText(stockProjection, drug),
                    style = MaterialTheme.typography.labelLarge,
                    color = stockTextColor,
                    modifier = Modifier.padding(top = 4.dp),
                )
                periodStockAtEndText(stockProjection, drug)?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = stockTextColor,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        }
    }
}

@Composable
private fun StockRow(batch: DrugStockBatch, drug: Drug, onRestock: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.stock_row_summary,
                        pluralUnitText(drug.form, drug.customFormText, batch.quantity),
                        formatPlainNumber(batch.strengthValue),
                        batch.strengthUnit.name.lowercase(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            IconButton(onClick = onRestock) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.restock_action))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}
