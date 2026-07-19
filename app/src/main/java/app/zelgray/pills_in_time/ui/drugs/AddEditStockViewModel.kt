package app.zelgray.pills_in_time.ui.drugs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.ui.navigation.NavRoutes
import app.zelgray.pills_in_time.util.ValidationUtils
import app.zelgray.pills_in_time.util.formatPlainNumber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditStockUiState(
    val drugId: Long = 0,
    val stockId: Long? = null,
    val quantity: String = "",
    val strengthValue: String = "",
    val strengthUnit: StrengthUnit = StrengthUnit.MG,
    val lowStockReminderDaysBefore: String = "",
    val quantityError: Boolean = false,
    val strengthError: Boolean = false,
    val lowStockReminderError: Boolean = false,
    // Strength is optional — but a drug with a strength-less batch can only
    // have a single supply, since strength is what would otherwise justify
    // more than one. This fires when that rule would be violated.
    val requiresStrengthError: Boolean = false,
) {
    val isEditing: Boolean get() = stockId != null
}

@HiltViewModel
class AddEditStockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stockRepository: StockRepository,
) : ViewModel() {

    private val drugId: Long = checkNotNull(savedStateHandle[NavRoutes.ARG_DRUG_ID])
    private val editingStockId: Long? = savedStateHandle[NavRoutes.ARG_STOCK_ID]

    private val _uiState = MutableStateFlow(AddEditStockUiState(drugId = drugId, stockId = editingStockId))
    val uiState: StateFlow<AddEditStockUiState> = _uiState.asStateFlow()

    init {
        editingStockId?.let { id ->
            viewModelScope.launch {
                val batch = stockRepository.getById(id)
                if (batch != null) {
                    _uiState.update {
                        it.copy(
                            quantity = formatPlainNumber(batch.quantity),
                            strengthValue = batch.strengthValue?.let(::formatPlainNumber).orEmpty(),
                            strengthUnit = batch.strengthUnit ?: StrengthUnit.MG,
                            lowStockReminderDaysBefore = batch.lowStockReminderDaysBefore?.toString().orEmpty(),
                        )
                    }
                }
            }
        }
    }

    fun onQuantityChange(value: String) {
        _uiState.update { it.copy(quantity = value, quantityError = false) }
    }

    fun onStrengthValueChange(value: String) {
        _uiState.update { it.copy(strengthValue = value, strengthError = false, requiresStrengthError = false) }
    }

    fun onStrengthUnitChange(value: StrengthUnit) {
        _uiState.update { it.copy(strengthUnit = value) }
    }

    fun onLowStockReminderDaysBeforeChange(value: String) {
        _uiState.update { it.copy(lowStockReminderDaysBefore = value, lowStockReminderError = false) }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val quantity = ValidationUtils.parsePositiveDouble(state.quantity)
        val strengthText = state.strengthValue.trim()
        // Strength is optional: a blank value means this batch doesn't track it.
        val strength = strengthText.takeIf { it.isNotEmpty() }?.let { ValidationUtils.parsePositiveDouble(it) }
        val strengthInvalid = strengthText.isNotEmpty() && strength == null
        val reminderText = state.lowStockReminderDaysBefore.trim()
        val reminderDaysBefore = if (reminderText.isEmpty()) null else reminderText.toIntOrNull()?.takeIf { it > 0 }
        val reminderInvalid = reminderText.isNotEmpty() && reminderDaysBefore == null

        if (quantity == null || strengthInvalid || reminderInvalid) {
            _uiState.update {
                it.copy(
                    quantityError = quantity == null,
                    strengthError = strengthInvalid,
                    lowStockReminderError = reminderInvalid,
                )
            }
            return
        }

        viewModelScope.launch {
            // A strength-less batch can only be this drug's one and only supply.
            val otherBatches = stockRepository.getBatchesForDrugOnce(drugId).filter { it.id != state.stockId }
            if (otherBatches.isNotEmpty() && (strength == null || otherBatches.any { it.strengthValue == null })) {
                _uiState.update { it.copy(requiresStrengthError = true) }
                return@launch
            }

            if (state.stockId != null) {
                val existing = stockRepository.getById(state.stockId)
                if (existing != null) {
                    stockRepository.updateBatch(
                        existing.copy(
                            quantity = quantity,
                            strengthValue = strength,
                            strengthUnit = if (strength != null) state.strengthUnit else null,
                            lowStockReminderDaysBefore = reminderDaysBefore,
                        ),
                    )
                }
            } else {
                stockRepository.createBatch(
                    drugId,
                    quantity,
                    strength,
                    if (strength != null) state.strengthUnit else null,
                    reminderDaysBefore,
                )
            }
            onSaved()
        }
    }
}
