package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.domain.model.BatchDecrement
import app.zelgray.pills_in_time.domain.model.DoseComboPiece
import app.zelgray.pills_in_time.domain.model.DoseConsumptionResult
import javax.inject.Inject

/**
 * Resolves a dose (a fixed strength allocation, or a plain unit count) down
 * to concrete (batchId, quantity) decrements against currently on-hand
 * batches. Which specific batch covers each piece is never a user decision —
 * same-strength batches are fungible, so consumption always goes oldest
 * addedAt first (FIFO). Returns Insufficient if on-hand quantity can't cover
 * the dose, so the caller can block logging instead of deducting negative.
 */
class ResolveDoseConsumptionUseCase @Inject constructor(
    private val findDoseCombos: FindDoseCombosUseCase,
) {
    operator fun invoke(
        doseMode: DoseMode,
        doseValue: Double,
        doseAllocation: List<DoseComboPiece>?,
        batches: List<DrugStockBatch>,
    ): DoseConsumptionResult {
        if (doseMode == DoseMode.UNITS) {
            return resolveByFifo(doseValue, batches)
        }

        // Legacy STRENGTH-mode IntakeTimes saved before this feature existed
        // (or ones saved with no on-hand stock at the time) have no pinned
        // allocation — fall back to the best-ranked combo available right now.
        val pieces = doseAllocation
            ?: findDoseCombos(batches, doseValue).firstOrNull()?.pieces
            ?: return DoseConsumptionResult.Insufficient

        val decrements = mutableListOf<BatchDecrement>()
        for (piece in pieces) {
            var remaining = piece.count
            batches
                .filter { it.strengthValue == piece.strength && it.quantity > 0 }
                .sortedBy { it.addedAt }
                .forEach { batch ->
                    if (remaining <= 0) return@forEach
                    val take = minOf(batch.quantity, remaining)
                    decrements.add(BatchDecrement(batch.id, take))
                    remaining -= take
                }
            if (remaining > EPSILON) return DoseConsumptionResult.Insufficient
        }
        return DoseConsumptionResult.Resolved(decrements)
    }

    private fun resolveByFifo(doseValue: Double, batches: List<DrugStockBatch>): DoseConsumptionResult {
        val decrements = mutableListOf<BatchDecrement>()
        var remaining = doseValue
        batches
            .filter { it.quantity > 0 }
            .sortedBy { it.addedAt }
            .forEach { batch ->
                if (remaining <= 0) return@forEach
                val take = minOf(batch.quantity, remaining)
                decrements.add(BatchDecrement(batch.id, take))
                remaining -= take
            }
        if (remaining > EPSILON) return DoseConsumptionResult.Insufficient
        return DoseConsumptionResult.Resolved(decrements)
    }

    private companion object {
        const val EPSILON = 1e-6
    }
}
