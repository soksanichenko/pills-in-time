package app.zelgray.pills_in_time.domain.model

/**
 * Fixed preset palette a patient's accent color is chosen from — used as the
 * notification accent color and as the seed for the app-wide Material theme.
 */
object PatientColorPalette {
    const val DEFAULT_NAME = "Me"

    val colors: List<Int> = listOf(
        0xFFE53935.toInt(), // red
        0xFFFB8C00.toInt(), // orange
        0xFFFFB300.toInt(), // amber
        0xFF43A047.toInt(), // green
        0xFF00897B.toInt(), // teal
        0xFF1E88E5.toInt(), // blue
        0xFF3949AB.toInt(), // indigo
        0xFF8E24AA.toInt(), // purple
        0xFFD81B60.toInt(), // pink
    )

    fun colorForIndex(index: Int): Int = colors[index % colors.size]
}
