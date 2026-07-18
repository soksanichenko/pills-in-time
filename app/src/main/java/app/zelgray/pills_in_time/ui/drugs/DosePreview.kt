package app.zelgray.pills_in_time.ui.drugs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.domain.model.DoseCombo
import app.zelgray.pills_in_time.ui.common.strengthUnitAbbreviation
import app.zelgray.pills_in_time.util.formatPlainNumber

sealed interface DosePreview {
    data class ExactCombos(val combos: List<DoseCombo>, val unit: StrengthUnit) : DosePreview
    data class NoExactMatch(val fallbackUnitsText: String, val unit: StrengthUnit) : DosePreview
    data class UnitsToStrength(val computedStrengthText: String, val unit: StrengthUnit) : DosePreview
}

private fun formatCount(count: Double): String {
    val rounded = Math.round(count * 2) / 2.0
    val whole = Math.floor(rounded).toInt()
    val hasHalf = rounded - whole >= 0.5 - 1e-9 && rounded - whole < 1.0 - 1e-9
    return when {
        !hasHalf -> whole.toString()
        whole > 0 -> "$whole½"
        else -> "½"
    }
}

fun formatCombo(combo: DoseCombo, unitSuffix: String): String =
    combo.pieces.joinToString(" + ") { "${formatCount(it.count)} × ${formatPlainNumber(it.strength)}$unitSuffix" }

@Composable
fun formatDosePreview(preview: DosePreview): String {
    val unitSuffix = " " + strengthUnitAbbreviation(preview.unitOf())
    return when (preview) {
        is DosePreview.ExactCombos -> preview.combos.joinToString(" ${stringResource(R.string.or_word)} ") {
            formatCombo(it, unitSuffix)
        }
        is DosePreview.NoExactMatch ->
            "${preview.fallbackUnitsText} — ${stringResource(R.string.no_exact_combo)}"
        is DosePreview.UnitsToStrength -> "${preview.computedStrengthText}$unitSuffix"
    }
}

private fun DosePreview.unitOf(): StrengthUnit = when (this) {
    is DosePreview.ExactCombos -> unit
    is DosePreview.NoExactMatch -> unit
    is DosePreview.UnitsToStrength -> unit
}
