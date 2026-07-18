package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.domain.model.DoseCombo
import app.zelgray.pills_in_time.domain.model.DoseComboPiece
import javax.inject.Inject
import kotlin.math.abs

/**
 * Ports the prototype's exact dose-to-supply combo algorithm (spec 4.4):
 * finds combinations of whole/half units across distinct batch strengths that
 * sum exactly to the target dose, ranks by (fewest total pieces, fewest
 * distinct strengths, then — as a tie-break — the combo where the strongest
 * tablet covers the largest share of the dose), and returns the top 2.
 *
 * Deviation from the prototype: batches with quantity <= 0 are filtered out
 * before extracting strengths, since suggesting a combo from stock that's
 * actually depleted defeats the point of tracking supply on hand.
 */
class FindDoseCombosUseCase @Inject constructor() {

    operator fun invoke(batches: List<DrugStockBatch>, targetDose: Double): List<DoseCombo> {
        val strengths = batches
            .filter { it.quantity > 0 }
            .map { it.strengthValue }
            .distinct()
            .sortedDescending()
            .take(MAX_DISTINCT_STRENGTHS)

        if (strengths.isEmpty() || targetDose <= 0) return emptyList()

        val steps = (0..12).map { it / 2.0 }
        val results = mutableListOf<List<DoseComboPiece>>()

        fun rec(idx: Int, remaining: Double, chosen: MutableList<DoseComboPiece>) {
            if (abs(remaining) < EPSILON) {
                results.add(chosen.filter { it.count > 0 }.toList())
                return
            }
            if (idx >= strengths.size || remaining < -EPSILON) return
            if (results.size > MAX_RESULTS) return

            for (step in steps) {
                if (step * strengths[idx] > remaining + EPSILON) break
                chosen.add(DoseComboPiece(strengths[idx], step))
                rec(idx + 1, remaining - step * strengths[idx], chosen)
                chosen.removeAt(chosen.size - 1)
            }
        }

        rec(0, targetDose, mutableListOf())

        val seen = mutableSetOf<String>()
        val uniqueResults = mutableListOf<List<DoseComboPiece>>()
        results.forEach { combo ->
            if (combo.isEmpty()) return@forEach
            val key = combo.map { "${it.strength}:${it.count}" }.sorted().joinToString("|")
            if (seen.add(key)) uniqueResults.add(combo)
        }

        return uniqueResults
            .map { DoseCombo(it) }
            .sortedWith(
                compareBy<DoseCombo> { it.totalPieces }
                    .thenBy { it.distinctStrengths }
                    .thenByDescending { it.dominantContribution },
            )
            .take(MAX_RETURNED)
    }

    private companion object {
        const val MAX_DISTINCT_STRENGTHS = 4
        const val MAX_RESULTS = 30
        const val MAX_RETURNED = 2
        const val EPSILON = 1e-6
    }
}
