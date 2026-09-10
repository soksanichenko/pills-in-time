package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.LowStockAlert
import java.time.LocalDate
import javax.inject.Inject

/**
 * Evaluates each stock batch that has a low-stock reminder configured — via
 * exactly one of two mutually exclusive modes:
 *  - days-before: due once the projected exhaustion date falls within the
 *    configured notice window; dedupes on that exact date
 *    (lowStockReminderFiredForRunOutDate) so it re-fires only if the forecast
 *    later shifts (e.g. the drug's periods change).
 *  - units-before: due once current quantity drops to/below the configured
 *    threshold, independent of any consumption forecast; dedupes via a
 *    boolean flag (lowStockReminderUnitsAlreadyFired) since there's no
 *    forecast date to key off, reset by the caller once quantity recovers
 *    above the threshold (e.g. a restock).
 *
 * Runs ProjectDrugStockUseCase once per drug (across all its batches
 * together, so real multi-strength dose combos are modeled correctly) and
 * reads each batch's own projected exhaustion date back out of that single
 * simulation, rather than simulating each batch in isolation — a batch's
 * quantity can only be understood in the context of every other batch it's
 * actually consumed alongside. That date is purely informational for a
 * units-before alert, which fires on quantity alone.
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
            val runOutDate = projection.batchExhaustionDates[batch.id]
            if (!isLow(batch, runOutDate, today)) return@mapNotNull null
            val alreadyNotified = if (batch.lowStockReminderDaysBefore != null) {
                batch.lowStockReminderFiredForRunOutDate == runOutDate
            } else {
                batch.lowStockReminderUnitsAlreadyFired
            }
            if (alreadyNotified) return@mapNotNull null
            LowStockAlert(batchId = batch.id, drugId = batch.drugId, runOutDate = runOutDate)
        }
    }

    /**
     * Whether [batch] is currently below its configured reminder threshold —
     * the bare condition, without the fired/already-notified dedup above.
     * Exposed so SnoozeLowStockReminderWorker can re-check this exact
     * condition before re-posting a postponed reminder (e.g. a restock
     * resolved it in the meantime) without duplicating the logic.
     */
    fun isLow(batch: DrugStockBatch, runOutDate: LocalDate?, today: LocalDate): Boolean {
        val daysBefore = batch.lowStockReminderDaysBefore
        val unitsBefore = batch.lowStockReminderUnitsBefore
        return when {
            daysBefore != null -> runOutDate != null && !runOutDate.isAfter(today.plusDays(daysBefore.toLong()))
            unitsBefore != null -> batch.quantity <= unitsBefore
            else -> false
        }
    }
}
