package app.zelgray.pills_in_time.ui.history

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.repository.PatientRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.domain.model.RecordLogResult
import app.zelgray.pills_in_time.domain.usecase.ScheduleAlarmsForWindowUseCase
import app.zelgray.pills_in_time.notification.NotificationContracts
import app.zelgray.pills_in_time.ui.navigation.NavRoutes
import app.zelgray.pills_in_time.util.formatPlainNumber
import app.zelgray.pills_in_time.util.parseLocaleAwareDouble
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class AddEditHistoryEntryUiState(
    val logId: Long? = null,
    val isLoading: Boolean = true,
    val drugs: List<Drug> = emptyList(),
    val selectedDrugId: Long? = null,
    val periods: List<ScheduledIntakeWithTimes> = emptyList(),
    val selectedScheduleId: Long? = null,
    val selectedTimeId: Long? = null,
    val occurrenceDate: LocalDate = LocalDate.now(),
    val doseMode: DoseMode = DoseMode.UNITS,
    val editReadOnlyDrugName: String = "",
    val editReadOnlyTimeOfDay: LocalTime? = null,
    val actualDate: LocalDate = LocalDate.now(),
    val actualTime: LocalTime = LocalTime.now(),
    val doseValueText: String = "1",
    val status: IntakeStatus = IntakeStatus.TAKEN,
    val selectionError: Boolean = false,
    val doseError: Boolean = false,
    val insufficientStockError: Boolean = false,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = logId != null
    val selectedPeriod: ScheduledIntakeWithTimes? get() = periods.find { it.scheduledIntake.id == selectedScheduleId }
}

