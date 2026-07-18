package app.zelgray.pills_in_time.ui.drugs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.repository.IntakeTimeInput
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.usecase.FindDoseCombosUseCase
import app.zelgray.pills_in_time.domain.usecase.ResolveEffectiveStrengthUseCase
import app.zelgray.pills_in_time.ui.navigation.NavRoutes
import app.zelgray.pills_in_time.util.formatPlainNumber
import app.zelgray.pills_in_time.util.parseLocaleAwareDouble
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class StartMode { CUSTOM, CONTINUE }

data class TimeRowState(
    val rowKey: Long,
    val id: Long = 0,
    val timeOfDay: LocalTime,
    val doseMode: DoseMode = DoseMode.UNITS,
    val doseValueText: String = "1",
)

data class AddEditPeriodUiState(
    val scheduleId: Long? = null,
    val drugId: Long = 0,
    val isLoading: Boolean = true,
    val startMode: StartMode = StartMode.CUSTOM,
    val customStartDate: LocalDate = LocalDate.now(),
    val hasPreviousEndedPeriod: Boolean = false,
    val previousPeriodEndDate: LocalDate? = null,
    val endMode: EndMode = EndMode.DAYS,
    val endDate: LocalDate = LocalDate.now().plusDays(6),
    val durationDaysText: String = "7",
    val cycleType: CycleType = CycleType.DAILY,
    val specificDays: Set<DayOfWeek> = emptySet(),
    val customCycleText: String = "",
    val intakeDaysText: String = "5",
    val breakDaysText: String = "2",
    val times: List<TimeRowState> = listOf(TimeRowState(rowKey = 0, timeOfDay = LocalTime.of(8, 0))),
    val newTimeInput: LocalTime = LocalTime.of(8, 0),
    val effectiveStrength: EffectiveStrength? = null,
    val stockBatches: List<DrugStockBatch> = emptyList(),
    val timesError: Boolean = false,
    val durationDaysError: Boolean = false,
    val specificDaysError: Boolean = false,
    val daysOnOffError: Boolean = false,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = scheduleId != null

    val effectiveStartDate: LocalDate
        get() = if (startMode == StartMode.CONTINUE && previousPeriodEndDate != null) {
            previousPeriodEndDate.plusDays(1)
        } else {
            customStartDate
        }

    val durationDays: Int get() = durationDaysText.toIntOrNull()?.coerceAtLeast(1) ?: 1

    val computedEndDate: LocalDate?
        get() = when (endMode) {
            EndMode.DATE -> endDate
            EndMode.DAYS -> effectiveStartDate.plusDays((durationDays - 1).toLong())
            EndMode.NONE -> null
        }

    val strengthModeAvailable: Boolean get() = effectiveStrength != null
}

