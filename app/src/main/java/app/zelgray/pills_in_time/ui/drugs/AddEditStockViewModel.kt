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
                            strengthValue = formatPlainNumber(batch.strengthValue),
                            strengthUnit = batch.strengthUnit,
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
        _uiState.update { it.copy(strengthValue = value, strengthError = false) }
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
        val strength = ValidationUtils.parsePositiveDouble(state.strengthValue)
        val reminderText = state.lowStockReminderDaysBefore.trim()
        val reminderDaysBefore = if (reminderText.isEmpty()) null else reminderText.toIntOrNull()?.takeIf { it > 0 }
        val reminderInvalid = reminderText.isNotEmpty() && reminderDaysBefore == null

        if (quantity == null || strength == null || reminderInvalid) {
            _uiState.update {
                it.copy(
                    quantityError = quantity == null,
                    strengthError = strength == null,
                    lowStockReminderError = reminderInvalid,
                )
            }
            return
        }
        viewModelScope.launch {
            if (state.stockId != null) {
                val existing = stockRepository.getById(state.stockId)
                if (existing != null) {
                    stockRepository.updateBatch(
                        existing.copy(
                            quantity = quantity,
                            strengthValue = strength,
                            strengthUnit = state.strengthUnit,
                            lowStockReminderDaysBefore = reminderDaysBefore,
                        ),
                    )
                }
            } else {
                stockRepository.createBatch(drugId, quantity, strength, state.strengthUnit, reminderDaysBefore)
            }
            onSaved()
        }
    }
}
