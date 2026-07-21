package app.zelgray.pills_in_time.ui.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.local.entity.Patient
import app.zelgray.pills_in_time.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientsUiState(
    val patients: List<Patient> = emptyList(),
    val currentPatientId: Long = 0,
) {
    val canDelete: Boolean get() = patients.size > 1
}

@HiltViewModel
class PatientsViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
) : ViewModel() {

    val uiState: StateFlow<PatientsUiState> = combine(
        patientRepository.observeAll(),
        patientRepository.observeCurrentPatientId(),
    ) { patients, currentId -> PatientsUiState(patients, currentId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientsUiState())

    fun onSelectPatient(patientId: Long) {
        viewModelScope.launch { patientRepository.setCurrentPatientId(patientId) }
    }

    suspend fun nextDefaultColor(): Int = patientRepository.nextDefaultColor()

    fun addPatient(name: String, color: Int) {
        viewModelScope.launch { patientRepository.createPatient(name, color) }
    }

    fun updatePatient(patient: Patient, name: String, color: Int) {
        viewModelScope.launch { patientRepository.updatePatient(patient.copy(name = name, color = color)) }
    }

    fun deletePatient(patient: Patient) {
        viewModelScope.launch { patientRepository.deletePatient(patient) }
    }
}
