package app.zelgray.pills_in_time.ui.drugs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.data.repository.DrugRepository
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

/** Which of the two mutually exclusive low-stock reminder thresholds is configured. */
enum class LowStockReminderMode { DAYS_BEFORE, UNITS_BEFORE }

data class AddEditStockUiState(
    val drugId: Long = 0,
    val stockId: Long? = null,
    val drugForm: DrugForm = DrugForm.OTHER,
    val quantity: String = "",
    val strengthValue: String = "",
    val strengthUnit: StrengthUnit = StrengthUnit.MG,
    val lowStockReminderMode: LowStockReminderMode = LowStockReminderMode.DAYS_BEFORE,
    val lowStockReminderValue: String = "",
    // Drop-size calibration (DrugForm.DROPS only) — an alternate way to fill
    // in `quantity`, from a bottle's volume and this dropper's drops/mL,
    // instead of typing a raw drop count. See DrugStockBatch.dropsPerMl.
    val useDropsCalibration: Boolean = false,
    val bottleVolumeMl: String = "",
    val dropsPerMl: String = "",
    val quantityError: Boolean = false,
    val strengthError: Boolean = false,
    val lowStockReminderError: Boolean = false,
    val bottleVolumeError: Boolean = false,
    val dropsPerMlError: Boolean = false,
    // Strength is optional — but a drug with a strength-less batch can only
    // have a single supply, since strength is what would otherwise justify
    // more than one. This fires when that rule would be violated.
    val requiresStrengthError: Boolean = false,
) {
    val isEditing: Boolean get() = stockId != null
    val showDropsCalibration: Boolean get() = drugForm == DrugForm.DROPS
}

