@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.zelgray.pills_in_time.ui.home

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.repository.PatientRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.SettingsRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.model.Occurrence
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus
import app.zelgray.pills_in_time.domain.model.RecordLogResult
import app.zelgray.pills_in_time.domain.usecase.GenerateOccurrencesForDateUseCase
import app.zelgray.pills_in_time.domain.usecase.ResolveEffectiveStrengthUseCase
import app.zelgray.pills_in_time.domain.usecase.ScheduleAlarmsForWindowUseCase
import app.zelgray.pills_in_time.notification.NotificationContracts
import app.zelgray.pills_in_time.notification.PostNotificationWorker
import app.zelgray.pills_in_time.util.NowProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class CalendarDayMark { NONE, TAKEN, SCHEDULED }

data class HomeListItem(
    val occurrence: Occurrence,
    val drug: Drug,
    val stockBatches: List<DrugStockBatch>,
    val effectiveStrength: EffectiveStrength?,
)

data class HomeUiState(
    val dayOffset: Int = 0,
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val items: List<HomeListItem> = emptyList(),
    val isLoading: Boolean = true,
)

private data class Inputs(
    val offset: Int,
    val periods: List<ScheduledIntakeWithTimes>,
    val drugs: List<Drug>,
    val stockBatches: List<DrugStockBatch>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val intakeRepository: IntakeRepository,
    private val drugRepository: DrugRepository,
    private val patientRepository: PatientRepository,
    private val stockRepository: StockRepository,
    private val settingsRepository: SettingsRepository,
    private val generateOccurrences: GenerateOccurrencesForDateUseCase,
    private val resolveEffectiveStrength: ResolveEffectiveStrengthUseCase,
    private val nowProvider: NowProvider,
) : ViewModel() {

    private val dayOffset = MutableStateFlow(0)

    // Keeps today's overdue status live without user interaction, per spec 4.7.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val uiState: StateFlow<HomeUiState> = patientRepository.observeCurrentPatientId()
        .flatMapLatest { patientId ->
            combine(
                dayOffset,
                scheduleRepository.observeAllPeriods(patientId),
                drugRepository.observeAllDrugs(patientId),
                stockRepository.observeAllBatches(),
                ticker,
            ) { offset, periods, drugs, batches, _ -> Inputs(offset, periods, drugs, batches) }
        }
        .flatMapLatest { inputs ->
            val today = nowProvider.currentLocalDate()
            val date = today.plusDays(inputs.offset.toLong())
            intakeRepository.observeLogsForDate(date).map { logs ->
                val now = nowProvider.currentLocalDateTime()
                val drugsById = inputs.drugs.associateBy { it.id }
                val batchesByDrugId = inputs.stockBatches.groupBy { it.drugId }
                val occurrences = generateOccurrences(inputs.periods, logs, date, today, now)
                HomeUiState(
                    dayOffset = inputs.offset,
                    date = date,
                    today = today,
                    items = occurrences.mapNotNull { occ ->
                        val drug = drugsById[occ.drugId] ?: return@mapNotNull null
                        val batches = batchesByDrugId[occ.drugId].orEmpty()
                        HomeListItem(occ, drug, batches, resolveEffectiveStrength(batches))
                    },
                    isLoading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoading = true))

    private val calendarMonth = MutableStateFlow<YearMonth?>(null)

    /** Empty (no DB subscription) unless the date-picker dialog is open, per [onCalendarMonthChanged]/[onCalendarDialogClosed]. */
    val calendarMarks: StateFlow<Map<LocalDate, CalendarDayMark>> = calendarMonth
        .flatMapLatest { month -> calendarMarksFlowFor(month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun calendarMarksFlowFor(month: YearMonth?): Flow<Map<LocalDate, CalendarDayMark>> {
        if (month == null) return flowOf(emptyMap())
        return patientRepository.observeCurrentPatientId().flatMapLatest { patientId ->
            combine(
                scheduleRepository.observeAllPeriods(patientId),
                intakeRepository.observeRawLogsInRange(patientId, month.atDay(1), month.atEndOfMonth()),
            ) { periods, logs ->
                val today = nowProvider.currentLocalDate()
                val now = nowProvider.currentLocalDateTime()
                (1..month.lengthOfMonth()).associate { day ->
                    val date = month.atDay(day)
                    val occurrences = generateOccurrences(periods, logs, date, today, now)
                    date to calendarMarkFor(date, occurrences, today)
                }
            }
        }
    }

    fun onCalendarMonthChanged(month: YearMonth) {
        calendarMonth.value = month
    }

    fun onCalendarDialogClosed() {
        calendarMonth.value = null
    }

    private fun calendarMarkFor(date: LocalDate, occurrences: List<Occurrence>, today: LocalDate): CalendarDayMark = when {
        occurrences.isEmpty() -> CalendarDayMark.NONE
        occurrences.all { it.status == OccurrenceStatus.TAKEN } -> CalendarDayMark.TAKEN
        date.isAfter(today) -> CalendarDayMark.SCHEDULED
        else -> CalendarDayMark.NONE
    }

    private val _toastMessageRes = MutableStateFlow<Int?>(null)
    val toastMessageRes: StateFlow<Int?> = _toastMessageRes.asStateFlow()

    fun consumeToast() {
        _toastMessageRes.value = null
    }

    fun onPrevDay() = dayOffset.update { it - 1 }
    fun onNextDay() = dayOffset.update { it + 1 }
    fun onGoToToday() {
        dayOffset.value = 0
    }

    fun goToDate(date: LocalDate) {
        dayOffset.value = ChronoUnit.DAYS.between(nowProvider.currentLocalDate(), date).toInt()
    }

    fun onTookIt(item: HomeListItem) = recordAction(item, IntakeStatus.TAKEN)
    fun onSkipped(item: HomeListItem) = recordAction(item, IntakeStatus.SKIPPED)

    /** Reverts an already-logged occurrence back to unlogged (reversing any stock consumption). */
    fun onCancelStatus(item: HomeListItem) {
        val logId = item.occurrence.logId ?: return
        viewModelScope.launch {
            intakeRepository.getById(logId)?.let { intakeRepository.deleteLog(it) }
        }
    }

    /**
     * Re-posts a real notification after the configured snooze delay, same as
     * tapping "Remind later" on the notification itself — this in-app sheet
     * action doesn't have an existing notification to reschedule from, so it
     * enqueues PostNotificationWorker directly instead of going through
     * IntakeActionReceiver/SnoozeWorker.
     */
    fun onSnooze(item: HomeListItem) {
        viewModelScope.launch {
            val minutes = settingsRepository.getSnoozeMinutesOnce()
            val data = NotificationContracts.dataFromOccurrence(item.occurrence)
            val request = OneTimeWorkRequestBuilder<PostNotificationWorker>()
                .setInputData(data)
                .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    private fun recordAction(item: HomeListItem, status: IntakeStatus) {
        viewModelScope.launch {
            val result = intakeRepository.recordQuickAction(
                drugId = item.occurrence.drugId,
                scheduledIntakeId = item.occurrence.scheduledIntakeId,
                intakeTimeId = item.occurrence.intakeTimeId,
                occurrenceDate = item.occurrence.occurrenceDate,
                doseValue = item.occurrence.doseValue,
                doseMode = item.occurrence.doseMode,
                status = status,
            )
            if (result == RecordLogResult.InsufficientStock) {
                _toastMessageRes.value = R.string.insufficient_stock_error
            } else {
                cancelReminderNotification(item.occurrence.scheduledIntakeId, item.occurrence.intakeTimeId, item.occurrence.occurrenceDate)
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
