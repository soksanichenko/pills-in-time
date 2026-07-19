package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FindDoseCombosUseCaseTest {

    private val useCase = FindDoseCombosUseCase()

    private fun batch(strength: Double, quantity: Double = 10.0, addedAt: Instant = Instant.EPOCH) =
        DrugStockBatch(
            drugId = 1,
            quantity = quantity,
            strengthValue = strength,
            strengthUnit = StrengthUnit.MG,
            addedAt = addedAt,
        )

    @Test
    fun `exact match with two of the same strength`() {
        val combos = useCase(listOf(batch(10.0)), targetDose = 20.0)
        assertEquals(1, combos.size)
        assertEquals(2.0, combos[0].totalPieces, 1e-9)
        assertEquals(10.0, combos[0].pieces.single().strength, 1e-9)
    }

    @Test
    fun `prefers fewer pieces - single 16mg beats 10+4mg for target 20`() {
        // spec example: target 20mg with batches 10mg, 4mg, 16mg -> "2x10mg" or "1x16mg+1x4mg"
        val combos = useCase(listOf(batch(10.0), batch(4.0), batch(16.0)), targetDose = 20.0)
        assertTrue(combos.isNotEmpty())
        // both combos are 2 pieces total (2x10 = 2 pieces; 16+4 = 2 pieces), so both may appear,
        // ranked by fewest total pieces then fewest distinct strengths -> 2x10mg (1 distinct) wins
        assertEquals(2.0, combos[0].totalPieces, 1e-9)
        assertEquals(1, combos[0].distinctStrengths)
    }

    @Test
    fun `half unit combo for target smaller than single batch`() {
        val combos = useCase(listOf(batch(4.0)), targetDose = 2.0)
        assertEquals(1, combos.size)
        assertEquals(0.5, combos[0].pieces.single().count, 1e-9)
    }

    @Test
    fun `no exact match returns empty list`() {
        val combos = useCase(listOf(batch(10.0)), targetDose = 3.0)
        assertTrue(combos.isEmpty())
    }

    @Test
    fun `batches with zero quantity are excluded from combo search`() {
        val combos = useCase(listOf(batch(10.0, quantity = 0.0)), targetDose = 10.0)
        assertTrue(combos.isEmpty())
    }

    @Test
    fun `returns at most two combos`() {
        val combos = useCase(
            listOf(batch(1.0), batch(2.0), batch(3.0), batch(4.0)),
            targetDose = 4.0,
        )
        assertTrue(combos.size <= 2)
    }

    @Test
    fun `zero or negative target dose yields no combos`() {
        assertTrue(useCase(listOf(batch(10.0)), targetDose = 0.0).isEmpty())
        assertTrue(useCase(listOf(batch(10.0)), targetDose = -5.0).isEmpty())
    }

    @Test
    fun `no batches yields no combos`() {
        assertTrue(useCase(emptyList(), targetDose = 10.0).isEmpty())
    }

    @Test
    fun `a batch with no strength is excluded rather than crashing`() {
        val noStrength = DrugStockBatch(
            drugId = 1,
            quantity = 10.0,
            strengthValue = null,
            strengthUnit = null,
            addedAt = Instant.EPOCH,
        )
        val combos = useCase(listOf(noStrength, batch(10.0)), targetDose = 10.0)
        assertEquals(1, combos.size)
        assertEquals(10.0, combos.single().pieces.single().strength, 1e-9)
    }

    @Test
    fun `real-world combo - 16mg and 4mg tablets for a 20mg dose picks one of each, not five 4mg`() {
        val combos = useCase(listOf(batch(16.0), batch(4.0)), targetDose = 20.0)
        assertEquals(2.0, combos[0].totalPieces, 1e-9)
        assertEquals(2, combos[0].distinctStrengths)
        assertEquals(setOf(16.0, 4.0), combos[0].pieces.map { it.strength }.toSet())
    }

    @Test
    fun `ties on pieces and distinct strengths favor the combo where the strongest tablet covers most of the dose`() {
        val combos = useCase(
            listOf(batch(19.0), batch(17.0), batch(3.0), batch(1.0)),
            targetDose = 20.0,
        )
        assertEquals(2.0, combos[0].totalPieces, 1e-9)
        assertEquals(2, combos[0].distinctStrengths)
        // 19+1 and 17+3 both tie at 2 pieces / 2 strengths; 19mg covers more of the dose (95%) than 17mg (85%)
        assertEquals(19.0, combos[0].pieces.maxByOrNull { it.strength }!!.strength, 1e-9)
    }
}
