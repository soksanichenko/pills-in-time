package app.zelgray.pills_in_time.ui.drugs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zelgray.pills_in_time.data.local.entity.DailySession
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.isSession
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.data.repository.DailySessionRepository
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import app.zelgray.pills_in_time.domain.model.DrugStockProjection
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.model.StockShortfall
import app.zelgray.pills_in_time.domain.usecase.ComputeStockShortfallUseCase
import app.zelgray.pills_in_time.domain.usecase.ProjectDrugStockUseCase
import app.zelgray.pills_in_time.domain.usecase.ResolveEffectiveStrengthUseCase
import app.zelgray.pills_in_time.domain.usecase.INDEFINITE_PAUSE_DATE
import app.zelgray.pills_in_time.domain.usecase.computePauseUntilDateForOccurrences
import app.zelgray.pills_in_time.notification.SessionActionHandler
import app.zelgray.pills_in_time.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DrugDetailUiState(
    val drug: Drug? = null,
    val stockBatches: List<DrugStockBatch> = emptyList(),
    val periods: List<ScheduledIntakeWithTimes> = emptyList(),
    val effectiveStrength: EffectiveStrength? = null,
    val stockProjection: DrugStockProjection? = null,
    // Only bounded periods (fixed date / N days / N occurrences) get a
    // shortfall entry here — an open-ended period has no course to complete.
    val shortfallByPeriodId: Map<Long, StockShortfall> = emptyMap(),
    val overallShortfall: StockShortfall? = null,
    // Today's DailySession (see IntakeTime.isSession) for whichever periods
    // have a session-type time — absent means that period's day hasn't been
    // started yet today.
    val todaySessionByScheduledIntakeId: Map<Long, DailySession> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class DrugDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val drugRepository: DrugRepository,
    private val stockRepository: StockRepository,
    private val scheduleRepository: ScheduleRepository,
    private val dailySessionRepository: DailySessionRepository,
    private val sessionActionHandler: SessionActionHandler,
    private val resolveEffectiveStrength: ResolveEffectiveStrengthUseCase,
    private val projectDrugStock: ProjectDrugStockUseCase,
    private val computeStockShortfall: ComputeStockShortfallUseCase,
) : ViewModel() {

    private val drugId: Long = checkNotNull(savedStateHandle[NavRoutes.ARG_DRUG_ID])

    val uiState: StateFlow<DrugDetailUiState> = combine(
        drugRepository.observeById(drugId),
        stockRepository.observeBatchesForDrug(drugId),
        scheduleRepository.observePeriodsForDrug(drugId),
        dailySessionRepository.observeInRange(LocalDate.now(), LocalDate.now()),
    ) { drug, batches, periods, todaySessions ->
        val effectiveStrength = resolveEffectiveStrength(batches)
        val today = LocalDate.now()

        val boundedEndDates = periods.mapNotNull { it.scheduledIntake.endDate }
        val shortfallByPeriodId = periods.mapNotNull { period ->
            val endDate = period.scheduledIntake.endDate ?: return@mapNotNull null
            val shortfall = computeStockShortfall(periods, endDate, batches, today)
            if (shortfall.isEmpty) null else period.scheduledIntake.id to shortfall
        }.toMap()
        val overallShortfall = boundedEndDates.maxOrNull()?.let { latestEnd ->
            computeStockShortfall(periods, latestEnd, batches, today).takeUnless { it.isEmpty }
        }

        DrugDetailUiState(
            drug = drug,
            stockBatches = batches,
            periods = periods,
            effectiveStrength = effectiveStrength,
            stockProjection = projectDrugStock(
                periods = periods,
                batches = batches,
                today = today,
            ),
            shortfallByPeriodId = shortfallByPeriodId,
            overallShortfall = overallShortfall,
            todaySessionByScheduledIntakeId = todaySessions.associateBy { it.scheduledIntakeId },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrugDetailUiState())

    suspend fun hasSchedulesOrStock(): Boolean = drugRepository.hasSchedulesOrStock(drugId)

    fun deleteDrug(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val drug = drugRepository.getById(drugId) ?: return@launch
            drugRepository.deleteDrug(drug)
            onDeleted()
        }
    }

    fun deleteStockBatch(batch: DrugStockBatch) {
        viewModelScope.launch { stockRepository.deleteBatch(batch) }
    }

    /** Adds newly purchased units to an existing batch in place, rather than
     * creating a separate batch — the stock projection (run-out dates)
     * recomputes automatically since it's derived from the reactive
     * stockBatches flow above. */
    fun restockBatch(batch: DrugStockBatch, additionalQuantity: Double) {
        viewModelScope.launch { stockRepository.updateBatch(batch.copy(quantity = batch.quantity + additionalQuantity)) }
    }

    fun deletePeriod(periodWithTimes: ScheduledIntakeWithTimes) {
        viewModelScope.launch { scheduleRepository.deletePeriod(periodWithTimes.scheduledIntake) }
    }

    fun stopPeriod(periodWithTimes: ScheduledIntakeWithTimes) {
        viewModelScope.launch {
            scheduleRepository.stopPeriod(periodWithTimes.scheduledIntake.id, LocalDate.now())
        }
    }

    fun pausePeriodForDays(periodWithTimes: ScheduledIntakeWithTimes, days: Int) {
        val today = LocalDate.now()
        viewModelScope.launch {
            scheduleRepository.pausePeriod(periodWithTimes.scheduledIntake.id, today.plusDays((days - 1).toLong()))
        }
    }

    fun pausePeriodForOccurrences(periodWithTimes: ScheduledIntakeWithTimes, occurrences: Int) {
        val today = LocalDate.now()
        val until = computePauseUntilDateForOccurrences(periodWithTimes.scheduledIntake, today, occurrences)
            ?: periodWithTimes.scheduledIntake.endDate
            ?: INDEFINITE_PAUSE_DATE
        viewModelScope.launch { scheduleRepository.pausePeriod(periodWithTimes.scheduledIntake.id, until) }
    }

    fun pausePeriodIndefinitely(periodWithTimes: ScheduledIntakeWithTimes) {
        viewModelScope.launch {
            scheduleRepository.pausePeriod(periodWithTimes.scheduledIntake.id, INDEFINITE_PAUSE_DATE)
        }
    }

    fun resumePeriod(periodWithTimes: ScheduledIntakeWithTimes) {
        viewModelScope.launch { scheduleRepository.resumePeriod(periodWithTimes.scheduledIntake.id) }
    }

    /** Manual "Начать день" — the same start-of-day anchor a day-start-prompt notification tap would set. */
    fun startDay(periodWithTimes: ScheduledIntakeWithTimes) {
        val time = periodWithTimes.times.find { it.isSession } ?: return
        viewModelScope.launch {
            sessionActionHandler.startDay(periodWithTimes.scheduledIntake.id, time.id, LocalDate.now())
        }
    }

    /** Manual "Иду спать" — ends today's session early. */
    fun endDay(periodWithTimes: ScheduledIntakeWithTimes) {
        val time = periodWithTimes.times.find { it.isSession } ?: return
        viewModelScope.launch {
            sessionActionHandler.endDay(periodWithTimes.scheduledIntake.id, time.id, LocalDate.now())
        }
    }
}
