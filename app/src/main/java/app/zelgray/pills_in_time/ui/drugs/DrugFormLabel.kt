package app.zelgray.pills_in_time.ui.drugs

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.ui.common.pluralUnitText
import app.zelgray.pills_in_time.util.formatPlainNumber

@Composable
fun drugFormLabel(form: DrugForm, customFormText: String?): String {
    return when (form) {
        DrugForm.TABLET -> stringResource(R.string.drug_form_tablet)
        DrugForm.CAPSULE -> stringResource(R.string.drug_form_capsule)
        DrugForm.DROPS -> stringResource(R.string.drug_form_drops)
        DrugForm.ML -> stringResource(R.string.drug_form_ml)
        DrugForm.AMPOULE -> stringResource(R.string.drug_form_ampoule)
        DrugForm.SACHET -> stringResource(R.string.drug_form_sachet)
        DrugForm.OTHER -> customFormText?.takeIf { it.isNotBlank() } ?: stringResource(R.string.drug_form_other)
    }
}

@Composable
fun drugSubtitle(drug: Drug): String = drugFormLabel(drug.form, drug.customFormText)

/** "20 tablets @ 5 mg", or just "20 tablets" for a batch that doesn't track strength. */
@Composable
fun stockRowSummaryText(batch: DrugStockBatch, drug: Drug): String {
    val quantityText = pluralUnitText(drug.form, drug.customFormText, batch.quantity)
    val strengthValue = batch.strengthValue
    val strengthUnit = batch.strengthUnit
    return if (strengthValue != null && strengthUnit != null) {
        stringResource(R.string.stock_row_summary, quantityText, formatPlainNumber(strengthValue), strengthUnit.name.lowercase())
    } else {
        quantityText
    }
}

/** Non-Composable variant for use outside Compose (Workers, receivers). */
fun drugFormLabelPlain(context: Context, form: DrugForm, customFormText: String?): String {
    return when (form) {
        DrugForm.TABLET -> context.getString(R.string.drug_form_tablet)
        DrugForm.CAPSULE -> context.getString(R.string.drug_form_capsule)
        DrugForm.DROPS -> context.getString(R.string.drug_form_drops)
        DrugForm.ML -> context.getString(R.string.drug_form_ml)
        DrugForm.AMPOULE -> context.getString(R.string.drug_form_ampoule)
        DrugForm.SACHET -> context.getString(R.string.drug_form_sachet)
        DrugForm.OTHER -> customFormText?.takeIf { it.isNotBlank() } ?: context.getString(R.string.drug_form_other)
    }
}
