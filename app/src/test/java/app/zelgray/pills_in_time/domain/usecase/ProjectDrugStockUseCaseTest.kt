package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
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

    private val useCase = ProjectDrugStockUseCase(ResolveDoseConsumptionUseCase(FindDoseCombosUseCase()))

    private fun batch(id: Long = 1, quantity: Double, strength: Double = 1.0) = DrugStockBatch(
        id = id,
        drugId = 1,
        quantity = quantity,
        strengthValue = strength,
        strengthUnit = StrengthUnit.MG,
        addedAt = Instant.EPOCH,
    )

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
        val result = useCase(emptyList(), batches = listOf(batch(quantity = 30.0)), today = today)
        assertEquals(StockOverallProjection.NoActivePeriods, result.overall)
        assertTrue(result.periodProjections.isEmpty())
    }

    @Test
    fun `already ended period is excluded`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today.minusDays(10), end = today.minusDays(1), times = listOf(unitsTime()))
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 30.0)), today = today)
        assertEquals(StockOverallProjection.NoActivePeriods, result.overall)
    }

    @Test
    fun `single bounded period consumes one unit per day down to remainder`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(9) // 10 days inclusive, 1 unit/day -> 10 consumed
        val p = period(start = today, end = end, times = listOf(unitsTime(dose = 1.0)))
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 30.0)), today = today)

        val proj = result.periodProjections.getValue(1L)
        assertEquals(30.0, proj.atStart, 0.001)
        assertEquals(20.0, proj.atEnd!!, 0.001)
        assertEquals(StockOverallProjection.RemainingAfterAllPeriods(20.0), result.overall)
        assertFalse(proj.stockDepleted)
    }

    @Test
    fun `period whose stock exactly covers its last dose is not flagged depleted`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(9) // 10 days inclusive, 1 unit/day -> exactly 10 consumed
        val p = period(start = today, end = end, times = listOf(unitsTime(dose = 1.0)))
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 10.0)), today = today)

        val proj = result.periodProjections.getValue(1L)
        assertEquals(0.0, proj.atEnd!!, 0.001)
        assertFalse(proj.stockDepleted)
    }

    @Test
    fun `sequential periods carry remaining stock forward`() {
        val today = LocalDate.of(2026, 7, 17)
        val p1 = period(id = 1, start = today, end = today.plusDays(4), times = listOf(unitsTime(scheduledIntakeId = 1, dose = 2.0)))
        val p2 = period(id = 2, start = today.plusDays(5), end = today.plusDays(9), times = listOf(unitsTime(id = 2, scheduledIntakeId = 2, dose = 1.0)))
        val result = useCase(listOf(p1, p2), batches = listOf(batch(quantity = 30.0)), today = today)

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
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 30.0)), today = today)

        assertEquals(StockOverallProjection.SufficientLongTerm, result.overall)
        val proj = result.periodProjections.getValue(1L)
        assertEquals(30.0, proj.atStart, 0.001)
        assertNull(proj.atEnd)
    }

    @Test
    fun `open-ended period that depletes stock reports runs-out date`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, end = null, times = listOf(unitsTime(dose = 5.0)))
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 12.0)), today = today)

        // day0: 12->7, day1: 7->2, day2: needs 5 but only 2 left -> insufficient, runs out on day2 (today+2)
        assertEquals(StockOverallProjection.RunsOutOn(today.plusDays(2)), result.overall)
    }

    @Test
    fun `strength mode dose is resolved against the matching on-hand strength`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(3) // 4 days
        val p = period(start = today, end = end, times = listOf(strengthTime(doseMg = 50.0)))
        // 1 tablet = 25mg -> 50mg dose = 2 tablets/day
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 20.0, strength = 25.0)), today = today)

        val proj = result.periodProjections.getValue(1L)
        // 4 days * 2 tablets/day = 8 consumed -> 12 remaining
        assertEquals(12.0, proj.atEnd!!, 0.001)
    }

    @Test
    fun `strength mode dose with no stock on hand at all consumes nothing`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(3)
        val p = period(start = today, end = end, times = listOf(strengthTime(doseMg = 50.0)))
        val result = useCase(listOf(p), batches = emptyList(), today = today)

        val proj = result.periodProjections.getValue(1L)
        assertEquals(0.0, proj.atStart, 0.001)
        assertEquals(0.0, proj.atEnd!!, 0.001)
        assertTrue(proj.stockDepleted)
    }

    @Test
    fun `insufficient stock blocks consumption entirely rather than draining the remainder`() {
        // A dose that can never be covered (100/day from 10 on hand) is never
        // logged for real, so the projection shouldn't drain it either — it
        // mirrors the app's actual behavior of blocking the log when stock
        // can't cover it.
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(4)
        val p = period(start = today, end = end, times = listOf(unitsTime(dose = 100.0)))
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 10.0)), today = today)

        val proj = result.periodProjections.getValue(1L)
        assertEquals(10.0, proj.atEnd!!, 0.001)
    }

    @Test
    fun `period that can never be covered is flagged depleted even though nothing was consumed`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(4)
        val p = period(start = today, end = end, times = listOf(unitsTime(dose = 100.0)))
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 10.0)), today = today)

        assertTrue(result.periodProjections.getValue(1L).stockDepleted)
    }

    @Test
    fun `an unrelated period's impossible dose does not phantom-deplete a later, achievable one`() {
        val today = LocalDate.of(2026, 7, 17)
        val p1 = period(id = 1, start = today, end = today.plusDays(4), times = listOf(unitsTime(scheduledIntakeId = 1, dose = 100.0)))
        val p2 = period(id = 2, start = today.plusDays(5), end = today.plusDays(9), times = listOf(unitsTime(id = 2, scheduledIntakeId = 2, dose = 1.0)))
        val result = useCase(listOf(p1, p2), batches = listOf(batch(quantity = 10.0)), today = today)

        // p1's dose is never resolvable, so it never actually consumes anything —
        // the 10 units are still there for p2's much smaller, achievable dose.
        val proj2 = result.periodProjections.getValue(2L)
        assertEquals(10.0, proj2.atStart, 0.001)
        assertEquals(5.0, proj2.atEnd!!, 0.001)
        assertFalse(proj2.stockDepleted)
    }

    @Test
    fun `a later period hitting the same already-exhausted stock is flagged depleted too`() {
        // p1: 3/day from 10 -> 10,7,4,1, then day3 needs 3 but only 1 left ->
        // insufficient, stuck at 1 for the rest of p1's run (days 0-9).
        // p2 starts right after p1 ends, needs 1/day: day10 consumes the
        // stuck 1 -> 0, then day11 needs 1 but none left -> insufficient on
        // p2's own days, so p2 must be flagged depleted too, not just p1.
        val today = LocalDate.of(2026, 7, 17)
        val p1 = period(id = 1, start = today, end = today.plusDays(9), times = listOf(unitsTime(scheduledIntakeId = 1, dose = 3.0)))
        val p2 = period(id = 2, start = today.plusDays(10), end = today.plusDays(14), times = listOf(unitsTime(id = 2, scheduledIntakeId = 2, dose = 1.0)))
        val result = useCase(listOf(p1, p2), batches = listOf(batch(quantity = 10.0)), today = today)

        assertTrue(result.periodProjections.getValue(1L).stockDepleted)
        assertTrue(result.periodProjections.getValue(2L).stockDepleted)
    }

    @Test
    fun `open-ended period that runs out is flagged depleted`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, end = null, times = listOf(unitsTime(dose = 5.0)))
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 12.0)), today = today)

        assertTrue(result.periodProjections.getValue(1L).stockDepleted)
    }

    @Test
    fun `a batch stuck insufficient above zero is still reported as exhausted`() {
        // 3/day from 10 on hand: 10->7->4->1, then day3 needs 3 but only 1 is
        // left -> insufficient, so it consumes nothing and stays stuck at 1
        // forever. The batch never reaches literal zero, but it's genuinely
        // unable to cover the remaining course, so it must still show up in
        // batchExhaustionDates rather than reading as "sufficient".
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(9)
        val p = period(start = today, end = end, times = listOf(unitsTime(dose = 3.0)))
        val result = useCase(listOf(p), batches = listOf(batch(id = 1, quantity = 10.0)), today = today)

        assertEquals(today.plusDays(3), result.batchExhaustionDates[1L])
    }

    @Test
    fun `atStart and atEnd break down per batch when the period isn't pinned`() {
        val today = LocalDate.of(2026, 7, 17)
        val end = today.plusDays(6) // 7 days
        val p = period(start = today, end = end, times = listOf(strengthTime(doseMg = 16.0)))
        val batch4mg = batch(id = 1, quantity = 16.5, strength = 4.0)
        val batch16mg = batch(id = 2, quantity = 7.0, strength = 16.0)
        val result = useCase(listOf(p), batches = listOf(batch4mg, batch16mg), today = today)

        val proj = result.periodProjections.getValue(1L)
        assertEquals(16.5, proj.atStartByBatch[1L]!!, 0.001)
        assertEquals(7.0, proj.atStartByBatch[2L]!!, 0.001)
        // 1x 16mg tablet/day for 7 days -> 16mg batch drained, 4mg untouched.
        assertEquals(16.5, proj.atEndByBatch!![1L]!!, 0.001)
        assertEquals(0.0, proj.atEndByBatch!![2L]!!, 0.001)
    }

    @Test
    fun `per-batch breakdown is empty for a single-batch drug`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, end = today.plusDays(3), times = listOf(unitsTime(dose = 1.0)))
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 10.0)), today = today)

        assertTrue(result.periodProjections.getValue(1L).atStartByBatch.isEmpty())
    }

    private fun sessionTime(
        id: Long = 1,
        scheduledIntakeId: Long = 1,
        dayStartFrom: LocalTime = LocalTime.of(8, 0),
        intervalHours: Int? = null,
        timesPerDay: Int? = null,
    ) = IntakeTime(
        id = id,
        scheduledIntakeId = scheduledIntakeId,
        timeOfDay = LocalTime.MIDNIGHT,
        doseMode = DoseMode.UNITS,
        doseValue = 1.0,
        sessionDayStartFrom = dayStartFrom,
        sessionIntervalHours = intervalHours,
        sessionTimesPerDay = timesPerDay,
    )

    @Test
    fun `count-per-day session consumes exactly its target count each active day`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, end = today, times = listOf(sessionTime(timesPerDay = 3)))
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 10.0)), today = today)
        // One active day (start == end) at 3 doses/day -> 10 - 3 = 7.
        assertEquals(7.0, result.periodProjections.getValue(1L).atEnd!!, 0.001)
    }

    @Test
    fun `hourly session consumes many doses a day, not just one`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(
            start = today,
            end = today,
            times = listOf(sessionTime(dayStartFrom = LocalTime.of(8, 0), intervalHours = 1)),
        )
        val result = useCase(listOf(p), batches = listOf(batch(quantity = 100.0)), today = today)
        // 08:00 to midnight is 16 hours, every 1h -> 16 doses that single day.
        assertEquals(84.0, result.periodProjections.getValue(1L).atEnd!!, 0.001)
    }
}
