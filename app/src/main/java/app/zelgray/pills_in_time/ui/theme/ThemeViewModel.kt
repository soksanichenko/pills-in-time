@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.zelgray.pills_in_time.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    patientRepository: PatientRepository,
) : ViewModel() {

    // Null only until the current patient's row first loads — MedTrackerTheme
    // falls back to the palette default for that brief window.
    val currentPatientColor: StateFlow<Int?> = patientRepository.observeCurrentPatientId()
        .flatMapLatest { patientRepository.observeById(it) }
        .map { it?.color }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
