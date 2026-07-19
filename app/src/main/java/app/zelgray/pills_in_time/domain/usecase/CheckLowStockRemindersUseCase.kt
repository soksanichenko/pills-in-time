package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.LowStockAlert
import java.time.LocalDate
import javax.inject.Inject

/**
 * Evaluates each stock batch that has a low-stock reminder configured. Runs
 * ProjectDrugStockUseCase once per drug (across all its batches together, so
 * real multi-strength dose combos are modeled correctly) and reads each
 * batch's own projected exhaustion date back out of that single simulation,
 * rather than simulating each batch in isolation — a batch's quantity can
 * only be understood in the context of every other batch it's actually
 * consumed alongside.
 *
 * A batch is due for an alert when its projected exhaustion date falls
 * within the batch's configured notice window and we haven't already
 * notified for that exact date (lowStockReminderFiredForRunOutDate) — so the
 * alert re-fires if the forecast later shifts (e.g. the drug's periods
 * change) but not on every periodic check while it's unchanged.
 */
class CheckLowStockRemindersUseCase @Inject constructor(
    private val projectDrugStock: ProjectDrugStockUseCase,
) {

    operator fun invoke(
        batches: List<DrugStockBatch>,
        periodsByDrugId: Map<Long, List<ScheduledIntakeWithTimes>>,
        today: LocalDate,
    ): List<LowStockAlert> = batches.groupBy { it.drugId }.flatMap { (drugId, drugBatches) ->
        val projection = projectDrugStock(
            periods = periodsByDrugId[drugId].orEmpty(),
            batches = drugBatches,
            today = today,
        )
        drugBatches.mapNotNull { batch ->
            val daysBefore = batch.lowStockReminderDaysBefore ?: return@mapNotNull null
            val runOutDate = projection.batchExhaustionDates[batch.id] ?: return@mapNotNull null

            if (runOutDate.isAfter(today.plusDays(daysBefore.toLong()))) return@mapNotNull null
            if (batch.lowStockReminderFiredForRunOutDate == runOutDate) return@mapNotNull null

            LowStockAlert(batchId = batch.id, drugId = batch.drugId, runOutDate = runOutDate)
        }
    }
}
