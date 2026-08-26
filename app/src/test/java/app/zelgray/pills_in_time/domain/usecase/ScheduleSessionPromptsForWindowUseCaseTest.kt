package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.AlarmKind
import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DailySession
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
import java.time.LocalTime

class ScheduleSessionPromptsForWindowUseCaseTest {

    private val useCase = ScheduleSessionPromptsForWindowUseCase()

    private fun period(id: Long = 1, start: LocalDate, times: List<IntakeTime>) = ScheduledIntakeWithTimes(
        scheduledIntake = ScheduledIntake(
            id = id,
            drugId = 1,
            startDate = start,
            endMode = EndMode.NONE,
            endDate = null,
            durationDays = null,
            cycleType = CycleType.DAILY,
            specificDays = null,
            customCycleText = null,
            createdAt = Instant.EPOCH,
        ),
        times = times,
    )

    private fun sessionTime(id: Long = 1, scheduledIntakeId: Long = 1) = IntakeTime(
        id = id,
        scheduledIntakeId = scheduledIntakeId,
        timeOfDay = LocalTime.MIDNIGHT,
        doseMode = DoseMode.UNITS,
        doseValue = 1.0,
        sessionDayStartFrom = LocalTime.of(8, 0),
        sessionIntervalHours = 1,
    )

    private fun fixedTime(id: Long = 1, scheduledIntakeId: Long = 1) =
        IntakeTime(id = id, scheduledIntakeId = scheduledIntakeId, timeOfDay = LocalTime.of(8, 0), doseMode = DoseMode.UNITS, doseValue = 1.0)

    @Test
    fun `session time with no DailySession yet produces a prompt spec`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime()
        val p = period(start = today.minusDays(5), times = listOf(t))
        val specs = useCase(listOf(p), emptyMap(), today, windowDays = 1)
        assertEquals(1, specs.size)
        assertEquals(AlarmKind.SESSION_START_PROMPT, specs.single().kind)
        assertEquals(LocalTime.of(8, 0), specs.single().timeOfDay)
    }

    @Test
    fun `session time already started today produces no prompt for that date`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime()
        val p = period(start = today.minusDays(5), times = listOf(t))
        val session = DailySession(scheduledIntakeId = p.scheduledIntake.id, date = today, startedAt = Instant.EPOCH)
        val specs = useCase(listOf(p), mapOf(today to listOf(session)), today, windowDays = 1)
        assertTrue(specs.isEmpty())
    }

    @Test
    fun `fixed-clock time never produces a prompt spec`() {
        val today = LocalDate.of(2026, 7, 17)
        val p = period(start = today.minusDays(5), times = listOf(fixedTime()))
        val specs = useCase(listOf(p), emptyMap(), today, windowDays = 1)
        assertTrue(specs.isEmpty())
    }

    @Test
    fun `period not yet active on a date produces no prompt for it`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime()
        val p = period(start = today.plusDays(1), times = listOf(t))
        val specs = useCase(listOf(p), emptyMap(), today, windowDays = 1)
        assertTrue(specs.isEmpty())
    }

    @Test
    fun `request code matches the shared dose-reminder scheme`() {
        val today = LocalDate.of(2026, 7, 17)
        val t = sessionTime(id = 7, scheduledIntakeId = 3)
        val p = period(id = 3, start = today.minusDays(5), times = listOf(t))
        val specs = useCase(listOf(p), emptyMap(), today, windowDays = 1)
        val expected = ScheduleAlarmsForWindowUseCase.computeRequestCode(3L, 7L, today)
        assertEquals(expected, specs.single().requestCode)
    }
}
