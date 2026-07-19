package app.zelgray.pills_in_time.ui.drugs

import androidx.compose.runtime.Composable
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.domain.model.ShortfallItem
import app.zelgray.pills_in_time.domain.model.StockShortfall
import app.zelgray.pills_in_time.ui.common.pluralUnitText
import app.zelgray.pills_in_time.ui.common.strengthUnitAbbreviation
import app.zelgray.pills_in_time.util.formatPlainNumber

/** "3 × 16 mg" for a specific on-hand strength, or a plain unit count ("5 tablets") when the shortfall can't be attributed to one. */
@Composable
private fun shortfallItemText(item: ShortfallItem, drug: Drug): String {
    val strengthValue = item.strengthValue
    val strengthUnit = item.strengthUnit
    return if (strengthValue != null && strengthUnit != null) {
        "${formatPlainNumber(item.quantity)} × ${formatPlainNumber(strengthValue)} ${strengthUnitAbbreviation(strengthUnit)}"
    } else {
        pluralUnitText(drug.form, drug.customFormText, item.quantity)
    }
}

@Composable
fun shortfallText(shortfall: StockShortfall, drug: Drug): String {
    val lines = shortfall.items.map { shortfallItemText(it, drug) }
    return lines.joinToString("\n")
}