@HiltViewModel
class AddEditPeriodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scheduleRepository: ScheduleRepository,
    private val stockRepository: StockRepository,
    private val resolveEffectiveStrength: ResolveEffectiveStrengthUseCase,
    private val findDoseCombos: FindDoseCombosUseCase,
) : ViewModel() {

    private val drugId: Long = checkNotNull(savedStateHandle[NavRoutes.ARG_DRUG_ID])
    private val editingScheduleId: Long? = savedStateHandle[NavRoutes.ARG_SCHEDULE_ID]

    private val _uiState = MutableStateFlow(AddEditPeriodUiState(drugId = drugId, scheduleId = editingScheduleId))
    val uiState: StateFlow<AddEditPeriodUiState> = _uiState.asStateFlow()

    private var rowKeySeq = 1L

    init {
        viewModelScope.launch {
            val allBatches = stockRepository.getBatchesForDrugOnce(drugId)
            val effectiveStrength = resolveEffectiveStrength(allBatches)

            val latest = scheduleRepository.getLatestPeriodByStartDate(drugId, excludeId = editingScheduleId ?: -1)
            val hasPrev = latest?.endDate != null

            if (editingScheduleId != null) {
                val schedule = scheduleRepository.getById(editingScheduleId)
                val times = scheduleRepository.getTimesForSchedule(editingScheduleId)
                if (schedule != null) {
                    _uiState.update {
                        it.copy(
                            startMode = StartMode.CUSTOM,
                            customStartDate = schedule.startDate,
                            hasPreviousEndedPeriod = hasPrev,
                            previousPeriodEndDate = latest?.endDate,
                            endMode = schedule.endMode,
                            endDate = schedule.endDate ?: schedule.startDate.plusDays(6),
                            durationDaysText = (schedule.durationDays ?: 7).toString(),
                            cycleType = schedule.cycleType,
                            specificDays = schedule.specificDays.orEmpty(),
                            customCycleText = schedule.customCycleText.orEmpty(),
                            intakeDaysText = (schedule.intakeDays ?: 5).toString(),
                            breakDaysText = (schedule.breakDays ?: 2).toString(),
                            times = times.map { t ->
                                TimeRowState(
                                    rowKey = rowKeySeq++,
                                    id = t.id,
                                    timeOfDay = t.timeOfDay,
                                    doseMode = t.doseMode,
                                    doseValueText = formatPlainNumber(t.doseValue),
                                )
                            }.ifEmpty { listOf(TimeRowState(rowKey = rowKeySeq++, timeOfDay = LocalTime.of(8, 0))) },
                            effectiveStrength = effectiveStrength,
                            stockBatches = allBatches,
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } else {
                _uiState.update {
                    it.copy(
                        hasPreviousEndedPeriod = hasPrev,
                        previousPeriodEndDate = latest?.endDate,
                        times = listOf(TimeRowState(rowKey = rowKeySeq++, timeOfDay = LocalTime.of(8, 0))),
                        effectiveStrength = effectiveStrength,
                        stockBatches = allBatches,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onStartModeChange(mode: StartMode) = _uiState.update { it.copy(startMode = mode) }

    fun onCustomStartDateChange(date: LocalDate) = _uiState.update { it.copy(customStartDate = date) }

    fun onEndModeChange(mode: EndMode) = _uiState.update { it.copy(endMode = mode) }

    fun onEndDateChange(date: LocalDate) = _uiState.update { it.copy(endDate = date) }

    fun onDurationDaysChange(text: String) = _uiState.update { it.copy(durationDaysText = text, durationDaysError = false) }

    fun onCycleTypeChange(type: CycleType) =
        _uiState.update { it.copy(cycleType = type, specificDaysError = false, daysOnOffError = false) }

    fun onToggleSpecificDay(day: DayOfWeek) = _uiState.update {
        val current = it.specificDays
        it.copy(specificDays = if (day in current) current - day else current + day, specificDaysError = false)
    }

    fun onCustomCycleTextChange(text: String) = _uiState.update { it.copy(customCycleText = text) }

    fun onIntakeDaysChange(text: String) = _uiState.update { it.copy(intakeDaysText = text, daysOnOffError = false) }

    fun onBreakDaysChange(text: String) = _uiState.update { it.copy(breakDaysText = text, daysOnOffError = false) }

    fun onNewTimeInputChange(time: LocalTime) = _uiState.update { it.copy(newTimeInput = time) }

    fun onAddTimeRow() {
        val state = _uiState.value
        if (state.times.any { it.timeOfDay == state.newTimeInput }) return
        val newRow = TimeRowState(rowKey = rowKeySeq++, timeOfDay = state.newTimeInput)
        _uiState.update {
            it.copy(
                times = (it.times + newRow).sortedBy { row -> row.timeOfDay },
                timesError = false,
            )
        }
    }

    fun onRemoveTimeRow(rowKey: Long) {
        _uiState.update { it.copy(times = it.times.filterNot { row -> row.rowKey == rowKey }) }
    }

    fun onTimeOfDayChange(rowKey: Long, newTime: LocalTime) {
        _uiState.update { state ->
            // Dedup by exact time, same rule as adding a new row — silently
            // ignore if another row already uses that exact time.
            if (state.times.any { it.rowKey != rowKey && it.timeOfDay == newTime }) return@update state
            state.copy(
                times = state.times.map { if (it.rowKey == rowKey) it.copy(timeOfDay = newTime) else it }
                    .sortedBy { it.timeOfDay },
                timesError = false,
            )
        }
    }

    fun onTimeDoseModeChange(rowKey: Long, mode: DoseMode) {
        if (mode == DoseMode.STRENGTH && !_uiState.value.strengthModeAvailable) return
        _uiState.update { state ->
            state.copy(times = state.times.map { if (it.rowKey == rowKey) it.copy(doseMode = mode) else it })
        }
    }

    fun onTimeDoseValueChange(rowKey: Long, text: String) {
        _uiState.update { state ->
            state.copy(times = state.times.map { if (it.rowKey == rowKey) it.copy(doseValueText = text) else it })
        }
    }

    fun dosePreviewFor(row: TimeRowState): DosePreview? {
        val state = _uiState.value
        val doseValue = parseLocaleAwareDouble(row.doseValueText) ?: return null
        val strength = state.effectiveStrength ?: return if (row.doseMode == DoseMode.STRENGTH) null else null

        return when (row.doseMode) {
            DoseMode.STRENGTH -> {
                val combos = findDoseCombos(state.stockBatches, doseValue)
                if (combos.isNotEmpty()) {
                    DosePreview.ExactCombos(combos, strength.unit)
                } else {
                    val fallbackUnits = if (strength.value > 0) doseValue / strength.value else 0.0
                    DosePreview.NoExactMatch(formatPlainNumber(fallbackUnits), strength.unit)
                }
            }
            DoseMode.UNITS -> {
                val computedStrength = doseValue * strength.value
                DosePreview.UnitsToStrength(formatPlainNumber(computedStrength), strength.unit)
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val timesInvalid = state.times.isEmpty() ||
            state.times.any { (parseLocaleAwareDouble(it.doseValueText) ?: 0.0) <= 0 }
        val durationInvalid = state.endMode == EndMode.DAYS &&
            (state.durationDaysText.toIntOrNull() == null || state.durationDaysText.toIntOrNull()!! < 1)
        val specificDaysInvalid = state.cycleType == CycleType.SPECIFIC_DAYS && state.specificDays.isEmpty()
        val daysOnOffInvalid = state.cycleType == CycleType.DAYS_ON_OFF &&
            ((state.intakeDaysText.toIntOrNull() ?: 0) < 1 || (state.breakDaysText.toIntOrNull() ?: 0) < 1)

        if (timesInvalid || durationInvalid || specificDaysInvalid || daysOnOffInvalid) {
            _uiState.update {
                it.copy(
                    timesError = timesInvalid,
                    durationDaysError = durationInvalid,
                    specificDaysError = specificDaysInvalid,
                    daysOnOffError = daysOnOffInvalid,
                )
            }
            return
        }
        viewModelScope.launch {
            val timeInputs = state.times.map {
                IntakeTimeInput(
                    id = it.id,
                    timeOfDay = it.timeOfDay,
                    doseMode = it.doseMode,
                    doseValue = parseLocaleAwareDouble(it.doseValueText) ?: 0.0,
                )
            }
            scheduleRepository.savePeriod(
                scheduleId = state.scheduleId,
                drugId = state.drugId,
                startDate = state.effectiveStartDate,
                endMode = state.endMode,
                endDate = state.computedEndDate,
                durationDays = if (state.endMode == EndMode.DAYS) state.durationDays else null,
                cycleType = state.cycleType,
                specificDays = if (state.cycleType == CycleType.SPECIFIC_DAYS) state.specificDays else null,
                customCycleText = if (state.cycleType == CycleType.CUSTOM) state.customCycleText.trim().takeIf { it.isNotBlank() } else null,
                intakeDays = if (state.cycleType == CycleType.DAYS_ON_OFF) state.intakeDaysText.toInt() else null,
                breakDays = if (state.cycleType == CycleType.DAYS_ON_OFF) state.breakDaysText.toInt() else null,
                times = timeInputs,
            )
            onSaved()
        }
    }
}
