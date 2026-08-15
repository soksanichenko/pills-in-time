package app.zelgray.pills_in_time.domain.model

data class BatchDecrement(val batchId: Long, val quantity: Double)

sealed interface DoseConsumptionResult {
    data class Resolved(val decrements: List<BatchDecrement>) : DoseConsumptionResult
    // shortBatchIds: which on-hand batches were actually implicated in the
    // shortfall (e.g. every batch of the strength that ran short) — lets a
    // forward projection mark those batches as exhausted even when their
    // quantity gets stuck just above zero rather than reaching it exactly
    // (an atomic dose that can never fully resolve never drains the rest).
    data class Insufficient(val shortBatchIds: Set<Long> = emptySet()) : DoseConsumptionResult
}