@HiltViewModel
class AddEditStockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stockRepository: StockRepository,
    private val drugRepository: DrugRepository,
) : ViewModel() {

    private val drugId: Long = checkNotNull(savedStateHandle[NavRoutes.ARG_DRUG_ID])
    private val editingStockId: Long? = savedStateHandle[NavRoutes.ARG_STOCK_ID]

    private val _uiState = MutableStateFlow(AddEditStockUiState(drugId = drugId, stockId = editingStockId))
    val uiState: StateFlow<AddEditStockUiState> = _uiState.asStateFlow()

    // Snapshot to diff against for the unsaved-changes prompt on exit —
    // taken once loading (if any) settles, so it reflects what was actually
    // loaded rather than the transient pre-load defaults.
    private var initialSnapshot = _uiState.value

    init {
        viewModelScope.launch {
            val form = drugRepository.getById(drugId)?.form ?: DrugForm.OTHER
            _uiState.update { it.copy(drugForm = form) }

            val batch = editingStockId?.let { stockRepository.getById(it) }
            if (batch != null) {
                val (mode, value) = when {
                    batch.lowStockReminderUnitsBefore != null ->
                        LowStockReminderMode.UNITS_BEFORE to formatPlainNumber(batch.lowStockReminderUnitsBefore)
                    batch.lowStockReminderDaysBefore != null ->
                        LowStockReminderMode.DAYS_BEFORE to batch.lowStockReminderDaysBefore.toString()
                    else -> LowStockReminderMode.DAYS_BEFORE to ""
                }
                _uiState.update {
                    it.copy(
                        quantity = formatPlainNumber(batch.quantity),
                        strengthValue = batch.strengthValue?.let(::formatPlainNumber).orEmpty(),
                        strengthUnit = batch.strengthUnit ?: StrengthUnit.MG,
                        lowStockReminderMode = mode,
                        lowStockReminderValue = value,
                        useDropsCalibration = batch.dropsPerMl != null,
                        dropsPerMl = batch.dropsPerMl?.let(::formatPlainNumber).orEmpty(),
                        bottleVolumeMl = batch.dropsPerMl?.let { formatPlainNumber(batch.quantity / it) }.orEmpty(),
                    )
                }
            } else if (form == DrugForm.DROPS) {
                // New batch of a drops drug: prefill the calibration from the
                // most recently added batch, so re-entering it per bottle
                // isn't needed when the same dropper is reused.
                stockRepository.getMostRecentBatch(drugId)?.dropsPerMl?.let { previousDropsPerMl ->
                    _uiState.update { it.copy(dropsPerMl = formatPlainNumber(previousDropsPerMl)) }
                }
            }
            initialSnapshot = _uiState.value
        }
    }

    /** Whether the form differs from what was last loaded/saved — drives the unsaved-changes exit prompt. */
    fun isDirty(): Boolean {
        val current = _uiState.value
        return current.copy(
            quantityError = initialSnapshot.quantityError,
            strengthError = initialSnapshot.strengthError,
            lowStockReminderError = initialSnapshot.lowStockReminderError,
            requiresStrengthError = initialSnapshot.requiresStrengthError,
            bottleVolumeError = initialSnapshot.bottleVolumeError,
            dropsPerMlError = initialSnapshot.dropsPerMlError,
        ) != initialSnapshot
    }

    fun onQuantityChange(value: String) {
        _uiState.update { it.copy(quantity = value, quantityError = false) }
    }

    fun onUseDropsCalibrationChange(useCalibration: Boolean) {
        _uiState.update { it.copy(useDropsCalibration = useCalibration, bottleVolumeError = false, dropsPerMlError = false) }
    }

    fun onBottleVolumeChange(value: String) {
        _uiState.update { it.copy(bottleVolumeMl = value, bottleVolumeError = false) }
        recomputeQuantityFromCalibration()
    }

    fun onDropsPerMlChange(value: String) {
        _uiState.update { it.copy(dropsPerMl = value, dropsPerMlError = false) }
        recomputeQuantityFromCalibration()
    }

    private fun recomputeQuantityFromCalibration() {
        val state = _uiState.value
        val volume = ValidationUtils.parsePositiveDouble(state.bottleVolumeMl)
        val dropsPerMl = ValidationUtils.parsePositiveDouble(state.dropsPerMl)
        if (volume != null && dropsPerMl != null) {
            _uiState.update { it.copy(quantity = formatPlainNumber(volume * dropsPerMl), quantityError = false) }
        }
    }

    fun onStrengthValueChange(value: String) {
        _uiState.update { it.copy(strengthValue = value, strengthError = false, requiresStrengthError = false) }
    }

    fun onStrengthUnitChange(value: StrengthUnit) {
        _uiState.update { it.copy(strengthUnit = value) }
    }

    fun onLowStockReminderModeChange(mode: LowStockReminderMode) {
        _uiState.update { it.copy(lowStockReminderMode = mode, lowStockReminderValue = "", lowStockReminderError = false) }
    }

    fun onLowStockReminderValueChange(value: String) {
        _uiState.update { it.copy(lowStockReminderValue = value, lowStockReminderError = false) }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val strengthText = state.strengthValue.trim()
        // Strength is optional: a blank value means this batch doesn't track it.
        val strength = strengthText.takeIf { it.isNotEmpty() }?.let { ValidationUtils.parsePositiveDouble(it) }
        val strengthInvalid = strengthText.isNotEmpty() && strength == null
        val reminderText = state.lowStockReminderValue.trim()
        val reminderDaysBefore = if (reminderText.isEmpty() || state.lowStockReminderMode != LowStockReminderMode.DAYS_BEFORE) {
            null
        } else {
            reminderText.toIntOrNull()?.takeIf { it > 0 }
        }
        val reminderUnitsBefore = if (reminderText.isEmpty() || state.lowStockReminderMode != LowStockReminderMode.UNITS_BEFORE) {
            null
        } else {
            ValidationUtils.parsePositiveDouble(reminderText)
        }
        val reminderInvalid = reminderText.isNotEmpty() && reminderDaysBefore == null && reminderUnitsBefore == null

        val useCalibration = state.showDropsCalibration && state.useDropsCalibration
        val bottleVolume = if (useCalibration) ValidationUtils.parsePositiveDouble(state.bottleVolumeMl) else null
        val dropsPerMl = if (useCalibration) ValidationUtils.parsePositiveDouble(state.dropsPerMl) else null
        val bottleVolumeInvalid = useCalibration && bottleVolume == null
        val dropsPerMlInvalid = useCalibration && dropsPerMl == null
        val quantity = if (useCalibration) {
            if (bottleVolume != null && dropsPerMl != null) bottleVolume * dropsPerMl else null
        } else {
            ValidationUtils.parsePositiveDouble(state.quantity)
        }

        if (quantity == null || strengthInvalid || reminderInvalid || bottleVolumeInvalid || dropsPerMlInvalid) {
            _uiState.update {
                it.copy(
                    quantityError = quantity == null && !useCalibration,
                    strengthError = strengthInvalid,
                    lowStockReminderError = reminderInvalid,
                    bottleVolumeError = bottleVolumeInvalid,
                    dropsPerMlError = dropsPerMlInvalid,
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
                            lowStockReminderUnitsBefore = reminderUnitsBefore,
                            lowStockReminderFiredForRunOutDate = if (reminderDaysBefore != null) existing.lowStockReminderFiredForRunOutDate else null,
                            lowStockReminderUnitsAlreadyFired = if (reminderUnitsBefore != null) existing.lowStockReminderUnitsAlreadyFired else false,
                            dropsPerMl = dropsPerMl,
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
                    reminderUnitsBefore,
                    dropsPerMl,
                )
            }
            onSaved()
        }
    }
}
