package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.model.LowStockAlert
import app.zelgray.pills_in_time.domain.model.StockOverallProjection
import java.time.LocalDate
import javax.inject.Inject

/**
 * Evaluates each stock batch that has a low-stock reminder configured, using
 * the same day-by-day simulation as ProjectDrugStockUseCase but scoped to
 * that single batch's own quantity and strength — i.e. "if this batch were
 * consumed on its own at the drug's current pace, when would it run out."
 *
 * A batch is due for an alert when its projected run-out date falls within
 * the batch's configured notice window and we haven't already notified for
 * that exact run-out date (lowStockReminderFiredForRunOutDate) — so the
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
    ): List<LowStockAlert> = batches.mapNotNull { batch ->
        val daysBefore = batch.lowStockReminderDaysBefore ?: return@mapNotNull null
        val periods = periodsByDrugId[batch.drugId].orEmpty()
        val projection = projectDrugStock(
            periods = periods,
            totalStock = batch.quantity,
            effectiveStrength = EffectiveStrength(batch.strengthValue, batch.strengthUnit),
            today = today,
        )
        val overall = projection.overall
        val runOutDate = (overall as? StockOverallProjection.RunsOutOn)?.date ?: return@mapNotNull null

        if (runOutDate.isAfter(today.plusDays(daysBefore.toLong()))) return@mapNotNull null
        if (batch.lowStockReminderFiredForRunOutDate == runOutDate) return@mapNotNull null

        LowStockAlert(batchId = batch.id, drugId = batch.drugId, runOutDate = runOutDate)
    }
}
