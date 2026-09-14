@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.zelgray.pills_in_time.ui.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.PatientRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.util.NowProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class DrugActivityFilter { ALL, ACTIVE, INACTIVE }

data class DrugsListUiState(
    val drugs: List<Drug> = emptyList(),
    val selectedFilter: DrugActivityFilter = DrugActivityFilter.ALL,
)

@HiltViewModel
class DrugsListViewModel @Inject constructor(
    drugRepository: DrugRepository,
    patientRepository: PatientRepository,
    scheduleRepository: ScheduleRepository,
    private val nowProvider: NowProvider,
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(DrugActivityFilter.ALL)

    val uiState: StateFlow<DrugsListUiState> = patientRepository.observeCurrentPatientId()
        .flatMapLatest { patientId ->
            combine(
                drugRepository.observeAllDrugs(patientId),
                scheduleRepository.observeAllPeriods(patientId),
                selectedFilter,
            ) { drugs, periods, filter ->
                val today = nowProvider.currentLocalDate()
                val activeDrugIds = periods
                    .filter { val end = it.scheduledIntake.endDate; end == null || !end.isBefore(today) }
                    .mapTo(mutableSetOf()) { it.scheduledIntake.drugId }
                val filtered = when (filter) {
                    DrugActivityFilter.ALL -> drugs
                    DrugActivityFilter.ACTIVE -> drugs.filter { it.id in activeDrugIds }
                    DrugActivityFilter.INACTIVE -> drugs.filter { it.id !in activeDrugIds }
                }
                DrugsListUiState(drugs = filtered, selectedFilter = filter)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrugsListUiState())

    fun onSelectFilter(filter: DrugActivityFilter) {
        selectedFilter.value = filter
    }
}
