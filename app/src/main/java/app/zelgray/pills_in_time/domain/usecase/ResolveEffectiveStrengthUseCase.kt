package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import javax.inject.Inject

/**
 * Resolves a drug's "reference strength" as the most recently added stock
 * batch, using an explicit addedAt timestamp (not row/array order like the
 * prototype's getEffStrength, which was an accident of seed-data ordering).
 */
class ResolveEffectiveStrengthUseCase @Inject constructor() {

    operator fun invoke(batches: List<DrugStockBatch>): EffectiveStrength? =
        batches.maxByOrNull { it.addedAt }?.let { EffectiveStrength(it.strengthValue, it.strengthUnit) }
}
