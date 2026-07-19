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
import app.zelgray.pills_in_time.domain.model.DoseCombo
import app.zelgray.pills_in_time.domain.model.DoseComboPiece
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.usecase.FindDoseCombosUseCase
import app.zelgray.pills_in_time.domain.usecase.ResolveEffectiveStrengthUseCase
import app.zelgray.pills_in_time.domain.usecase.computeEndDateForOccurrences
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
    // Which strength combo this STRENGTH-mode dose is fixed to, chosen here
    // (or defaulted to the top-ranked one) so logging never needs to guess
    // which on-hand batches to decrement from. Null for UNITS mode, or when
    // no exact combo exists for the entered dose.
    val doseAllocation: List<DoseComboPiece>? = null,
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
    val durationOccurrencesText: String = "8",
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
    val durationOccurrencesError: Boolean = false,
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
    val durationOccurrences: Int get() = durationOccurrencesText.toIntOrNull()?.coerceAtLeast(1) ?: 1

    // Only non-DAILY/CUSTOM cycles have a meaningful "N occurrences" duration
    // distinct from "N days" — for those two, every day is active, so the two
    // modes would mean the same thing.
    val occurrenceDurationAvailable: Boolean get() = cycleType != CycleType.DAILY && cycleType != CycleType.CUSTOM

    val computedEndDate: LocalDate?
        get() = when (endMode) {
            EndMode.DATE -> endDate
            EndMode.DAYS -> effectiveStartDate.plusDays((durationDays - 1).toLong())
            EndMode.OCCURRENCES -> computeEndDateForOccurrences(
                startDate = effectiveStartDate,
                cycleType = cycleType,
                specificDays = specificDays,
                intakeDays = intakeDaysText.toIntOrNull(),
                breakDays = breakDaysText.toIntOrNull(),
                occurrences = durationOccurrences,
            )
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
                            durationOccurrencesText = (schedule.durationOccurrences ?: 8).toString(),
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
                                    doseAllocation = t.doseAllocation,
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

    fun onDurationOccurrencesChange(text: String) =
        _uiState.update { it.copy(durationOccurrencesText = text, durationOccurrencesError = false) }

    fun onCycleTypeChange(type: CycleType) = _uiState.update { state ->
        val occurrenceModeStillAvailable = type != CycleType.DAILY && type != CycleType.CUSTOM
        state.copy(
            cycleType = type,
            // "N occurrences" isn't a meaningful distinct choice for DAILY/CUSTOM
            // (every day is active), so fall back to "N days" if it was selected.
            endMode = if (state.endMode == EndMode.OCCURRENCES && !occurrenceModeStillAvailable) EndMode.DAYS else state.endMode,
            specificDaysError = false,
            daysOnOffError = false,
        )
    }

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
            state.copy(
                times = state.times.map { if (it.rowKey == rowKey) it.copy(doseMode = mode, doseAllocation = null) else it },
            )
        }
    }

    fun onTimeDoseValueChange(rowKey: Long, text: String) {
        _uiState.update { state ->
            state.copy(
                times = state.times.map { if (it.rowKey == rowKey) it.copy(doseValueText = text, doseAllocation = null) else it },
            )
        }
    }

    /** Fixes which on-hand strength combo a STRENGTH-mode row's dose comes from. */
    fun onComboSelected(rowKey: Long, combo: DoseCombo) {
        _uiState.update { state ->
            state.copy(times = state.times.map { if (it.rowKey == rowKey) it.copy(doseAllocation = combo.pieces) else it })
        }
    }

    /** The combo to show as selected in the picker: the row's own choice, or the top-ranked one by default. */
    fun selectedComboFor(row: TimeRowState, combos: List<DoseCombo>): DoseCombo? =
        combos.find { it.pieces == row.doseAllocation } ?: combos.firstOrNull()

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
        val durationOccurrencesInvalid = state.endMode == EndMode.OCCURRENCES &&
            (state.durationOccurrencesText.toIntOrNull() == null || state.durationOccurrencesText.toIntOrNull()!! < 1)
        val specificDaysInvalid = state.cycleType == CycleType.SPECIFIC_DAYS && state.specificDays.isEmpty()
        val daysOnOffInvalid = state.cycleType == CycleType.DAYS_ON_OFF &&
            ((state.intakeDaysText.toIntOrNull() ?: 0) < 1 || (state.breakDaysText.toIntOrNull() ?: 0) < 1)

        if (timesInvalid || durationInvalid || durationOccurrencesInvalid || specificDaysInvalid || daysOnOffInvalid) {
            _uiState.update {
                it.copy(
                    timesError = timesInvalid,
                    durationDaysError = durationInvalid,
                    durationOccurrencesError = durationOccurrencesInvalid,
                    specificDaysError = specificDaysInvalid,
                    daysOnOffError = daysOnOffInvalid,
                )
            }
            return
        }
        viewModelScope.launch {
            val timeInputs = state.times.map { row ->
                val doseValue = parseLocaleAwareDouble(row.doseValueText) ?: 0.0
                // Persist whichever combo the row ended up with — the user's
                // explicit pick, or (if they never touched the picker, e.g. a
                // single-option or untouched default case) the top-ranked one.
                val allocation = if (row.doseMode == DoseMode.STRENGTH) {
                    row.doseAllocation ?: findDoseCombos(state.stockBatches, doseValue).firstOrNull()?.pieces
                } else {
                    null
                }
                IntakeTimeInput(
                    id = row.id,
                    timeOfDay = row.timeOfDay,
                    doseMode = row.doseMode,
                    doseValue = doseValue,
                    doseAllocation = allocation,
                )
            }
            scheduleRepository.savePeriod(
                scheduleId = state.scheduleId,
                drugId = state.drugId,
                startDate = state.effectiveStartDate,
                endMode = state.endMode,
                endDate = state.computedEndDate,
                durationDays = if (state.endMode == EndMode.DAYS) state.durationDays else null,
                durationOccurrences = if (state.endMode == EndMode.OCCURRENCES) state.durationOccurrences else null,
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
