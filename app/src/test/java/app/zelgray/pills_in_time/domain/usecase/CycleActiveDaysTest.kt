package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CycleActiveDaysTest {

    @Test
    fun `daily cycle - N occurrences is N calendar days later`() {
        val start = LocalDate.of(2026, 7, 17)
        val end = computeEndDateForOccurrences(start, CycleType.DAILY, null, null, null, occurrences = 8)
        assertEquals(start.plusDays(7), end)
    }

    @Test
    fun `specific one day per week - N occurrences spans N weeks, not N days`() {
        val start = LocalDate.of(2026, 7, 20) // a Monday
        val end = computeEndDateForOccurrences(
            start,
            CycleType.SPECIFIC_DAYS,
            setOf(DayOfWeek.MONDAY),
            null,
            null,
            occurrences = 8,
        )
        // 8 Mondays: start + 7 weeks
        assertEquals(start.plusWeeks(7), end)
    }

    @Test
    fun `every other day - N occurrences spans roughly 2N days`() {
        val start = LocalDate.of(2026, 7, 17)
        val end = computeEndDateForOccurrences(start, CycleType.EVERY_OTHER_DAY, null, null, null, occurrences = 5)
        // active on offsets 0,2,4,6,8 -> 5th occurrence is 8 days after start
        assertEquals(start.plusDays(8), end)
    }

    @Test
    fun `days-on-off - N occurrences counts only the on-days`() {
        val start = LocalDate.of(2026, 7, 17)
        // 3 on, 4 off -> active offsets 0,1,2, 7,8,9, 14,15,16 ...
        val end = computeEndDateForOccurrences(start, CycleType.DAYS_ON_OFF, null, intakeDays = 3, breakDays = 4, occurrences = 4)
        // 4th active day is offset 7 (first day of the second on-block)
        assertEquals(start.plusDays(7), end)
    }

    @Test
    fun `zero or negative occurrences yields null`() {
        val start = LocalDate.of(2026, 7, 17)
        assertNull(computeEndDateForOccurrences(start, CycleType.DAILY, null, null, null, occurrences = 0))
        assertNull(computeEndDateForOccurrences(start, CycleType.DAILY, null, null, null, occurrences = -1))
    }

    @Test
    fun `misconfigured days-on-off that never activates yields null rather than looping forever`() {
        val start = LocalDate.of(2026, 7, 17)
        val end = computeEndDateForOccurrences(start, CycleType.DAYS_ON_OFF, null, intakeDays = 0, breakDays = 0, occurrences = 1)
        assertNull(end)
    }
}
