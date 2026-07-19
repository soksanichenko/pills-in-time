package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ComputeStockShortfallUseCaseTest {

    private val useCase = ComputeStockShortfallUseCase(FindDoseCombosUseCase())
    private val today = LocalDate.of(2026, 7, 17)

    private fun batch(id: Long = 1, quantity: Double, strength: Double? = 1.0) = DrugStockBatch(
        id = id,
        drugId = 1,
        quantity = quantity,
        strengthValue = strength,
        strengthUnit = strength?.let { StrengthUnit.MG },
        addedAt = Instant.EPOCH,
    )

    private fun period(id: Long = 1, start: LocalDate, end: LocalDate?, times: List<IntakeTime>) = ScheduledIntakeWithTimes(
        scheduledIntake = ScheduledIntake(
            id = id,
            drugId = 1,
            startDate = start,
            endMode = if (end != null) EndMode.DATE else EndMode.NONE,
            endDate = end,
            durationDays = null,
            cycleType = CycleType.DAILY,
            specificDays = null,
            customCycleText = null,
            createdAt = Instant.EPOCH,
        ),
        times = times,
    )

    private fun unitsTime(id: Long = 1, scheduledIntakeId: Long = 1, dose: Double = 1.0) = IntakeTime(
        id = id, scheduledIntakeId = scheduledIntakeId, timeOfDay = LocalTime.of(8, 0), doseMode = DoseMode.UNITS, doseValue = dose,
    )

    private fun strengthTime(id: Long = 1, scheduledIntakeId: Long = 1, doseMg: Double = 10.0) = IntakeTime(
        id = id, scheduledIntakeId = scheduledIntakeId, timeOfDay = LocalTime.of(8, 0), doseMode = DoseMode.STRENGTH, doseValue = doseMg,
    )

    @Test
    fun `sufficient stock for the whole window yields no shortfall`() {
        val shortfall = useCase(
            listOf(period(start = today, end = today.plusDays(4), times = listOf(unitsTime(dose = 1.0)))),
            windowEnd = today.plusDays(4),
            batches = listOf(batch(quantity = 10.0)),
            today = today,
        )
        assertTrue(shortfall.isEmpty)
    }

    @Test
    fun `units-mode shortfall reports the missing plain quantity`() {
        // 5 days of 1/day = 5 needed, only 2 on hand -> 3 short
        val shortfall = useCase(
            listOf(period(start = today, end = today.plusDays(4), times = listOf(unitsTime(dose = 1.0)))),
            windowEnd = today.plusDays(4),
            batches = listOf(batch(quantity = 2.0, strength = null)),
            today = today,
        )
        assertEquals(1, shortfall.items.size)
        val item = shortfall.items.single()
        assertEquals(null, item.strengthValue)
        assertEquals(3.0, item.quantity, 1e-9)
    }

    @Test
    fun `strength-mode shortfall reports which strength and how much is missing`() {
        // 3 days of 10mg/day = 30mg needed = 3 tablets of 10mg, only 1 on hand -> 2 short
        val shortfall = useCase(
            listOf(period(start = today, end = today.plusDays(2), times = listOf(strengthTime(doseMg = 10.0)))),
            windowEnd = today.plusDays(2),
            batches = listOf(batch(quantity = 1.0, strength = 10.0)),
            today = today,
        )
        assertEquals(1, shortfall.items.size)
        val item = shortfall.items.single()
        assertEquals(10.0, item.strengthValue!!, 1e-9)
        assertEquals(2.0, item.quantity, 1e-9)
    }

    @Test
    fun `demand from other currently active periods also counts against the same shared stock`() {
        val target = period(id = 1, start = today, end = today.plusDays(1), times = listOf(unitsTime(id = 1, scheduledIntakeId = 1, dose = 1.0)))
        val other = period(id = 2, start = today, end = today.plusDays(1), times = listOf(unitsTime(id = 2, scheduledIntakeId = 2, dose = 1.0)))
        // Each needs 2 total (2 days x 1/day) = 4 combined, only 3 on hand -> 1 short
        val shortfall = useCase(
            listOf(target, other),
            windowEnd = today.plusDays(1),
            batches = listOf(batch(quantity = 3.0, strength = null)),
            today = today,
        )
        assertEquals(1.0, shortfall.items.single().quantity, 1e-9)
    }

    @Test
    fun `a period that already ended before today does not contribute demand`() {
        val ended = period(start = today.minusDays(10), end = today.minusDays(1), times = listOf(unitsTime(dose = 100.0)))
        val shortfall = useCase(
            listOf(ended),
            windowEnd = today.plusDays(4),
            batches = listOf(batch(quantity = 1.0, strength = null)),
            today = today,
        )
        assertTrue(shortfall.isEmpty)
    }

    @Test
    fun `window end before today yields no shortfall`() {
        val shortfall = useCase(
            listOf(period(start = today.minusDays(5), end = today.minusDays(1), times = listOf(unitsTime(dose = 1.0)))),
            windowEnd = today.minusDays(1),
            batches = emptyList(),
            today = today,
        )
        assertTrue(shortfall.isEmpty)
    }
}
