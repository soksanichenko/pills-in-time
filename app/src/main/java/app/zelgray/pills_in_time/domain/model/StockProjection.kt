package app.zelgray.pills_in_time.domain.model

import java.time.LocalDate

data class PeriodStockProjection(
    val atStart: Double,
    val atEnd: Double?,
    val stockDepleted: Boolean,
    // Same atStart/atEnd checkpoints, broken down per on-hand batch — a
    // healthy combined total can still hide one specific strength running
    // out while another is plentiful. Empty for a single-batch drug (the
    // combined figure already says everything there is to say) or a period
    // pinned to one supply (atStart/atEnd already reflect just that one).
    val atStartByBatch: Map<Long, Double> = emptyMap(),
    val atEndByBatch: Map<Long, Double>? = null,
)

sealed interface StockOverallProjection {
    data class RemainingAfterAllPeriods(val amount: Double) : StockOverallProjection
    data class RunsOutOn(val date: LocalDate) : StockOverallProjection
    data object SufficientLongTerm : StockOverallProjection
    data object NoActivePeriods : StockOverallProjection
}

data class DrugStockProjection(
    val periodProjections: Map<Long, PeriodStockProjection>,
    val overall: StockOverallProjection,
    // The date each on-hand batch's own quantity is simulated to hit zero,
    // keyed by batch id — used for per-batch low-stock reminders. Absent
    // means that batch isn't projected to deplete within the horizon.
    val batchExhaustionDates: Map<Long, LocalDate> = emptyMap(),
)
