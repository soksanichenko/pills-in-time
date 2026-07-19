package app.zelgray.pills_in_time.domain.model

data class DoseComboPiece(val strength: Double, val count: Double)

/** Encodes a fixed dose allocation for storage (Room column, backup JSON) as "strength:count;strength:count". */
fun List<DoseComboPiece>.encodeToCsv(): String = joinToString(";") { "${it.strength}:${it.count}" }

fun String?.decodeDoseAllocationCsv(): List<DoseComboPiece>? =
    this?.takeIf { it.isNotEmpty() }
        ?.split(";")
        ?.map { piece ->
            val (strength, count) = piece.split(":")
            DoseComboPiece(strength.toDouble(), count.toDouble())
        }

data class DoseCombo(val pieces: List<DoseComboPiece>) {
    val totalPieces: Double get() = pieces.sumOf { it.count }
    val distinctStrengths: Int get() = pieces.size

    /** How much of the target dose the strongest tablet in this combo accounts for — used as a
     * tie-break so that, among equally-simple combos, the one where the strongest tablet does
     * most of the work wins (fewer distinct small top-ups to keep track of). */
    val dominantContribution: Double get() = pieces.maxByOrNull { it.strength }?.let { it.strength * it.count } ?: 0.0
}
