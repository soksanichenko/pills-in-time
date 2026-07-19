package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ResolveEffectiveStrengthUseCaseTest {

    private val useCase = ResolveEffectiveStrengthUseCase()

    private fun batch(strength: Double?, addedAt: Instant) =
        DrugStockBatch(
            drugId = 1,
            quantity = 10.0,
            strengthValue = strength,
            strengthUnit = strength?.let { StrengthUnit.MG },
            addedAt = addedAt,
        )

    @Test
    fun `no batches yields null`() {
        assertNull(useCase(emptyList()))
    }

    @Test
    fun `picks the batch with the latest addedAt, not array order`() {
        val older = batch(6.0, Instant.ofEpochSecond(100))
        val newer = batch(5.0, Instant.ofEpochSecond(200))
        // deliberately out of chronological order in the input list
        val result = useCase(listOf(newer, older))
        assertEquals(5.0, result!!.value, 1e-9)
    }

    @Test
    fun `most recent batch with no strength yields null (drug doesn't track strength)`() {
        val result = useCase(listOf(batch(null, Instant.ofEpochSecond(100))))
        assertNull(result)
    }
}
