package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.isSession
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.BatchDecrement
import app.zelgray.pills_in_time.domain.model.DoseConsumptionResult
import app.zelgray.pills_in_time.domain.model.DrugStockProjection
import app.zelgray.pills_in_time.domain.model.PeriodStockProjection
import app.zelgray.pills_in_time.domain.model.StockOverallProjection
import java.time.LocalDate
import java.time.LocalTime
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
        val atStartByBatch = mutableMapOf<Long, Map<Long, Double>>()
        val atEndByBatch = mutableMapOf<Long, Map<Long, Double>>()
        val batchExhaustionDates = mutableMapOf<Long, LocalDate>()
        // Per-period first-insufficient-date, not a single shared date — a later
        // period sharing the same now-exhausted stock/batch has its own dose
        // fail on its own days, which a single global "first ever" date would
        // never capture once it's already been claimed by an earlier period.
        val periodInsufficientDates = mutableMapOf<Long, LocalDate>()

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
                    if (period.scheduledIntake.pinnedBatchId == null && batches.size > 1) {
                        atStartByBatch[sid] = working.associate { it.id to it.quantity }
                    }
                }
            }

            val activePeriods = relevant.filter { isPeriodActiveOn(it.scheduledIntake, date) }
            for (period in activePeriods) {
                val periodSid = period.scheduledIntake.id
                for (time in period.times) {
                    // A session-based time (see IntakeTime.isSession) has no
                    // single daily dose — it fires many times a day, either an
                    // exact count (COUNT_PER_DAY) or an estimate from its
                    // interval and how much of the day it's active for (HOURLY).
                    val dosesToday = if (time.isSession) sessionDosesPerDay(time) else 1
                    repeat(dosesToday) {
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
                            is DoseConsumptionResult.Insufficient -> {
                                if (runOutDate == null) runOutDate = date
                                periodInsufficientDates.putIfAbsent(periodSid, date)
                                // The implicated batch(es) may never actually reach literal
                                // zero (an atomic dose that can't fully resolve consumes
                                // nothing, so they get stuck just above it) — mark them
                                // exhausted here too, not only via the quantity<=0 check below.
                                result.shortBatchIds.forEach { id -> batchExhaustionDates.putIfAbsent(id, date) }
                            }
                        }
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
                    if (period.scheduledIntake.pinnedBatchId == null && batches.size > 1) {
                        atEndByBatch[sid] = working.associate { it.id to it.quantity }
                    }
                }
            }

            date = date.plusDays(1)
        }

        val periodProjections = relevant.associate { p ->
            val sid = p.scheduledIntake.id
            val pinnedBatchId = p.scheduledIntake.pinnedBatchId
            val start = atStart[sid] ?: batches.sumOf { it.quantity }
            val end = atEnd[sid]
            // Recorded only while this exact period was itself active and its own
            // dose failed to resolve, so it's already guaranteed to fall within
            // this period's own date range — no separate bound check needed, and
            // an unrelated, separately-impossible period elsewhere never phantom-
            // flags this one (it only ever sets its own sid's entry).
            val depletionWithinPeriod = periodInsufficientDates[sid] != null
            sid to PeriodStockProjection(
                atStart = start,
                atEnd = end,
                // Running out exactly after covering the period's last dose (end == 0,
                // but every dose along the way resolved fine) isn't a problem — only an
                // actual unresolved (Insufficient) dose within the period is.
                stockDepleted = start <= 0.0 || depletionWithinPeriod,
                atStartByBatch = atStartByBatch[sid]
                    ?: (if (pinnedBatchId == null && batches.size > 1) batches.associate { it.id to it.quantity } else emptyMap()),
                atEndByBatch = atEndByBatch[sid],
            )
        }

        val overall = when {
            runOutDate != null -> StockOverallProjection.RunsOutOn(runOutDate)
            hasOpenEnded -> StockOverallProjection.SufficientLongTerm
            else -> StockOverallProjection.RemainingAfterAllPeriods(totalRemaining())
        }

        return DrugStockProjection(periodProjections, overall, batchExhaustionDates)
    }

    /**
     * COUNT_PER_DAY has an exact count. HOURLY has no fixed count — estimated
     * from how many interval-hours fit between its day-start prompt time and
     * midnight, since that's the only signal available (there's no separate
     * "usual bedtime" setting). A rough estimate, not a guarantee — matches
     * the same tradeoff GenerateOccurrencesForDateUseCase makes by not
     * needing to know the actual end-of-day time either.
     */
    private fun sessionDosesPerDay(time: IntakeTime): Int {
        time.sessionTimesPerDay?.let { return it }
        val intervalHours = time.sessionIntervalHours ?: return 1
        val startFrom = time.sessionDayStartFrom ?: LocalTime.MIDNIGHT
        val awakeHours = (24 - startFrom.hour).coerceAtLeast(1)
        return (awakeHours / intervalHours).coerceAtLeast(1)
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
