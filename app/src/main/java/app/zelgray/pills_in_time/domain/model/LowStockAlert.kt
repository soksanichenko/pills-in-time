package app.zelgray.pills_in_time.domain.model

import java.time.LocalDate

data class LowStockAlert(
    val batchId: Long,
    val drugId: Long,
    val runOutDate: LocalDate,
)
