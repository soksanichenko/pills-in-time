package app.zelgray.pills_in_time.ui.drugs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.ui.navigation.NavRoutes
import app.zelgray.pills_in_time.util.ValidationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditDrugUiState(
    val drugId: Long? = null,
    val name: String = "",
    val form: DrugForm = DrugForm.TABLET,
    val customFormText: String = "",
    val nameError: Boolean = false,
    val isLoading: Boolean = false,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = drugId != null
}

@HiltViewModel
class AddEditDrugViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val drugRepository: DrugRepository,
) : ViewModel() {

    private val editingDrugId: Long? = savedStateHandle[NavRoutes.ARG_DRUG_ID]

    private val _uiState = MutableStateFlow(AddEditDrugUiState(drugId = editingDrugId))
    val uiState: StateFlow<AddEditDrugUiState> = _uiState.asStateFlow()

    // Snapshot to diff against for the unsaved-changes prompt on exit —
    // taken once loading (if any) settles, so it reflects what was actually
    // loaded rather than the transient pre-load defaults.
    private var initialSnapshot = _uiState.value

    init {
        editingDrugId?.let { id ->
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val drug = drugRepository.getById(id)
                if (drug != null) {
                    _uiState.update {
                        it.copy(
                            name = drug.name,
                            form = drug.form,
                            customFormText = drug.customFormText.orEmpty(),
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                initialSnapshot = _uiState.value
            }
        }
    }

    /** Whether the form differs from what was last loaded/saved — drives the unsaved-changes exit prompt. */
    fun isDirty(): Boolean {
        val current = _uiState.value
        return current.copy(isLoading = initialSnapshot.isLoading, nameError = initialSnapshot.nameError) != initialSnapshot
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameError = false) }
    }

    fun onFormChange(value: DrugForm) {
        _uiState.update { it.copy(form = value) }
    }

    fun onCustomFormTextChange(value: String) {
        _uiState.update { it.copy(customFormText = value) }
    }

    fun save(onSaved: (Long) -> Unit) {
        val state = _uiState.value
        if (!ValidationUtils.isNonBlank(state.name)) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        val customText = state.customFormText.trim().takeIf { state.form == DrugForm.OTHER && it.isNotBlank() }
        viewModelScope.launch {
            val id = if (state.drugId != null) {
                val existing = drugRepository.getById(state.drugId) ?: return@launch
                drugRepository.updateDrug(
                    existing.copy(
                        name = state.name.trim(),
                        form = state.form,
                        customFormText = customText,
                    ),
                )
                state.drugId
            } else {
                drugRepository.createDrug(state.name.trim(), state.form, customText)
            }
            onSaved(id)
        }
    }
}
