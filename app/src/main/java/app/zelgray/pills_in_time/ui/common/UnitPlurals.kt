package app.zelgray.pills_in_time.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.util.formatPlainNumber
import kotlin.math.roundToInt

/**
 * "N tablets"/"N capsules"/etc. — proper Android <plurals> (not the
 * naive English-only "+s" the prototype used). Dose values can be
 * fractional (half-tablet doses), but Android's plural category selection
 * only works on an Int, so the category is chosen from the rounded value
 * while the exact original number is still what's displayed via %1$s.
 * Custom (OTHER) forms are free text the user typed, so no plural form
 * exists for them — shown invariant, same as the drug's category label.
 */
@Composable
fun pluralUnitText(form: DrugForm, customFormText: String?, value: Double): String {
    val formatted = formatPlainNumber(value)
    val categoryCount = value.roundToInt().coerceAtLeast(0)
    return when (form) {
        DrugForm.TABLET -> pluralStringResource(R.plurals.unit_tablet, categoryCount, formatted)
        DrugForm.CAPSULE -> pluralStringResource(R.plurals.unit_capsule, categoryCount, formatted)
        DrugForm.DROPS -> pluralStringResource(R.plurals.unit_drops, categoryCount, formatted)
        DrugForm.ML -> pluralStringResource(R.plurals.unit_ml, categoryCount, formatted)
        DrugForm.OTHER -> "$formatted ${customFormText?.takeIf { it.isNotBlank() } ?: stringResource(R.string.drug_form_other)}"
    }
}

@Composable
fun strengthUnitAbbreviation(unit: StrengthUnit): String = when (unit) {
    StrengthUnit.MG -> stringResource(R.string.unit_mg)
    StrengthUnit.MCG -> stringResource(R.string.unit_mcg)
    StrengthUnit.IU -> stringResource(R.string.unit_iu)
}

/** Non-Composable variant of [pluralUnitText], for use outside Compose (Workers, receivers). */
fun pluralUnitTextPlain(context: Context, form: DrugForm, customFormText: String?, value: Double): String {
    val formatted = formatPlainNumber(value)
    val categoryCount = value.roundToInt().coerceAtLeast(0)
    return when (form) {
        DrugForm.TABLET -> context.resources.getQuantityString(R.plurals.unit_tablet, categoryCount, formatted)
        DrugForm.CAPSULE -> context.resources.getQuantityString(R.plurals.unit_capsule, categoryCount, formatted)
        DrugForm.DROPS -> context.resources.getQuantityString(R.plurals.unit_drops, categoryCount, formatted)
        DrugForm.ML -> context.resources.getQuantityString(R.plurals.unit_ml, categoryCount, formatted)
        DrugForm.OTHER -> "$formatted ${customFormText?.takeIf { it.isNotBlank() } ?: context.getString(R.string.drug_form_other)}"
    }
}

/** Non-Composable variant of [strengthUnitAbbreviation], for use outside Compose (Workers, receivers). */
fun strengthUnitAbbreviationPlain(context: Context, unit: StrengthUnit): String = when (unit) {
    StrengthUnit.MG -> context.getString(R.string.unit_mg)
    StrengthUnit.MCG -> context.getString(R.string.unit_mcg)
    StrengthUnit.IU -> context.getString(R.string.unit_iu)
}
