package app.zelgray.pills_in_time.ui.drugs

import android.content.Context
import androidx.compose.runtime.Composable
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.usecase.FindDoseCombosUseCase
import app.zelgray.pills_in_time.ui.common.pluralUnitText
import app.zelgray.pills_in_time.ui.common.pluralUnitTextPlain
import app.zelgray.pills_in_time.ui.common.strengthUnitAbbreviation
import app.zelgray.pills_in_time.ui.common.strengthUnitAbbreviationPlain
import app.zelgray.pills_in_time.util.formatPlainNumber

private val findDoseCombos = FindDoseCombosUseCase()

/**
 * Dose text for a single intake: a plural unit count for UNITS-mode doses,
 * or — for STRENGTH-mode doses — the exact tablet combo from current stock
 * (falling back to a rough "≈N" estimate when no exact combo exists).
 * Shared by the Drug detail period list, the Home occurrence row/sheet, and
 * (via [doseTextPlain]) the dose reminder notification, so all three stay
 * consistent.
 */
@Composable
fun doseText(
    doseValue: Double,
    doseMode: DoseMode,
    drug: Drug,
    stockBatches: List<DrugStockBatch>,
    effectiveStrength: EffectiveStrength?,
): String {
    if (doseMode == DoseMode.UNITS) return pluralUnitText(drug.form, drug.customFormText, doseValue)
    val strength = effectiveStrength ?: return formatPlainNumber(doseValue)
    val strengthText = "${formatPlainNumber(doseValue)} ${strengthUnitAbbreviation(strength.unit)}"
    val bestCombo = findDoseCombos(stockBatches, doseValue).firstOrNull()
    if (bestCombo != null) {
        return "$strengthText (${formatCombo(bestCombo, " " + strengthUnitAbbreviation(strength.unit))})"
    }
    if (strength.value <= 0) return strengthText
    val tabletCount = doseValue / strength.value
    return "$strengthText (≈${pluralUnitText(drug.form, drug.customFormText, tabletCount)})"
}

/** Non-Composable variant of [doseText], for use outside Compose (Workers, receivers). */
fun doseTextPlain(
    context: Context,
    doseValue: Double,
    doseMode: DoseMode,
    drug: Drug,
    stockBatches: List<DrugStockBatch>,
    effectiveStrength: EffectiveStrength?,
): String {
    if (doseMode == DoseMode.UNITS) return pluralUnitTextPlain(context, drug.form, drug.customFormText, doseValue)
    val strength = effectiveStrength ?: return formatPlainNumber(doseValue)
    val strengthText = "${formatPlainNumber(doseValue)} ${strengthUnitAbbreviationPlain(context, strength.unit)}"
    val bestCombo = findDoseCombos(stockBatches, doseValue).firstOrNull()
    if (bestCombo != null) {
        return "$strengthText (${formatCombo(bestCombo, " " + strengthUnitAbbreviationPlain(context, strength.unit))})"
    }
    if (strength.value <= 0) return strengthText
    val tabletCount = doseValue / strength.value
    return "$strengthText (≈${pluralUnitTextPlain(context, drug.form, drug.customFormText, tabletCount)})"
}
