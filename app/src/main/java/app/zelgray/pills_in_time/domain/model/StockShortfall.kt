package app.zelgray.pills_in_time.domain.model

import app.zelgray.pills_in_time.data.local.entity.StrengthUnit

/**
 * One still-missing amount to complete a course: a specific on-hand strength
 * (strengthValue/strengthUnit non-null), or a generic unit count when the
 * dose can't be attributed to a strength (UNITS-mode doses, or a STRENGTH-mode
 * dose with no combo available at all).
 */
data class ShortfallItem(
    val strengthValue: Double?,
    val strengthUnit: StrengthUnit?,
    val quantity: Double,
)

data class StockShortfall(val items: List<ShortfallItem>) {
    val isEmpty: Boolean get() = items.isEmpty()
}
