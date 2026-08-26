package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DailySession
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeSource
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.entity.SnoozedOccurrence
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class GenerateOccurrencesForDateUseCaseTest {

    private val useCase = GenerateOccurrencesForDateUseCase()

    private fun period(
        id: Long = 1,
        drugId: Long = 1,
        start: LocalDate,
        end: LocalDate? = null,
        cycle: CycleType = CycleType.DAILY,
        specificDays: Set<DayOfWeek>? = null,
        intakeDays: Int? = null,
        breakDays: Int? = null,
        times: List<IntakeTime>,
    ) = ScheduledIntakeWithTimes(
        scheduledIntake = ScheduledIntake(
            id = id,
            drugId = drugId,
            startDate = start,
            endMode = if (end != null) EndMode.DATE else EndMode.NONE,
            endDate = end,
            durationDays = null,
            cycleType = cycle,
            specificDays = specificDays,
            customCycleText = null,
            intakeDays = intakeDays,
            breakDays = breakDays,
            createdAt = Instant.EPOCH,
        ),
        times = times,
    )

    private fun time(id: Long = 1, scheduledIntakeId: Long = 1, time: LocalTime = LocalTime.of(8, 0)) =
        IntakeTime(id = id, scheduledIntakeId = scheduledIntakeId, timeOfDay = time, doseMode = DoseMode.UNITS, doseValue = 1.0)

    @Test
    fun `daily cycle is active every day within range`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today.minusDays(5), times = listOf(time()))
        val result = useCase(listOf(p), emptyList(), today, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertEquals(1, result.size)
    }

    @Test
    fun `date before start is not active`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today.plusDays(1), times = listOf(time()))
        val result = useCase(listOf(p), emptyList(), today, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `date after end is not active`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today.minusDays(10), end = today.minusDays(1), times = listOf(time()))
        val result = useCase(listOf(p), emptyList(), today, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `every other day parity anchored to period start`() {
        val start = LocalDate.of(2026, 7, 10)
        val p = period(start = start, cycle = CycleType.EVERY_OTHER_DAY, times = listOf(time()))
        // start + 0 -> active, start + 1 -> inactive, start + 2 -> active
        val active = useCase(listOf(p), emptyList(), start, start, LocalDateTime.of(start, LocalTime.of(9, 0)))
        val inactive = useCase(listOf(p), emptyList(), start.plusDays(1), start.plusDays(1), LocalDateTime.of(start.plusDays(1), LocalTime.of(9, 0)))
        val activeAgain = useCase(listOf(p), emptyList(), start.plusDays(2), start.plusDays(2), LocalDateTime.of(start.plusDays(2), LocalTime.of(9, 0)))
        assertEquals(1, active.size)
        assertTrue(inactive.isEmpty())
        assertEquals(1, activeAgain.size)
    }

    @Test
    fun `specific days filters by day of week`() {
        val start = LocalDate.of(2026, 7, 1)
        val monday = LocalDate.of(2026, 7, 6) // a Monday
        val tuesday = monday.plusDays(1)
        val p = period(
            start = start,
            cycle = CycleType.SPECIFIC_DAYS,
            specificDays = setOf(DayOfWeek.MONDAY),
            times = listOf(time()),
        )
        val onMonday = useCase(listOf(p), emptyList(), monday, monday, LocalDateTime.of(monday, LocalTime.of(9, 0)))
        val onTuesday = useCase(listOf(p), emptyList(), tuesday, tuesday, LocalDateTime.of(tuesday, LocalTime.of(9, 0)))
        assertEquals(1, onMonday.size)
        assertTrue(onTuesday.isEmpty())
    }

    @Test
    fun `days-on-off cycle is active during the intake phase`() {
        val start = LocalDate.of(2026, 7, 1)
        val p = period(start = start, cycle = CycleType.DAYS_ON_OFF, intakeDays = 5, breakDays = 2, times = listOf(time()))
        // days 0-4 (start..start+4) are the 5 "on" days of the first cycle
        listOf(0L, 1L, 4L).forEach { offset ->
            val result = useCase(listOf(p), emptyList(), start.plusDays(offset), start, LocalDateTime.of(start, LocalTime.of(9, 0)))
            assertEquals("offset $offset should be active", 1, result.size)
        }
    }

    @Test
    fun `days-on-off cycle is inactive during the break phase`() {
        val start = LocalDate.of(2026, 7, 1)
        val p = period(start = start, cycle = CycleType.DAYS_ON_OFF, intakeDays = 5, breakDays = 2, times = listOf(time()))
        // days 5-6 are the 2 "off" days of the first cycle
        listOf(5L, 6L).forEach { offset ->
            val result = useCase(listOf(p), emptyList(), start.plusDays(offset), start, LocalDateTime.of(start, LocalTime.of(9, 0)))
            assertTrue("offset $offset should be inactive", result.isEmpty())
        }
    }

    @Test
    fun `days-on-off cycle repeats across multiple cycles`() {
        val start = LocalDate.of(2026, 7, 1)
        val p = period(start = start, cycle = CycleType.DAYS_ON_OFF, intakeDays = 3, breakDays = 1, times = listOf(time()))
        // cycle length 4: on,on,on,off,on,on,on,off,...
        val secondCycleOn = useCase(listOf(p), emptyList(), start.plusDays(4), start, LocalDateTime.of(start, LocalTime.of(9, 0)))
        val secondCycleOff = useCase(listOf(p), emptyList(), start.plusDays(7), start, LocalDateTime.of(start, LocalTime.of(9, 0)))
        assertEquals(1, secondCycleOn.size)
        assertTrue(secondCycleOff.isEmpty())
    }

    @Test
    fun `days-on-off cycle with missing counts is inactive`() {
        val start = LocalDate.of(2026, 7, 1)
        val p = period(start = start, cycle = CycleType.DAYS_ON_OFF, intakeDays = null, breakDays = null, times = listOf(time()))
        val result = useCase(listOf(p), emptyList(), start, start, LocalDateTime.of(start, LocalTime.of(9, 0)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `custom cycle behaves like daily`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today.minusDays(3), cycle = CycleType.CUSTOM, times = listOf(time()))
        val result = useCase(listOf(p), emptyList(), today, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertEquals(1, result.size)
    }

    @Test
    fun `today occurrence before scheduled time is upcoming`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, times = listOf(time(time = LocalTime.of(20, 0))))
        val result = useCase(listOf(p), emptyList(), today, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertEquals(OccurrenceStatus.UPCOMING, result.single().status)
    }

    @Test
    fun `today occurrence past scheduled time with no log is overdue`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, times = listOf(time(time = LocalTime.of(8, 0))))
        val result = useCase(listOf(p), emptyList(), today, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertEquals(OccurrenceStatus.OVERDUE, result.single().status)
    }

    @Test
    fun `past date with no log is missed, never fabricated as taken`() {
        val today = LocalDate.of(2026, 7, 17)
        val yesterday = today.minusDays(1)
        val p = period(start = yesterday.minusDays(5), times = listOf(time()))
        val result = useCase(listOf(p), emptyList(), yesterday, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertEquals(OccurrenceStatus.MISSED, result.single().status)
    }

    @Test
    fun `future date with no log is upcoming`() {
        val today = LocalDate.of(2026, 7, 17)
        val tomorrow = today.plusDays(1)
        val p = period(start = today.minusDays(5), times = listOf(time()))
        val result = useCase(listOf(p), emptyList(), tomorrow, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertEquals(OccurrenceStatus.UPCOMING, result.single().status)
    }

    @Test
    fun `existing log overrides computed status`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = time()
        val p = period(start = today, times = listOf(t))
        val log = IntakeLog(
            drugId = 1,
            scheduledIntakeId = 1,
            intakeTimeId = t.id,
            occurrenceDate = today,
            status = IntakeStatus.SKIPPED,
            actualDateTime = Instant.EPOCH,
            actualDoseValue = 1.0,
            actualDoseMode = DoseMode.UNITS,
            source = IntakeSource.REMINDER,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val result = useCase(listOf(p), listOf(log), today, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertEquals(OccurrenceStatus.SKIPPED, result.single().status)
    }

    @Test
    fun `grace period delays overdue transition`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today, times = listOf(time(time = LocalTime.of(8, 0))))
        val now = LocalDateTime.of(today, LocalTime.of(8, 10))
        val withoutGrace = useCase(listOf(p), emptyList(), today, today, now, graceMinutes = 0)
        val withGrace = useCase(listOf(p), emptyList(), today, today, now, graceMinutes = 15)
        assertEquals(OccurrenceStatus.OVERDUE, withoutGrace.single().status)
        assertEquals(OccurrenceStatus.UPCOMING, withGrace.single().status)
    }

    @Test
    fun `snoozed overdue occurrence is postponed until the snooze delay elapses`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = time()
        val p = period(start = today, times = listOf(t))
        val now = LocalDateTime.of(today, LocalTime.of(9, 0))
        val snoozed = SnoozedOccurrence(
            scheduledIntakeId = p.scheduledIntake.id,
            intakeTimeId = t.id,
            occurrenceDate = today,
            snoozedUntil = now.plusMinutes(15).atZone(java.time.ZoneId.systemDefault()).toInstant(),
        )
        val result = useCase(listOf(p), emptyList(), today, today, now, snoozed = listOf(snoozed))
        assertEquals(OccurrenceStatus.POSTPONED, result.single().status)
    }

    @Test
    fun `postponed occurrence reverts to overdue once the snooze delay elapses`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = time()
        val p = period(start = today, times = listOf(t))
        val now = LocalDateTime.of(today, LocalTime.of(9, 0))
        val snoozed = SnoozedOccurrence(
            scheduledIntakeId = p.scheduledIntake.id,
            intakeTimeId = t.id,
            occurrenceDate = today,
            snoozedUntil = now.minusMinutes(1).atZone(java.time.ZoneId.systemDefault()).toInstant(),
        )
        val result = useCase(listOf(p), emptyList(), today, today, now, snoozed = listOf(snoozed))
        assertEquals(OccurrenceStatus.OVERDUE, result.single().status)
    }

    @Test
    fun `multiple times in a period each produce an occurrence sorted by time`() {
        val today = LocalDate.of(2026, 7, 17)
        val morning = time(id = 1, time = LocalTime.of(8, 0))
        val evening = time(id = 2, time = LocalTime.of(20, 0))
        val p = period(start = today, times = listOf(evening, morning))
        val result = useCase(listOf(p), emptyList(), today, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertEquals(2, result.size)
        assertEquals(LocalTime.of(8, 0), result[0].timeOfDay)
        assertEquals(LocalTime.of(20, 0), result[1].timeOfDay)
    }

    private fun sessionTime(
        id: Long = 10,
        scheduledIntakeId: Long = 1,
        intervalHours: Int? = 1,
        timesPerDay: Int? = null,
    ) = IntakeTime(
        id = id,
        scheduledIntakeId = scheduledIntakeId,
        timeOfDay = LocalTime.MIDNIGHT,
        doseMode = DoseMode.UNITS,
        doseValue = 1.0,
        sessionDayStartFrom = LocalTime.of(8, 0),
        sessionIntervalHours = intervalHours,
        sessionTimesPerDay = timesPerDay,
    )

    private fun sessionLog(t: IntakeTime, date: LocalDate, seq: Int, status: IntakeStatus = IntakeStatus.TAKEN) = IntakeLog(
        drugId = 1,
        scheduledIntakeId = t.scheduledIntakeId,
        intakeTimeId = t.id,
        occurrenceDate = date,
        status = status,
        actualDateTime = Instant.EPOCH,
        actualDoseValue = 1.0,
        actualDoseMode = DoseMode.UNITS,
        source = IntakeSource.REMINDER,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        sessionSeq = seq,
    )

    private fun instant(dateTime: LocalDateTime) = dateTime.atZone(ZoneId.systemDefault()).toInstant()

    @Test
    fun `session time with no DailySession yet produces no occurrences`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime()
        val p = period(start = today, times = listOf(t))
        val result = useCase(listOf(p), emptyList(), today, today, LocalDateTime.of(today, LocalTime.of(9, 0)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `hourly session with no doses logged yet has one pending occurrence due at start`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime(intervalHours = 1)
        val p = period(start = today, times = listOf(t))
        val startedAt = instant(LocalDateTime.of(today, LocalTime.of(8, 0)))
        val session = DailySession(scheduledIntakeId = t.scheduledIntakeId, date = today, startedAt = startedAt)
        val now = LocalDateTime.of(today, LocalTime.of(8, 5))
        val result = useCase(listOf(p), emptyList(), today, today, now, sessions = listOf(session))
        assertEquals(1, result.size)
        assertEquals(1, result.single().sessionSeq)
        assertEquals(LocalTime.of(8, 0), result.single().timeOfDay)
        assertEquals(OccurrenceStatus.OVERDUE, result.single().status)
    }

    @Test
    fun `hourly session logged dose becomes taken and next pending dose is due one interval later`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime(intervalHours = 1)
        val p = period(start = today, times = listOf(t))
        val startedAt = instant(LocalDateTime.of(today, LocalTime.of(8, 0)))
        val session = DailySession(scheduledIntakeId = t.scheduledIntakeId, date = today, startedAt = startedAt)
        val logs = listOf(sessionLog(t, today, seq = 1))
        val now = LocalDateTime.of(today, LocalTime.of(8, 30))
        val result = useCase(listOf(p), logs, today, today, now, sessions = listOf(session))
        assertEquals(2, result.size)
        assertEquals(OccurrenceStatus.TAKEN, result[0].status)
        assertEquals(1, result[0].sessionSeq)
        assertEquals(2, result[1].sessionSeq)
        assertEquals(LocalTime.of(9, 0), result[1].timeOfDay)
        assertEquals(OccurrenceStatus.UPCOMING, result[1].status)
    }

    @Test
    fun `count-per-day session has one pending occurrence with no fixed time until target reached`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime(intervalHours = null, timesPerDay = 3)
        val p = period(start = today, times = listOf(t))
        val startedAt = instant(LocalDateTime.of(today, LocalTime.of(8, 0)))
        val session = DailySession(scheduledIntakeId = t.scheduledIntakeId, date = today, startedAt = startedAt)
        val now = LocalDateTime.of(today, LocalTime.of(9, 0))
        val result = useCase(listOf(p), emptyList(), today, today, now, sessions = listOf(session))
        assertEquals(1, result.size)
        assertEquals(null, result.single().timeOfDay)
        assertEquals(OccurrenceStatus.OVERDUE, result.single().status)
    }

    @Test
    fun `count-per-day session with target reached has no pending occurrence`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime(intervalHours = null, timesPerDay = 2)
        val p = period(start = today, times = listOf(t))
        val startedAt = instant(LocalDateTime.of(today, LocalTime.of(8, 0)))
        val session = DailySession(scheduledIntakeId = t.scheduledIntakeId, date = today, startedAt = startedAt)
        val logs = listOf(sessionLog(t, today, seq = 1), sessionLog(t, today, seq = 2))
        val now = LocalDateTime.of(today, LocalTime.of(12, 0))
        val result = useCase(listOf(p), logs, today, today, now, sessions = listOf(session))
        assertEquals(2, result.size)
        assertTrue(result.all { it.status == OccurrenceStatus.TAKEN })
    }

    @Test
    fun `ended session has no pending occurrence even below its target`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime(intervalHours = null, timesPerDay = 3)
        val p = period(start = today, times = listOf(t))
        val startedAt = instant(LocalDateTime.of(today, LocalTime.of(8, 0)))
        val endedAt = instant(LocalDateTime.of(today, LocalTime.of(10, 0)))
        val session = DailySession(scheduledIntakeId = t.scheduledIntakeId, date = today, startedAt = startedAt, endedAt = endedAt)
        val logs = listOf(sessionLog(t, today, seq = 1))
        val now = LocalDateTime.of(today, LocalTime.of(12, 0))
        val result = useCase(listOf(p), logs, today, today, now, sessions = listOf(session))
        assertEquals(1, result.size)
        assertEquals(OccurrenceStatus.TAKEN, result.single().status)
    }
}
