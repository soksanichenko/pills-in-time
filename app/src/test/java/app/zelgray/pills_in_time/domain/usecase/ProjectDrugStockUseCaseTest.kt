package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.EffectiveStrength
import app.zelgray.pills_in_time.domain.model.StockOverallProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ProjectDrugStockUseCaseTest {

    private val useCase = ProjectDrugStockUseCase()

    private fun period(
        id: Long = 1,
        start: LocalDate,
        end: LocalDate? = null,
        cycle: CycleType = CycleType.DAILY,
        times: List<IntakeTime>,
    ) = ScheduledIntakeWithTimes(
        scheduledIntake = ScheduledIntake(
            id = id,
            drugId = 1,
            startDate = start,
            endMode = if (end != null) EndMode.DATE else EndMode.NONE,
            endDate = end,
            durationDays = null,
            cycleType = cycle,
            specificDays = null,
            customCycleText = null,
            createdAt = Instant.EPOCH,
        ),
        times = times,
    )

    private fun unitsTime(id: Long = 1, scheduledIntakeId: Long = 1, dose: Double = 1.0) = IntakeTime(
        id = id, scheduledIntakeId = scheduledIntakeId, timeOfDay = LocalTime.of(8, 0), doseMode = DoseMode.UNITS, doseValue = dose,
    )

    private fun strengthTime(id: Long = 1, scheduledIntakeId: Long = 1, doseMg: Double = 50.0) = IntakeTime(
        id = id, scheduledIntakeId = scheduledIntakeId, timeOfDay = LocalTime.of(8, 0), doseMode = DoseMode.STRENGTH, doseValue = doseMg,
    )

    @Test
    fun `no periods yields NoActivePeriods`() {
        val today = LocalDate.of(2026, 7, 17)
        val result = useCase(emptyList(), totalStock = 30.0, effectiveStrength = null, today = today)
        assertEquals(StockOverallProjection.NoActivePeriods, result.overall)
        assertTrue(result.periodProjections.isEmpty())
    }

    @Test
    fun `already ended period is excluded`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today.minusDays(10), end = today.minusDays(1), times = listOf(unitsTime()))
        val result = useCase(listOf(p), totalStock = 30.0, effectiveStrength = null, today = today)
        assertEquals(StockOverallProjection.NoActivePeriods, result.overall)
    }

    @Test
    fun `single bounded period consumes one unit per day down to remainder`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(9) // 10 days inclusive, 1 unit/day -> 10 consumed
        val p = period(start = today, end = end, times = listOf(unitsTime(dose = 1.0)))
        val result = useCase(listOf(p), totalStock = 30.0, effectiveStrength = null, today = today)

        val proj = result.periodProjections.getValue(1L)
        assertEquals(30.0, proj.atStart, 0.001)
        assertEquals(20.0, proj.atEnd!!, 0.001)
        assertEquals(StockOverallProjection.RemainingAfterAllPeriods(20.0), result.overall)
        assertFalse(proj.stockDepleted)
    }

    @Test
    fun `sequential periods carry remaining stock forward`() {
        val today = LocalDate.of(2026, 7, 17)
        val p1 = period(id = 1, start = today, end = today.plusDays(4), times = listOf(unitsTime(scheduledIntakeId = 1, dose = 2.0)))
        val p2 = period(id = 2, start = today.plusDays(5), end = today.plusDays(9), times = listOf(unitsTime(id = 2, scheduledIntakeId = 2, dose = 1.0)))
        val result = useCase(listOf(p1, p2), totalStock = 30.0, effectiveStrength = null, today = today)

        // p1: 5 days * 2/day = 10 consumed -> 20 remaining at its end
        val proj1 = result.periodProjections.getValue(1L)
        assertEquals(30.0, proj1.atStart, 0.001)
        assertEquals(20.0, proj1.atEnd!!, 0.001)

        // p2 starts right after p1 ends, at 20 remaining; 5 days * 1/day = 5 -> 15 remaining
        val proj2 = result.periodProjections.getValue(2L)
        assertEquals(20.0, proj2.atStart, 0.001)
        assertEquals(15.0, proj2.atEnd!!, 0.001)

        assertEquals(StockOverallProjection.RemainingAfterAllPeriods(15.0), result.overall)
    }

    @Test
    fun `open-ended period with sufficient stock is sufficient long term`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, end = null, times = listOf(unitsTime(dose = 0.001)))
        val result = useCase(listOf(p), totalStock = 30.0, effectiveStrength = null, today = today)

        assertEquals(StockOverallProjection.SufficientLongTerm, result.overall)
        val proj = result.periodProjections.getValue(1L)
        assertEquals(30.0, proj.atStart, 0.001)
        assertNull(proj.atEnd)
    }

    @Test
    fun `open-ended period that depletes stock reports runs-out date`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, end = null, times = listOf(unitsTime(dose = 5.0)))
        val result = useCase(listOf(p), totalStock = 12.0, effectiveStrength = null, today = today)

        // day0: 12->7, day1: 7->2, day2: 2-5<0 -> runs out on day2 (today+2)
        assertEquals(StockOverallProjection.RunsOutOn(today.plusDays(2)), result.overall)
    }

    @Test
    fun `strength mode dose is converted to units via effective strength`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(3) // 4 days
        val p = period(start = today, end = end, times = listOf(strengthTime(doseMg = 50.0)))
        val strength = EffectiveStrength(value = 25.0, unit = StrengthUnit.MG) // 1 tablet = 25mg -> dose = 2 tablets/day
        val result = useCase(listOf(p), totalStock = 20.0, effectiveStrength = strength, today = today)

        val proj = result.periodProjections.getValue(1L)
        // 4 days * 2 tablets/day = 8 consumed -> 12 remaining
        assertEquals(12.0, proj.atEnd!!, 0.001)
    }

    @Test
    fun `strength mode dose with no effective strength contributes zero consumption`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(3)
        val p = period(start = today, end = end, times = listOf(strengthTime(doseMg = 50.0)))
        val result = useCase(listOf(p), totalStock = 20.0, effectiveStrength = null, today = today)

        val proj = result.periodProjections.getValue(1L)
        assertEquals(20.0, proj.atEnd!!, 0.001)
    }

    @Test
    fun `stock never goes negative`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(4)
        val p = period(start = today, end = end, times = listOf(unitsTime(dose = 100.0)))
        val result = useCase(listOf(p), totalStock = 10.0, effectiveStrength = null, today = today)

        val proj = result.periodProjections.getValue(1L)
        assertEquals(0.0, proj.atEnd!!, 0.001)
    }

    @Test
    fun `period that empties stock by its end is flagged depleted`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(4)
        val p = period(start = today, end = end, times = listOf(unitsTime(dose = 100.0)))
        val result = useCase(listOf(p), totalStock = 10.0, effectiveStrength = null, today = today)

        assertTrue(result.periodProjections.getValue(1L).stockDepleted)
    }

    @Test
    fun `period starting with zero stock already on hand is flagged depleted`() {
        val today = LocalDate.of(2026, 7, 17)
        val p1 = period(id = 1, start = today, end = today.plusDays(4), times = listOf(unitsTime(scheduledIntakeId = 1, dose = 100.0)))
        val p2 = period(id = 2, start = today.plusDays(5), end = today.plusDays(9), times = listOf(unitsTime(id = 2, scheduledIntakeId = 2, dose = 1.0)))
        val result = useCase(listOf(p1, p2), totalStock = 10.0, effectiveStrength = null, today = today)

        assertEquals(0.0, result.periodProjections.getValue(2L).atStart, 0.001)
        assertTrue(result.periodProjections.getValue(2L).stockDepleted)
    }

    @Test
    fun `open-ended period that runs out is flagged depleted`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, end = null, times = listOf(unitsTime(dose = 5.0)))
        val result = useCase(listOf(p), totalStock = 12.0, effectiveStrength = null, today = today)

        assertTrue(result.periodProjections.getValue(1L).stockDepleted)
    }
}
