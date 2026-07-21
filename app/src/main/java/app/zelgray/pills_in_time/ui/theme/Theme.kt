package app.zelgray.pills_in_time.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import app.zelgray.pills_in_time.domain.model.PatientColorPalette
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

@Composable
fun MedTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val patientColor by themeViewModel.currentPatientColor.collectAsState()
    val seedColor = patientColor ?: PatientColorPalette.colorForIndex(0)
    val colorScheme = remember(seedColor, darkTheme) { colorSchemeFromSeed(seedColor, darkTheme) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/**
 * Builds a full Material 3 ColorScheme from an arbitrary seed color (a
 * patient's chosen accent), the same HCT/tonal-palette algorithm Android
 * itself uses for wallpaper-based dynamic color — just seeded from the
 * patient's color instead of the wallpaper.
 */
private fun colorSchemeFromSeed(seedColor: Int, isDark: Boolean): ColorScheme {
    val scheme: DynamicScheme = SchemeTonalSpot(Hct.fromInt(seedColor), isDark, 0.0)
    val colors = MaterialDynamicColors()
    fun color(role: com.google.android.material.color.utilities.DynamicColor) = Color(role.getArgb(scheme))

    return ColorScheme(
        primary = color(colors.primary()),
        onPrimary = color(colors.onPrimary()),
        primaryContainer = color(colors.primaryContainer()),
        onPrimaryContainer = color(colors.onPrimaryContainer()),
        inversePrimary = color(colors.inversePrimary()),
        secondary = color(colors.secondary()),
        onSecondary = color(colors.onSecondary()),
        secondaryContainer = color(colors.secondaryContainer()),
        onSecondaryContainer = color(colors.onSecondaryContainer()),
        tertiary = color(colors.tertiary()),
        onTertiary = color(colors.onTertiary()),
        tertiaryContainer = color(colors.tertiaryContainer()),
        onTertiaryContainer = color(colors.onTertiaryContainer()),
        background = color(colors.background()),
        onBackground = color(colors.onBackground()),
        surface = color(colors.surface()),
        onSurface = color(colors.onSurface()),
        surfaceVariant = color(colors.surfaceVariant()),
        onSurfaceVariant = color(colors.onSurfaceVariant()),
        surfaceTint = color(colors.surfaceTint()),
        inverseSurface = color(colors.inverseSurface()),
        inverseOnSurface = color(colors.inverseOnSurface()),
        error = color(colors.error()),
        onError = color(colors.onError()),
        errorContainer = color(colors.errorContainer()),
        onErrorContainer = color(colors.onErrorContainer()),
        outline = color(colors.outline()),
        outlineVariant = color(colors.outlineVariant()),
        scrim = color(colors.scrim()),
        surfaceBright = color(colors.surfaceBright()),
        surfaceDim = color(colors.surfaceDim()),
        surfaceContainer = color(colors.surfaceContainer()),
        surfaceContainerHigh = color(colors.surfaceContainerHigh()),
        surfaceContainerHighest = color(colors.surfaceContainerHighest()),
        surfaceContainerLow = color(colors.surfaceContainerLow()),
        surfaceContainerLowest = color(colors.surfaceContainerLowest()),
        primaryFixed = color(colors.primaryFixed()),
        primaryFixedDim = color(colors.primaryFixedDim()),
        onPrimaryFixed = color(colors.onPrimaryFixed()),
        onPrimaryFixedVariant = color(colors.onPrimaryFixedVariant()),
        secondaryFixed = color(colors.secondaryFixed()),
        secondaryFixedDim = color(colors.secondaryFixedDim()),
        onSecondaryFixed = color(colors.onSecondaryFixed()),
        onSecondaryFixedVariant = color(colors.onSecondaryFixedVariant()),
        tertiaryFixed = color(colors.tertiaryFixed()),
        tertiaryFixedDim = color(colors.tertiaryFixedDim()),
        onTertiaryFixed = color(colors.onTertiaryFixed()),
        onTertiaryFixedVariant = color(colors.onTertiaryFixedVariant()),
    )
}
