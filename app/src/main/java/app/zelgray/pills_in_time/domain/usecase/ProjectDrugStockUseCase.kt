package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.DrugStockProjection
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.model.PeriodStockProjection
import app.zelgray.pills_in_time.domain.model.StockOverallProjection
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Simulates day-by-day stock consumption from today onward across a drug's
 * periods to project, per period, how much stock is left at its start and
 * at its end, plus an overall figure for what's left once every bounded
 * period concludes (or when stock is projected to run out, if sooner).
 *
 * Periods that already ended before today are excluded — there's no stock
 * ledger to reconstruct what the supply looked like back then.
 */
class ProjectDrugStockUseCase @Inject constructor() {

    operator fun invoke(
        periods: List<ScheduledIntakeWithTimes>,
        totalStock: Double,
        effectiveStrength: EffectiveStrength?,
        today: LocalDate,
    ): DrugStockProjection {
        val relevant = periods.filter { p ->
            val end = p.scheduledIntake.endDate
            end == null || !end.isBefore(today)
        }
        if (relevant.isEmpty()) {
            return DrugStockProjection(emptyMap(), StockOverallProjection.NoActivePeriods)
        }

        val hasOpenEnded = relevant.any { it.scheduledIntake.endDate == null }
        val horizonEnd = if (hasOpenEnded) {
            today.plusDays(HORIZON_DAYS)
        } else {
            relevant.mapNotNull { it.scheduledIntake.endDate }.max()
        }

        var remaining = totalStock
        var runOutDate: LocalDate? = null
        val atStart = mutableMapOf<Long, Double>()
        val atEnd = mutableMapOf<Long, Double>()

        var date = today
        while (!date.isAfter(horizonEnd)) {
            for (period in relevant) {
                val sid = period.scheduledIntake.id
                val effectiveStartDate = maxOf(period.scheduledIntake.startDate, today)
                if (date == effectiveStartDate) {
                    atStart[sid] = remaining
                }
            }

            val consumedToday = relevant
                .filter { isActiveOn(it.scheduledIntake, date) }
                .sumOf { p -> p.times.sumOf { toUnits(it, effectiveStrength) } }

            if (runOutDate == null && consumedToday > remaining) {
                runOutDate = date
            }
            remaining = (remaining - consumedToday).coerceAtLeast(0.0)

            for (period in relevant) {
                val sid = period.scheduledIntake.id
                val end = period.scheduledIntake.endDate
                if (end != null && date == end) {
                    atEnd[sid] = remaining
                }
            }

            date = date.plusDays(1)
        }

        val periodProjections = relevant.associate { p ->
            val sid = p.scheduledIntake.id
            val start = atStart[sid] ?: totalStock
            val end = atEnd[sid]
            sid to PeriodStockProjection(
                atStart = start,
                atEnd = end,
                stockDepleted = start <= 0.0 || (end != null && end <= 0.0) || (end == null && runOutDate != null),
            )
        }

        val overall = when {
            runOutDate != null -> StockOverallProjection.RunsOutOn(runOutDate)
            hasOpenEnded -> StockOverallProjection.SufficientLongTerm
            else -> StockOverallProjection.RemainingAfterAllPeriods(remaining)
        }

        return DrugStockProjection(periodProjections, overall)
    }

    private fun toUnits(time: IntakeTime, effectiveStrength: EffectiveStrength?): Double =
        when {
            time.doseMode == DoseMode.UNITS -> time.doseValue
            effectiveStrength != null && effectiveStrength.value > 0 -> time.doseValue / effectiveStrength.value
            else -> 0.0
        }

    private fun isActiveOn(period: ScheduledIntake, date: LocalDate): Boolean {
        if (date.isBefore(period.startDate)) return false
        val end = period.endDate
        if (end != null && date.isAfter(end)) return false
        return when (period.cycleType) {
            CycleType.DAILY, CycleType.CUSTOM -> true
            CycleType.EVERY_OTHER_DAY -> ChronoUnit.DAYS.between(period.startDate, date) % 2 == 0L
            CycleType.SPECIFIC_DAYS -> period.specificDays?.contains(date.dayOfWeek) == true
            CycleType.DAYS_ON_OFF -> {
                val on = period.intakeDays
                val off = period.breakDays
                if (on == null || off == null || on <= 0 || off <= 0) {
                    false
                } else {
                    val daysSinceStart = ChronoUnit.DAYS.between(period.startDate, date)
                    daysSinceStart % (on + off) < on
                }
            }
        }
    }

    private companion object {
        const val HORIZON_DAYS = 730L
    }
}
