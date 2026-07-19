package app.zelgray.pills_in_time.domain.model

data class BatchDecrement(val batchId: Long, val quantity: Double)

sealed interface DoseConsumptionResult {
    data class Resolved(val decrements: List<BatchDecrement>) : DoseConsumptionResult
    data object Insufficient : DoseConsumptionResult
}
