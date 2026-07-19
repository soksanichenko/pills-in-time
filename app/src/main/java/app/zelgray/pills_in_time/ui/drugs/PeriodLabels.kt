package app.zelgray.pills_in_time.ui.drugs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.model.PeriodStockProjection
import app.zelgray.pills_in_time.domain.model.StockOverallProjection
import app.zelgray.pills_in_time.ui.common.localizedDate
import app.zelgray.pills_in_time.ui.common.pluralUnitText
import app.zelgray.pills_in_time.ui.common.strengthUnitAbbreviation
import app.zelgray.pills_in_time.util.formatPlainNumber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun periodDateRangeLabel(period: ScheduledIntake): String {
    val start = localizedDate(period.startDate)
    val end = period.endDate?.let { localizedDate(it) }
    return if (end != null) "$start – $end" else stringResource(R.string.period_ongoing_from, start)
}

@Composable
fun periodCycleLabel(period: ScheduledIntake): String = when (period.cycleType) {
    CycleType.DAILY -> stringResource(R.string.cycle_daily)
    CycleType.EVERY_OTHER_DAY -> stringResource(R.string.cycle_every_other_day)
    CycleType.SPECIFIC_DAYS -> {
        val locale = Locale.getDefault()
        val days = period.specificDays.orEmpty()
            .sortedBy { it.value }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        stringResource(R.string.cycle_specific_days) + (if (days.isNotEmpty()) ": $days" else "")
    }
    CycleType.DAYS_ON_OFF -> stringResource(
        R.string.cycle_days_on_off_label,
        period.intakeDays ?: 0,
        period.breakDays ?: 0,
    )
    CycleType.CUSTOM -> period.customCycleText?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cycle_custom)
}

/** Bulleted list of a period's intake times — one line per time, dose shown
 * as a plural unit count, or (for strength-mode doses) the mg/mcg/IU amount
 * plus the exact combination of tablets from stock that adds up to it (the
 * same combo-finder used in the Add/Edit Period preview), falling back to a
 * rough tablet-count estimate if no exact combo exists. */
@Composable
fun PeriodTimesList(
    periodWithTimes: ScheduledIntakeWithTimes,
    drug: Drug,
    stockBatches: List<DrugStockBatch>,
    effectiveStrength: EffectiveStrength?,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    Column(modifier = modifier) {
        for (time in periodWithTimes.times.sortedBy { it.timeOfDay }) {
            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                Text(text = "•  ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${time.timeOfDay.format(timeFormatter)} — " +
                        doseText(time.doseValue, time.doseMode, drug, stockBatches, effectiveStrength, time.doseAllocation),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
fun stockOverallProjectionText(
    overall: StockOverallProjection,
    drug: Drug,
    batches: List<DrugStockBatch>,
    batchExhaustionDates: Map<Long, LocalDate>,
): String {
    val base = when (overall) {
        is StockOverallProjection.RemainingAfterAllPeriods -> stringResource(
            R.string.stock_projection_remaining,
            pluralUnitText(drug.form, drug.customFormText, overall.amount),
        )
        is StockOverallProjection.RunsOutOn -> stringResource(R.string.stock_projection_runs_out, localizedDate(overall.date))
        StockOverallProjection.SufficientLongTerm -> stringResource(R.string.stock_projection_sufficient)
        StockOverallProjection.NoActivePeriods -> stringResource(R.string.stock_projection_none)
    }
    val breakdown = perBatchExhaustionText(batches, batchExhaustionDates)
    return if (breakdown != null) "$base ($breakdown)" else base
}

/**
 * "4 mg: sufficient, 16 mg: 23 Jul" — each on-hand batch's own projected
 * exhaustion date (or a "sufficient" placeholder if it isn't projected to
 * run out within the horizon), shown only when there's more than one supply
 * — with a single batch the overall figure already says everything there is
 * to say.
 */
@Composable
fun perBatchExhaustionText(batches: List<DrugStockBatch>, batchExhaustionDates: Map<Long, LocalDate>): String? {
    if (batches.size <= 1) return null
    val sufficientLabel = stringResource(R.string.batch_sufficient_short)
    // A strength-less batch can only ever be a drug's sole supply (see
    // AddEditStockViewModel), so every batch reaching this point (more than
    // one on hand) is guaranteed to have a strength — the fallback below is
    // just to satisfy the compiler, not an expected real case.
    val entries = batches.map { batch ->
        val strengthLabel = batch.strengthValue?.let { value ->
            batch.strengthUnit?.let { unit -> "${formatPlainNumber(value)} ${strengthUnitAbbreviation(unit)}" }
        }
            ?: formatPlainNumber(batch.quantity)
        val statusText = batchExhaustionDates[batch.id]?.let { localizedDate(it) } ?: sufficientLabel
        "$strengthLabel: $statusText"
    }
    return entries.joinToString(", ")
}

@Composable
fun periodStockAtStartText(projection: PeriodStockProjection, drug: Drug): String = stringResource(
    R.string.period_stock_at_start,
    pluralUnitText(drug.form, drug.customFormText, projection.atStart),
)

@Composable
fun periodStockAtEndText(projection: PeriodStockProjection, drug: Drug): String? = projection.atEnd?.let {
    stringResource(R.string.period_stock_at_end, pluralUnitText(drug.form, drug.customFormText, it))
}
