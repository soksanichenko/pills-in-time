package app.zelgray.pills_in_time.ui.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.repository.DrugRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DrugsListViewModel @Inject constructor(
    drugRepository: DrugRepository,
) : ViewModel() {

    val drugs: StateFlow<List<Drug>> = drugRepository.observeAllDrugs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
