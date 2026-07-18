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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
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
                        doseText(time.doseValue, time.doseMode, drug, stockBatches, effectiveStrength),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
fun stockOverallProjectionText(overall: StockOverallProjection, drug: Drug): String = when (overall) {
    is StockOverallProjection.RemainingAfterAllPeriods -> stringResource(
        R.string.stock_projection_remaining,
        pluralUnitText(drug.form, drug.customFormText, overall.amount),
    )
    is StockOverallProjection.RunsOutOn -> stringResource(R.string.stock_projection_runs_out, localizedDate(overall.date))
    StockOverallProjection.SufficientLongTerm -> stringResource(R.string.stock_projection_sufficient)
    StockOverallProjection.NoActivePeriods -> stringResource(R.string.stock_projection_none)
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

fun isPeriodActiveOn(period: ScheduledIntake, date: LocalDate): Boolean {
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
                ChronoUnit.DAYS.between(period.startDate, date) % (on + off) < on
            }
        }
    }
}
