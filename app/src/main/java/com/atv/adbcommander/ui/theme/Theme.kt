package com.atv.adbcommander.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Tellyscope Branded Dark Theme
private val DarkBrandColorScheme = darkColorScheme(
    primary = TellyTealPrimary,
    onPrimary = TellyTealDark,
    primaryContainer = TellyTealDark,
    onPrimaryContainer = TellyTealGlow,
    inversePrimary = TellyTealInverse,
    secondary = TellySecondary,
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = TellySecondaryContainer,
    onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = TellyTertiary,
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = TellyTertiaryContainer,
    onTertiaryContainer = Color(0xFFD3E4FF),
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
    outlineVariant = Color(0xFF2B3232),
    error = TellyError,
    onError = Color(0xFF690005),
    errorContainer = TellyErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6)
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
