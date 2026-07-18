@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.zelgray.pills_in_time.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.local.relation.IntakeLogWithDrug
import app.zelgray.pills_in_time.util.NowProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HistoryDayGroup(val date: LocalDate, val entries: List<IntakeLogWithDrug>)

data class HistoryUiState(
    val drugs: List<Drug> = emptyList(),
    val selectedDrugId: Long? = null,
    val dayGroups: List<HistoryDayGroup> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val drugRepository: DrugRepository,
    private val intakeRepository: IntakeRepository,
    private val nowProvider: NowProvider,
) : ViewModel() {

    private val selectedDrugId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        drugRepository.observeAllDrugs(),
        selectedDrugId,
    ) { drugs, selected -> drugs to selected }
        .flatMapLatest { (drugs, selected) ->
            intakeRepository.observeAllLogs(selected).map { logs ->
                val groups = logs
                    .groupBy { it.intakeLog.occurrenceDate }
                    .map { (date, entries) -> HistoryDayGroup(date, entries) }
                HistoryUiState(
                    drugs = drugs,
                    selectedDrugId = selected,
                    dayGroups = groups,
                    today = nowProvider.currentLocalDate(),
                    isLoading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(isLoading = true))

    fun onSelectDrugFilter(drugId: Long?) {
        selectedDrugId.value = drugId
    }

    fun deleteLog(log: IntakeLog) {
        viewModelScope.launch { intakeRepository.deleteLog(log) }
    }
}
