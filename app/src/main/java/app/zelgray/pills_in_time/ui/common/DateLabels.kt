package app.zelgray.pills_in_time.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.zelgray.pills_in_time.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.minusDays(1) -> stringResource(R.string.day_yesterday)
    today.plusDays(1) -> stringResource(R.string.day_tomorrow)
    else -> localizedDate(date, includeYear = false)
}

/** Locale-aware date, e.g. "17 Jul 2026" (en) or "17 лип. 2026" (uk) — day/month order and month name follow the device locale. */
@Composable
fun localizedDate(date: LocalDate, includeYear: Boolean = true): String {
    val locale = Locale.getDefault()
    val skeleton = if (includeYear) "d MMM yyyy" else "d MMM"
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** Non-Composable equivalent of [localizedDate], for use from Workers/receivers that build notification text outside Compose. */
fun localizedDatePlain(date: LocalDate): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "d MMM yyyy")
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** Locale-aware date + time, e.g. "17 Jul 2026, 14:05". */
@Composable
fun localizedDateTime(instant: Instant): String {
    val locale = Locale.getDefault()
    val skeleton = "d MMM yyyy HH:mm"
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, locale))
}
