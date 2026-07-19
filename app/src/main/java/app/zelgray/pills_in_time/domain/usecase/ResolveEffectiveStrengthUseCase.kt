package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import javax.inject.Inject

/**
 * Resolves a drug's "reference strength" as the most recently added stock
 * batch, using an explicit addedAt timestamp (not row/array order like the
 * prototype's getEffStrength, which was an accident of seed-data ordering).
 * Null if there are no batches, or the most recent one doesn't track strength
 * at all (a drug with no strength is capped at a single supply, so this is
 * effectively "does this drug track strength" for STRENGTH-mode availability).
 */
class ResolveEffectiveStrengthUseCase @Inject constructor() {

    operator fun invoke(batches: List<DrugStockBatch>): EffectiveStrength? {
        val latest = batches.maxByOrNull { it.addedAt } ?: return null
        val value = latest.strengthValue ?: return null
        val unit = latest.strengthUnit ?: return null
        return EffectiveStrength(value, unit)
    }
}
