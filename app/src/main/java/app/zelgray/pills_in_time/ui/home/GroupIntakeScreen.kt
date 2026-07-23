@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.model.Occurrence
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus
import app.zelgray.pills_in_time.domain.usecase.GenerateOccurrencesForDateUseCase
import app.zelgray.pills_in_time.domain.usecase.ResolveEffectiveStrengthUseCase
import app.zelgray.pills_in_time.notification.NotificationContracts
import app.zelgray.pills_in_time.ui.common.StatusPill
import app.zelgray.pills_in_time.ui.drugs.doseText
import app.zelgray.pills_in_time.ui.navigation.NavRoutes
import app.zelgray.pills_in_time.util.NowProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class GroupIntakeItem(
    val occurrence: Occurrence,
    val drug: Drug,
    val stockBatches: List<DrugStockBatch>,
    val effectiveStrength: EffectiveStrength?,
    val checked: Boolean,
)

data class GroupIntakeUiState(
    val timeOfDay: LocalTime,
    val items: List<GroupIntakeItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class GroupIntakeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val drugRepository: DrugRepository,
    private val stockRepository: StockRepository,
    private val intakeRepository: IntakeRepository,
    private val generateOccurrences: GenerateOccurrencesForDateUseCase,
    private val resolveEffectiveStrength: ResolveEffectiveStrengthUseCase,
    private val nowProvider: NowProvider,
) : ViewModel() {

    private val patientId: Long = checkNotNull(savedStateHandle[NavRoutes.ARG_PATIENT_ID])
    private val occurrenceDate: LocalDate = LocalDate.ofEpochDay(checkNotNull(savedStateHandle.get<Long>(NavRoutes.ARG_EPOCH_DAY)))
    private val timeOfDay: LocalTime = LocalTime.ofSecondOfDay(checkNotNull(savedStateHandle.get<Int>(NavRoutes.ARG_SECOND_OF_DAY)).toLong())

    private val _uiState = MutableStateFlow(GroupIntakeUiState(timeOfDay = timeOfDay))
    val uiState: StateFlow<GroupIntakeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val periods = scheduleRepository.getAllPeriodsForPatientOnce(patientId)
        val drugsById = drugRepository.getAllOnce(patientId).associateBy { it.id }
        val today = nowProvider.currentLocalDate()
        val now = nowProvider.currentLocalDateTime()
        val logs = intakeRepository.getLogsForDateOnce(occurrenceDate)
        val occurrences = generateOccurrences(periods, logs, occurrenceDate, today, now).filter { it.timeOfDay == timeOfDay }

        val items = occurrences.mapNotNull { occ ->
            val drug = drugsById[occ.drugId] ?: return@mapNotNull null
            val batches = stockRepository.getBatchesForDrugOnce(occ.drugId)
            GroupIntakeItem(
                occurrence = occ,
                drug = drug,
                stockBatches = batches,
                effectiveStrength = resolveEffectiveStrength(batches),
                checked = occ.status == OccurrenceStatus.UPCOMING || occ.status == OccurrenceStatus.OVERDUE,
            )
        }
        _uiState.value = GroupIntakeUiState(timeOfDay = timeOfDay, items = items, isLoading = false)
    }

    fun onToggle(item: GroupIntakeItem) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.occurrence.scheduledIntakeId == item.occurrence.scheduledIntakeId && it.occurrence.intakeTimeId == item.occurrence.intakeTimeId) {
                        it.copy(checked = !it.checked)
                    } else {
                        it
                    }
                },
            )
        }
    }

    fun onConfirm(onDone: () -> Unit) {
        viewModelScope.launch {
            val actionable = _uiState.value.items.filter {
                it.checked && (it.occurrence.status == OccurrenceStatus.UPCOMING || it.occurrence.status == OccurrenceStatus.OVERDUE)
            }
            actionable.forEach { item ->
                intakeRepository.recordQuickAction(
                    drugId = item.occurrence.drugId,
                    scheduledIntakeId = item.occurrence.scheduledIntakeId,
                    intakeTimeId = item.occurrence.intakeTimeId,
                    occurrenceDate = item.occurrence.occurrenceDate,
                    doseValue = item.occurrence.doseValue,
                    doseMode = item.occurrence.doseMode,
                    status = IntakeStatus.TAKEN,
                )
            }
            cancelGroupNotification()
            onDone()
        }
    }

    /** Mirrors HomeViewModel's cancelReminderNotification, but for the merged group notification. */
    private fun cancelGroupNotification() {
        val notificationId = NotificationContracts.computeGroupNotificationId(patientId, occurrenceDate, timeOfDay)
        NotificationManagerCompat.from(context).cancel(notificationId)
        WorkManager.getInstance(context).cancelUniqueWork(NotificationContracts.repeatWorkName(notificationId))
    }
}

@Composable
fun GroupIntakeScreen(
    viewModel: GroupIntakeViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_intake_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = stringResource(R.string.group_intake_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(state.items, key = { it.occurrence.scheduledIntakeId.toString() + "_" + it.occurrence.intakeTimeId }) { item ->
                    GroupIntakeRow(item = item, onToggle = { viewModel.onToggle(item) })
                }
            }
            Button(
                onClick = { viewModel.onConfirm(onDone) },
                enabled = state.items.any { it.checked && (it.occurrence.status == OccurrenceStatus.UPCOMING || it.occurrence.status == OccurrenceStatus.OVERDUE) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.action_took_it))
            }
        }
    }
}

@Composable
private fun GroupIntakeRow(item: GroupIntakeItem, onToggle: () -> Unit) {
    val actionable = item.occurrence.status == OccurrenceStatus.UPCOMING || item.occurrence.status == OccurrenceStatus.OVERDUE
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.checked, onCheckedChange = { onToggle() }, enabled = actionable)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = item.occurrence.timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm")) + "  " + item.drug.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = doseText(item.occurrence.doseValue, item.occurrence.doseMode, item.drug, item.stockBatches, item.effectiveStrength, item.occurrence.doseAllocation),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(item.occurrence.status)
        }
    }
}
