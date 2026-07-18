package app.zelgray.pills_in_time.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberFormatTest {

    @Test
    fun `formatPlainNumber always uses a dot regardless of default locale`() {
        assertEquals("19.5", formatPlainNumber(19.5))
        assertEquals("3.33", formatPlainNumber(10.0 / 3.0))
        assertEquals("20", formatPlainNumber(20.0))
    }

    @Test
    fun `formatPlainNumber output round-trips through parseLocaleAwareDouble`() {
        val value = 19.5
        assertEquals(value, parseLocaleAwareDouble(formatPlainNumber(value))!!, 1e-9)
    }

    @Test
    fun `parseLocaleAwareDouble accepts a comma decimal separator`() {
        assertEquals(19.5, parseLocaleAwareDouble("19,5")!!, 1e-9)
    }

    @Test
    fun `parseLocaleAwareDouble accepts a dot decimal separator`() {
        assertEquals(19.5, parseLocaleAwareDouble("19.5")!!, 1e-9)
    }

    @Test
    fun `parseLocaleAwareDouble rejects garbage`() {
        assertNull(parseLocaleAwareDouble("abc"))
        assertNull(parseLocaleAwareDouble(""))
    }

    @Test
    fun `ValidationUtils parsePositiveDouble accepts a comma decimal separator`() {
        assertEquals(19.5, ValidationUtils.parsePositiveDouble("19,5")!!, 1e-9)
    }
}