@HiltViewModel
class AddEditHistoryEntryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val drugRepository: DrugRepository,
    private val scheduleRepository: ScheduleRepository,
    private val intakeRepository: IntakeRepository,
    private val patientRepository: PatientRepository,
) : ViewModel() {

    private val editingLogId: Long? = savedStateHandle[NavRoutes.ARG_LOG_ID]

    private val _uiState = MutableStateFlow(AddEditHistoryEntryUiState(logId = editingLogId))
    val uiState: StateFlow<AddEditHistoryEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val patientId = patientRepository.observeCurrentPatientId().first()
            val drugs = drugRepository.observeAllDrugs(patientId).first()
            if (editingLogId != null) {
                val log = intakeRepository.getById(editingLogId)
                if (log != null) {
                    val drug = drugs.find { it.id == log.drugId }
                    val time = scheduleRepository.getTimeById(log.intakeTimeId)
                    val actualZoned = log.actualDateTime.atZone(ZoneId.systemDefault())
                    _uiState.update {
                        it.copy(
                            drugs = drugs,
                            selectedDrugId = log.drugId,
                            selectedScheduleId = log.scheduledIntakeId,
                            selectedTimeId = log.intakeTimeId,
                            occurrenceDate = log.occurrenceDate,
                            doseMode = log.actualDoseMode,
                            editReadOnlyDrugName = drug?.name.orEmpty(),
                            editReadOnlyTimeOfDay = time?.timeOfDay,
                            actualDate = actualZoned.toLocalDate(),
                            actualTime = actualZoned.toLocalTime(),
                            doseValueText = formatPlainNumber(log.actualDoseValue),
                            status = log.status,
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } else {
                _uiState.update { it.copy(drugs = drugs, isLoading = false) }
            }
        }
    }

    fun onDrugSelected(drugId: Long) {
        viewModelScope.launch {
            val periods = scheduleRepository.getPeriodsForDrugOnce(drugId)
            _uiState.update {
                it.copy(
                    selectedDrugId = drugId,
                    periods = periods,
                    selectedScheduleId = null,
                    selectedTimeId = null,
                    selectionError = false,
                )
            }
        }
    }

    fun onPeriodSelected(scheduleId: Long) {
        _uiState.update { state ->
            val period = state.periods.find { it.scheduledIntake.id == scheduleId }
            val firstTime = period?.times?.minByOrNull { it.timeOfDay }
            state.copy(
                selectedScheduleId = scheduleId,
                selectedTimeId = firstTime?.id,
                occurrenceDate = period?.scheduledIntake?.startDate ?: state.occurrenceDate,
                doseMode = firstTime?.doseMode ?: state.doseMode,
                doseValueText = firstTime?.let { formatPlainNumber(it.doseValue) } ?: state.doseValueText,
                actualTime = firstTime?.timeOfDay ?: state.actualTime,
                selectionError = false,
            )
        }
    }

    fun onTimeSelected(timeId: Long) {
        _uiState.update { state ->
            val time = state.selectedPeriod?.times?.find { it.id == timeId }
            state.copy(
                selectedTimeId = timeId,
                doseMode = time?.doseMode ?: state.doseMode,
                doseValueText = time?.let { formatPlainNumber(it.doseValue) } ?: state.doseValueText,
                actualTime = time?.timeOfDay ?: state.actualTime,
                selectionError = false,
            )
        }
    }

    fun onOccurrenceDateChange(date: LocalDate) = _uiState.update { it.copy(occurrenceDate = date) }
    fun onActualDateChange(date: LocalDate) = _uiState.update { it.copy(actualDate = date) }
    fun onActualTimeChange(time: LocalTime) = _uiState.update { it.copy(actualTime = time) }
    fun onDoseValueChange(text: String) = _uiState.update { it.copy(doseValueText = text, doseError = false, insufficientStockError = false) }
    fun onStatusChange(status: IntakeStatus) = _uiState.update { it.copy(status = status, insufficientStockError = false) }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val doseValue = parseLocaleAwareDouble(state.doseValueText)
        if (doseValue == null || doseValue <= 0) {
            _uiState.update { it.copy(doseError = true) }
            return
        }
        if (!state.isEditing && (state.selectedDrugId == null || state.selectedScheduleId == null || state.selectedTimeId == null)) {
            _uiState.update { it.copy(selectionError = true) }
            return
        }

        viewModelScope.launch {
            val actualDateTime = ZonedDateTime.of(state.actualDate, state.actualTime, ZoneId.systemDefault()).toInstant()
            val result = if (state.isEditing) {
                intakeRepository.updateLogDetails(
                    logId = state.logId!!,
                    actualDateTime = actualDateTime,
                    doseValue = doseValue,
                    doseMode = state.doseMode,
                    status = state.status,
                )
            } else {
                intakeRepository.recordManualEntry(
                    drugId = state.selectedDrugId!!,
                    scheduledIntakeId = state.selectedScheduleId!!,
                    intakeTimeId = state.selectedTimeId!!,
                    occurrenceDate = state.occurrenceDate,
                    actualDateTime = actualDateTime,
                    doseValue = doseValue,
                    doseMode = state.doseMode,
                    status = state.status,
                )
            }
            if (result == RecordLogResult.InsufficientStock) {
                _uiState.update { it.copy(insufficientStockError = true) }
            } else {
                cancelReminderNotification(state.selectedScheduleId!!, state.selectedTimeId!!, state.occurrenceDate)
                onSaved()
            }
        }
    }

    /**
     * Dismisses the reminder notification (and its pending 5-minute repeat) for
     * an occurrence that just got logged directly in-app — otherwise a stale
     * notification would sit there even though the dose is already recorded.
     * Mirrors what IntakeActionReceiver already does when acted on from the
     * notification itself.
     */
    private fun cancelReminderNotification(scheduledIntakeId: Long, intakeTimeId: Long, occurrenceDate: LocalDate) {
        val notificationId = ScheduleAlarmsForWindowUseCase.computeRequestCode(scheduledIntakeId, intakeTimeId, occurrenceDate)
        NotificationManagerCompat.from(context).cancel(notificationId)
        WorkManager.getInstance(context).cancelUniqueWork(NotificationContracts.repeatWorkName(notificationId))
    }
}
