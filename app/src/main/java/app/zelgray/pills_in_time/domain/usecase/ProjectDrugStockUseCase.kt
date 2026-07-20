package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.BatchDecrement
import app.zelgray.pills_in_time.domain.model.DoseConsumptionResult
import app.zelgray.pills_in_time.domain.model.DrugStockProjection
import app.zelgray.pills_in_time.domain.model.PeriodStockProjection
import app.zelgray.pills_in_time.domain.model.StockOverallProjection
import java.time.LocalDate
import javax.inject.Inject

/**
 * Simulates day-by-day stock consumption from today onward across a drug's
 * periods to project, per period, how much stock is left at its start and
 * at its end, plus an overall figure for what's left once every bounded
 * period concludes (or when stock is projected to run out, if sooner).
 *
 * Consumption is resolved the same way a real logged dose would be
 * (ResolveDoseConsumptionUseCase — a pinned strength combo, or FIFO across
 * whichever batches are on hand), against a scratch copy of the batches,
 * so the projection stays consistent with what actually happens when doses
 * get logged for real. This matters once a dose spans multiple distinct
 * on-hand strengths: a single combined "total stock ÷ one reference
 * strength" number can't represent that correctly. A day a dose can't be
 * resolved consumes nothing that day (mirroring the real blocking behavior
 * on insufficient stock) rather than force-draining the remainder.
 *
 * Periods that already ended before today are excluded — there's no stock
 * ledger to reconstruct what the supply looked like back then.
 */
class ProjectDrugStockUseCase @Inject constructor(
    private val resolveDoseConsumption: ResolveDoseConsumptionUseCase,
) {

    operator fun invoke(
        periods: List<ScheduledIntakeWithTimes>,
        batches: List<DrugStockBatch>,
        today: LocalDate,
    ): DrugStockProjection {
        val relevant = periods.filter { p ->
            val end = p.scheduledIntake.endDate
            end == null || !end.isBefore(today)
        }
        if (relevant.isEmpty()) {
            return DrugStockProjection(emptyMap(), StockOverallProjection.NoActivePeriods, emptyMap())
        }

        val hasOpenEnded = relevant.any { it.scheduledIntake.endDate == null }
        val horizonEnd = if (hasOpenEnded) {
            today.plusDays(HORIZON_DAYS)
        } else {
            relevant.mapNotNull { it.scheduledIntake.endDate }.max()
        }

        var working = batches
        var runOutDate: LocalDate? = null
        val atStart = mutableMapOf<Long, Double>()
        val atEnd = mutableMapOf<Long, Double>()
        val batchExhaustionDates = mutableMapOf<Long, LocalDate>()

        fun totalRemaining() = working.sumOf { it.quantity }
        // A period pinned to one specific supply cares about that batch's own
        // quantity, not the drug-wide total — so its "runs out" reflects only
        // that supply, per period, instead of the aggregate across every batch.
        fun remainingFor(pinnedBatchId: Long?) =
            pinnedBatchId?.let { id -> working.firstOrNull { it.id == id }?.quantity ?: 0.0 } ?: totalRemaining()

        var date = today
        while (!date.isAfter(horizonEnd)) {
            for (period in relevant) {
                val sid = period.scheduledIntake.id
                val effectiveStartDate = maxOf(period.scheduledIntake.startDate, today)
                if (date == effectiveStartDate) {
                    atStart[sid] = remainingFor(period.scheduledIntake.pinnedBatchId)
                }
            }

            val activePeriods = relevant.filter { isPeriodActiveOn(it.scheduledIntake, date) }
            for (period in activePeriods) {
                for (time in period.times) {
                    when (
                        val result = resolveDoseConsumption(
                            time.doseMode,
                            time.doseValue,
                            time.doseAllocation,
                            working,
                            period.scheduledIntake.pinnedBatchId,
                        )
                    ) {
                        is DoseConsumptionResult.Resolved -> working = applyDecrements(working, result.decrements)
                        DoseConsumptionResult.Insufficient -> if (runOutDate == null) runOutDate = date
                    }
                }
            }

            working.forEach { batch ->
                if (batch.quantity <= 0.0) {
                    batchExhaustionDates.putIfAbsent(batch.id, date)
                }
            }

            for (period in relevant) {
                val sid = period.scheduledIntake.id
                val end = period.scheduledIntake.endDate
                if (end != null && date == end) {
                    atEnd[sid] = remainingFor(period.scheduledIntake.pinnedBatchId)
                }
            }

            date = date.plusDays(1)
        }

        val periodProjections = relevant.associate { p ->
            val sid = p.scheduledIntake.id
            val pinnedBatchId = p.scheduledIntake.pinnedBatchId
            val start = atStart[sid] ?: batches.sumOf { it.quantity }
            val end = atEnd[sid]
            val periodEnd = p.scheduledIntake.endDate
            val periodStart = maxOf(p.scheduledIntake.startDate, today)
            val depletionDate = if (pinnedBatchId != null) batchExhaustionDates[pinnedBatchId] else runOutDate
            // A shortfall only counts against a period if it actually falls
            // within that period's own active date range — an unrelated,
            // separately-impossible period elsewhere shouldn't phantom-flag this one.
            val depletionWithinPeriod = depletionDate != null &&
                !depletionDate.isBefore(periodStart) &&
                (periodEnd == null || !depletionDate.isAfter(periodEnd))
            sid to PeriodStockProjection(
                atStart = start,
                atEnd = end,
                stockDepleted = start <= 0.0 || (end != null && end <= 0.0) || depletionWithinPeriod,
            )
        }

        val overall = when {
            runOutDate != null -> StockOverallProjection.RunsOutOn(runOutDate)
            hasOpenEnded -> StockOverallProjection.SufficientLongTerm
            else -> StockOverallProjection.RemainingAfterAllPeriods(totalRemaining())
        }

        return DrugStockProjection(periodProjections, overall, batchExhaustionDates)
    }

    private fun applyDecrements(batches: List<DrugStockBatch>, decrements: List<BatchDecrement>): List<DrugStockBatch> {
        val byId = decrements.associateBy { it.batchId }
        return batches.map { batch ->
            val decrement = byId[batch.id] ?: return@map batch
            batch.copy(quantity = (batch.quantity - decrement.quantity).coerceAtLeast(0.0))
        }
    }

    private companion object {
        const val HORIZON_DAYS = 730L
    }
}
