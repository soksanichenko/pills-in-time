package app.zelgray.pills_in_time.domain.model

import java.time.LocalDate

data class LowStockAlert(
    val batchId: Long,
    val drugId: Long,
    // Null for a units-before reminder whose batch has no active/projected
    // consumption forecast — the alert still fires on quantity alone.
    val runOutDate: LocalDate?,
)
