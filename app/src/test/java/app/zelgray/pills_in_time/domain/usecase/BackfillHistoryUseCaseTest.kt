package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class BackfillHistoryUseCaseTest {

    private val useCase = BackfillHistoryUseCase()

    private fun period(
        start: LocalDate,
        cycle: CycleType = CycleType.DAILY,
        intakeDays: Int? = null,
        breakDays: Int? = null,
    ) = ScheduledIntake(
        id = 1,
        drugId = 1,
        startDate = start,
        endMode = EndMode.NONE,
        endDate = null,
        durationDays = null,
        cycleType = cycle,
        specificDays = null,
        customCycleText = null,
        intakeDays = intakeDays,
        breakDays = breakDays,
        createdAt = Instant.EPOCH,
    )

    private fun fixedTime(id: Long = 1, dose: Double = 1.0) = IntakeTime(
        id = id, scheduledIntakeId = 1, timeOfDay = LocalTime.of(8, 0), doseMode = DoseMode.UNITS, doseValue = dose,
    )

    private fun sessionTime(id: Long = 2) = IntakeTime(
        id = id, scheduledIntakeId = 1, timeOfDay = LocalTime.MIDNIGHT, doseMode = DoseMode.UNITS, doseValue = 1.0,
        sessionIntervalHours = 1,
    )

    @Test
    fun `daily period generates one entry per past day, none for today`() {
        val today = LocalDate.of(2026, 9, 8)
        val start = today.minusDays(3)
        val entries = useCase(period(start), listOf(fixedTime()), until = today)

        assertEquals(3, entries.size)
        assertEquals(listOf(start, start.plusDays(1), start.plusDays(2)), entries.map { it.occurrenceDate })
    }

    @Test
    fun `start date equal to today generates nothing`() {
        val today = LocalDate.of(2026, 9, 8)
        val entries = useCase(period(today), listOf(fixedTime()), until = today)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `multiple fixed times per day each get their own entry`() {
        val today = LocalDate.of(2026, 9, 8)
        val start = today.minusDays(1)
        val entries = useCase(period(start), listOf(fixedTime(id = 1), fixedTime(id = 2)), until = today)
        assertEquals(2, entries.size)
        assertEquals(setOf(1L, 2L), entries.map { it.intakeTimeId }.toSet())
    }

    @Test
    fun `session times are excluded entirely`() {
        val today = LocalDate.of(2026, 9, 8)
        val start = today.minusDays(3)
        val entries = useCase(period(start), listOf(sessionTime()), until = today)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `only cycle-active days produce entries`() {
        val today = LocalDate.of(2026, 9, 8)
        val start = today.minusDays(4) // 4 days back: on,on,off,off pattern with 2-on/2-off
        val entries = useCase(period(start, cycle = CycleType.DAYS_ON_OFF, intakeDays = 2, breakDays = 2), listOf(fixedTime()), until = today)

        // Active days: start, start+1 (on), start+2, start+3 (off) -> only 2 active days before today.
        assertEquals(2, entries.size)
        assertEquals(listOf(start, start.plusDays(1)), entries.map { it.occurrenceDate })
    }

    @Test
    fun `far past start date is capped instead of generating years of entries`() {
        val today = LocalDate.of(2026, 9, 8)
        val start = today.minusYears(5)
        val entries = useCase(period(start), listOf(fixedTime()), until = today)

        assertEquals(730, entries.size)
    }
}
