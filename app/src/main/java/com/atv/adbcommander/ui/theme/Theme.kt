package com.atv.adbcommander.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Minimalist Monochrome & Titanium Dark Theme
private val DarkBrandColorScheme = darkColorScheme(
    primary = TellyTealPrimary,
    onPrimary = Color(0xFF0A0C0E),
    primaryContainer = TellyTealDark,
    onPrimaryContainer = TellyTealGlow,
    inversePrimary = TellyTealInverse,
    secondary = TellySecondary,
    onSecondary = Color(0xFF15181C),
    secondaryContainer = TellySecondaryContainer,
    onSecondaryContainer = Color(0xFFE4E9EE),
    tertiary = TellyTertiary,
    onTertiary = Color(0xFF15181C),
    tertiaryContainer = TellyTertiaryContainer,
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = TellyDarkBackground,
    surface = TellyDarkSurface,
    surfaceVariant = TellySurfaceVariant,
    onBackground = TellyOnSurface,
    onSurface = TellyOnSurface,
    onSurfaceVariant = TellyOnSurfaceVariant,
    surfaceContainer = TellySurfaceContainer,
    surfaceContainerHigh = TellySurfaceContainerHigh,
    surfaceContainerHighest = TellySurfaceContainerHighest,
    surfaceContainerLow = TellySurfaceContainerLow,
    surfaceContainerLowest = TellySurfaceContainerLowest,
    outline = TellyOutline,
    outlineVariant = Color(0xFF1F2429),
    error = TellyError,
    onError = Color(0xFF3B0B0B),
    errorContainer = TellyErrorContainer,
    onErrorContainer = Color(0xFFFFFFFF)
)

@Composable
fun ATVADBCommanderTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        DarkBrandColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
