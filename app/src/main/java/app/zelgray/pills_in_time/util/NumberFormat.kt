package app.zelgray.pills_in_time.util

import java.util.Locale

/**
 * Whole numbers show without a decimal point; fractional values (e.g.
 * half-tablet doses, or a mg/strength division result) are rounded to 2
 * decimals and trimmed, avoiding ugly floating-point artifacts like
 * "3.3000000000000003" that a plain Double.toString() would show.
 *
 * Always uses Locale.ROOT (".") regardless of device locale: this text is
 * re-parsed by parseLocaleAwareDouble, which is locale-independent (Kotlin's
 * toDoubleOrNull only ever accepts '.'), so formatting with the device's
 * locale (e.g. "19,5" under uk/ru) would silently make the value unsavable
 * the next time the same field is edited.
 */
fun formatPlainNumber(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
    }

/**
 * Parses user-typed decimal text. Accepts both '.' and ',' as the decimal
 * separator since some device keyboards (uk/ru locale) insert a comma for
 * the decimal key even though toDoubleOrNull only understands '.'.
 */
fun parseLocaleAwareDouble(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
