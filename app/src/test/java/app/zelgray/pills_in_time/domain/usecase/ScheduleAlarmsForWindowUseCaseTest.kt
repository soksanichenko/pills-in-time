package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ScheduleAlarmsForWindowUseCaseTest {

    private val useCase = ScheduleAlarmsForWindowUseCase(GenerateOccurrencesForDateUseCase())

    private fun period(
        id: Long = 1,
        start: LocalDate,
        end: LocalDate? = null,
        times: List<IntakeTime>,
    ) = ScheduledIntakeWithTimes(
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

    private fun time(id: Long = 1, time: LocalTime) =
        IntakeTime(id = id, scheduledIntakeId = 1, timeOfDay = time, doseMode = DoseMode.UNITS, doseValue = 1.0)

    @Test
    fun `only upcoming occurrences produce alarm specs`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, times = listOf(time(time = LocalTime.of(8, 0)), time(id = 2, time = LocalTime.of(20, 0))))
        val now = LocalDateTime.of(today, LocalTime.of(9, 0)) // 8:00 already passed -> overdue, 20:00 still upcoming
        val specs = useCase(listOf(p), emptyMap(), today, now, windowDays = 1)
        assertEquals(1, specs.size)
        assertEquals(LocalTime.of(20, 0), specs.single().timeOfDay)
    }

    @Test
    fun `window covers multiple future days`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today.minusDays(5), times = listOf(time(time = LocalTime.of(8, 0))))
        val now = LocalDateTime.of(today, LocalTime.of(7, 0))
        val specs = useCase(listOf(p), emptyMap(), today, now, windowDays = 3)
        // today (upcoming, before 8am), tomorrow, day after -> 3 specs
        assertEquals(3, specs.size)
        assertEquals(setOf(today, today.plusDays(1), today.plusDays(2)), specs.map { it.occurrenceDate }.toSet())
    }

    @Test
    fun `request code is deterministic and stable`() {
        val a = ScheduleAlarmsForWindowUseCase.computeRequestCode(1L, 2L, LocalDate.of(2026, 7, 17))
        val b = ScheduleAlarmsForWindowUseCase.computeRequestCode(1L, 2L, LocalDate.of(2026, 7, 17))
        val c = ScheduleAlarmsForWindowUseCase.computeRequestCode(1L, 2L, LocalDate.of(2026, 7, 18))
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun `trigger time reflects the occurrence date and time in the given zone`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, times = listOf(time(time = LocalTime.of(20, 0))))
        val now = LocalDateTime.of(today, LocalTime.of(9, 0))
        val zone = ZoneId.of("UTC")
        val specs = useCase(listOf(p), emptyMap(), today, now, windowDays = 1, zoneId = zone)
        val expectedMillis = LocalDateTime.of(today, LocalTime.of(20, 0)).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedMillis, specs.single().triggerAtEpochMilli)
    }

    @Test
    fun `no periods yields no alarm specs`() {
        val today = LocalDate.of(2026, 7, 17)
        val specs = useCase(emptyList(), emptyMap(), today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertTrue(specs.isEmpty())
    }
}
