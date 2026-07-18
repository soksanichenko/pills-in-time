package app.zelgray.pills_in_time.util

object ValidationUtils {

    fun parsePositiveDouble(text: String): Double? {
        val value = parseLocaleAwareDouble(text) ?: return null
        return value.takeIf { it > 0 }
    }

    fun isNonBlank(text: String): Boolean = text.isNotBlank()
}
