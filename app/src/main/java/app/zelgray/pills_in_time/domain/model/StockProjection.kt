package app.zelgray.pills_in_time.domain.model

import java.time.LocalDate

data class PeriodStockProjection(
    val atStart: Double,
    val atEnd: Double?,
    val stockDepleted: Boolean,
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
