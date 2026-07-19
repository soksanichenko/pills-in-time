package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.ShortfallItem
import app.zelgray.pills_in_time.domain.model.StockShortfall
import java.time.LocalDate
import javax.inject.Inject

/**
 * How much (and of what on-hand strength) is still missing to cover every
 * remaining dose from today through [windowEnd], across every period passed
 * in — they all draw from the same shared batches, so a course can only be
 * judged coverable in the context of everything else also consuming from it.
 *
 * This is a pure aggregate demand-vs-supply comparison, not a day-by-day
 * consumption simulation: since an unresolvable dose blocks entirely and
 * consumes nothing (see ResolveDoseConsumptionUseCase/ProjectDrugStockUseCase),
 * simulating forward would never actually spend the stock that's genuinely
 * on hand, undercounting the true shortfall. Total demand per strength (or
 * per plain unit count, for UNITS-mode doses) minus what's currently
 * available is the correct total to still buy.
 */
class ComputeStockShortfallUseCase @Inject constructor(
    private val findDoseCombos: FindDoseCombosUseCase,
) {
    operator fun invoke(
        periods: List<ScheduledIntakeWithTimes>,
        windowEnd: LocalDate,
        batches: List<DrugStockBatch>,
        today: LocalDate,
    ): StockShortfall {
        if (windowEnd.isBefore(today)) return StockShortfall(emptyList())

        val relevant = periods.filter { p ->
            val end = p.scheduledIntake.endDate
            end == null || !end.isBefore(today)
        }

        val strengthDemand = mutableMapOf<Double, Double>()
        var unitsDemand = 0.0

        var date = today
        while (!date.isAfter(windowEnd)) {
            relevant.filter { isPeriodActiveOn(it.scheduledIntake, date) }.flatMap { it.times }.forEach { time ->
                if (time.doseMode == DoseMode.UNITS) {
                    unitsDemand += time.doseValue
                } else {
                    val pieces = time.doseAllocation ?: findDoseCombos(batches, time.doseValue).firstOrNull()?.pieces
                    if (pieces == null) {
                        unitsDemand += time.doseValue
                    } else {
                        pieces.forEach { piece -> strengthDemand[piece.strength] = (strengthDemand[piece.strength] ?: 0.0) + piece.count }
                    }
                }
            }
            date = date.plusDays(1)
        }

        if (strengthDemand.isEmpty() && unitsDemand <= EPSILON) return StockShortfall(emptyList())

        val availableByStrength = batches.filter { it.strengthValue != null }
            .groupBy { it.strengthValue!! }
            .mapValuesTo(mutableMapOf()) { (_, list) -> list.sumOf { it.quantity } }

        val items = mutableListOf<ShortfallItem>()
        strengthDemand.forEach { (strength, demand) ->
            val available = availableByStrength[strength] ?: 0.0
            val used = minOf(available, demand)
            availableByStrength[strength] = available - used
            val short = demand - used
            if (short > EPSILON) {
                val unit = batches.firstOrNull { it.strengthValue == strength }?.strengthUnit
                items.add(ShortfallItem(strength, unit, short))
            }
        }

        if (unitsDemand > EPSILON) {
            val remainingAcrossAllBatches = availableByStrength.values.sum() +
                batches.filter { it.strengthValue == null }.sumOf { it.quantity }
            val short = unitsDemand - remainingAcrossAllBatches
            if (short > EPSILON) {
                items.add(ShortfallItem(null, null, short))
            }
        }

        return StockShortfall(items.sortedByDescending { it.strengthValue ?: Double.MAX_VALUE })
    }

    private companion object {
        const val EPSILON = 1e-6
    }
}
