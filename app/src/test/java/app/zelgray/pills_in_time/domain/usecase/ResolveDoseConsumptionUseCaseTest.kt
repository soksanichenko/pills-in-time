package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.domain.model.DoseComboPiece
import app.zelgray.pills_in_time.domain.model.DoseConsumptionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ResolveDoseConsumptionUseCaseTest {

    private val useCase = ResolveDoseConsumptionUseCase(FindDoseCombosUseCase())

    private fun batch(id: Long, strength: Double, quantity: Double, addedAt: Instant) =
        DrugStockBatch(
            id = id,
            drugId = 1,
            quantity = quantity,
            strengthValue = strength,
            strengthUnit = StrengthUnit.MG,
            addedAt = addedAt,
        )

    @Test
    fun `STRENGTH mode with a pinned allocation decrements the matching batch`() {
        val batches = listOf(batch(id = 1, strength = 50.0, quantity = 10.0, addedAt = Instant.EPOCH))
        val result = useCase(
            doseMode = DoseMode.STRENGTH,
            doseValue = 100.0,
            doseAllocation = listOf(DoseComboPiece(strength = 50.0, count = 2.0)),
            batches = batches,
        )
        val resolved = result as DoseConsumptionResult.Resolved
        assertEquals(1, resolved.decrements.size)
        assertEquals(1L, resolved.decrements.single().batchId)
        assertEquals(2.0, resolved.decrements.single().quantity, 1e-9)
    }

    @Test
    fun `same-strength batches are consumed oldest addedAt first, splitting across them if needed`() {
        val older = batch(id = 1, strength = 50.0, quantity = 1.0, addedAt = Instant.EPOCH)
        val newer = batch(id = 2, strength = 50.0, quantity = 10.0, addedAt = Instant.EPOCH.plusSeconds(60))
        val result = useCase(
            doseMode = DoseMode.STRENGTH,
            doseValue = 150.0,
            doseAllocation = listOf(DoseComboPiece(strength = 50.0, count = 3.0)),
            batches = listOf(newer, older),
        )
        val resolved = result as DoseConsumptionResult.Resolved
        assertEquals(2, resolved.decrements.size)
        assertEquals(1.0, resolved.decrements.first { it.batchId == 1L }.quantity, 1e-9)
        assertEquals(2.0, resolved.decrements.first { it.batchId == 2L }.quantity, 1e-9)
    }

    @Test
    fun `multi-piece allocation resolves each piece against its own strength`() {
        val batches = listOf(
            batch(id = 1, strength = 50.0, quantity = 10.0, addedAt = Instant.EPOCH),
            batch(id = 2, strength = 25.0, quantity = 10.0, addedAt = Instant.EPOCH),
        )
        val result = useCase(
            doseMode = DoseMode.STRENGTH,
            doseValue = 125.0,
            doseAllocation = listOf(DoseComboPiece(50.0, 2.0), DoseComboPiece(25.0, 1.0)),
            batches = batches,
        )
        val resolved = result as DoseConsumptionResult.Resolved
        assertEquals(2.0, resolved.decrements.first { it.batchId == 1L }.quantity, 1e-9)
        assertEquals(1.0, resolved.decrements.first { it.batchId == 2L }.quantity, 1e-9)
    }

    @Test
    fun `insufficient matching-strength quantity is reported as Insufficient`() {
        val batches = listOf(batch(id = 1, strength = 50.0, quantity = 1.0, addedAt = Instant.EPOCH))
        val result = useCase(
            doseMode = DoseMode.STRENGTH,
            doseValue = 100.0,
            doseAllocation = listOf(DoseComboPiece(strength = 50.0, count = 2.0)),
            batches = batches,
        )
        assertEquals(DoseConsumptionResult.Insufficient, result)
    }

    @Test
    fun `null allocation falls back to the best-ranked combo currently available`() {
        val batches = listOf(batch(id = 1, strength = 20.0, quantity = 10.0, addedAt = Instant.EPOCH))
        val result = useCase(
            doseMode = DoseMode.STRENGTH,
            doseValue = 40.0,
            doseAllocation = null,
            batches = batches,
        )
        val resolved = result as DoseConsumptionResult.Resolved
        assertEquals(2.0, resolved.decrements.single().quantity, 1e-9)
    }

    @Test
    fun `null allocation with no batches on hand is Insufficient`() {
        val result = useCase(DoseMode.STRENGTH, doseValue = 40.0, doseAllocation = null, batches = emptyList())
        assertEquals(DoseConsumptionResult.Insufficient, result)
    }

    @Test
    fun `UNITS mode consumes oldest batch first regardless of strength`() {
        val older = batch(id = 1, strength = 50.0, quantity = 1.0, addedAt = Instant.EPOCH)
        val newer = batch(id = 2, strength = 25.0, quantity = 10.0, addedAt = Instant.EPOCH.plusSeconds(60))
        val result = useCase(
            doseMode = DoseMode.UNITS,
            doseValue = 3.0,
            doseAllocation = null,
            batches = listOf(newer, older),
        )
        val resolved = result as DoseConsumptionResult.Resolved
        assertEquals(1.0, resolved.decrements.first { it.batchId == 1L }.quantity, 1e-9)
        assertEquals(2.0, resolved.decrements.first { it.batchId == 2L }.quantity, 1e-9)
    }

    @Test
    fun `UNITS mode insufficient total quantity is reported as Insufficient`() {
        val batches = listOf(batch(id = 1, strength = 50.0, quantity = 1.0, addedAt = Instant.EPOCH))
        val result = useCase(DoseMode.UNITS, doseValue = 2.0, doseAllocation = null, batches = batches)
        assertEquals(DoseConsumptionResult.Insufficient, result)
    }

    @Test
    fun `batches with zero quantity are skipped`() {
        val depleted = batch(id = 1, strength = 50.0, quantity = 0.0, addedAt = Instant.EPOCH)
        val result = useCase(DoseMode.UNITS, doseValue = 1.0, doseAllocation = null, batches = listOf(depleted))
        assertEquals(DoseConsumptionResult.Insufficient, result)
    }
}
