package com.atv.adbcommander.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ========================================================
// Tellyscope Material 3 Expressive Deep Teal & Cyan Palette
// ========================================================

// Signature Tellyscope Screen Glow & Deep Teal Seeds
val TellyTealPrimary = Color(0xFF80D4D5)       // Cyan-teal glow for icons, badges, indicators
val TellyTealDark = Color(0xFF004F50)          // Rich deep teal container
val TellyTealGlow = Color(0xFF9CF1F2)          // Accent teal highlight
val TellyTealInverse = Color(0xFF00696B)      // Darker teal for light surfaces
val TellyTealContainerLight = Color(0xFF9CF1F2)

// Obsidian Teal Surfaces
val TellyDarkBackground = Color(0xFF0E1415)    // Deepest obsidian teal background
val TellyDarkSurface = Color(0xFF0E1415)
val TellySurfaceContainer = Color(0xFF172021)   // Elevated cards base
val TellySurfaceContainerHigh = Color(0xFF253132) // Selected chips / modals / elevated dialogs
val TellySurfaceContainerHighest = Color(0xFF2F3C3D)
val TellySurfaceContainerLow = Color(0xFF121A1B)
val TellySurfaceContainerLowest = Color(0xFF080F10)
val TellySurfaceVariant = Color(0xFF3F4948)

// Typography & Text
val TellyOnSurface = Color(0xFFDDE4E3)          // Bright crisp text
val TellyOnSurfaceVariant = Color(0xFFBEC8C8)   // Subtitle / muted text
val TellyOutline = Color(0xFF3F4948)           // Card borders
val TellyOutlineSubtle = Color(0x33889392)

// Status & Semantic Accents
val TellySuccess = Color(0xFF2DD4BF)           // Teal-green success badge
val TellySuccessContainer = Color(0x262DD4BF)
val TellyWarning = Color(0xFFFBBF24)           // Amber warning
val TellyWarningContainer = Color(0x26FBBF24)
val TellyError = Color(0xFFFFB4AB)             // Muted red error
val TellyErrorContainer = Color(0xFF93000A)

// Secondary Tones
val TellySecondary = Color(0xFFB0CCCB)
val TellySecondaryContainer = Color(0xFF324B4B)
val TellyTertiary = Color(0xFFB3C8E8)
val TellyTertiaryContainer = Color(0xFF334863)

// Legacy compatibility aliases
val DarkBackground = TellyDarkBackground
val DarkSurface = TellyDarkSurface
val DarkSurfaceVariant = TellySurfaceContainerHigh
val DarkCard = TellySurfaceContainer
val DarkCardElevated = TellySurfaceContainerHigh
val DarkCardGlass = Color(0xE6172021)

val BorderSubtle = TellyOutline
val BorderGlow = TellyTealPrimary.copy(alpha = 0.4f)
val BorderSubtleGlow = TellyTealDark.copy(alpha = 0.5f)

val PrimaryBlue = TellyTealPrimary
val PrimaryGlow = TellyTealGlow
val CyberCyan = TellyTealPrimary
val AccentCyan = TellyTealGlow
val AccentGreen = TellySuccess
val AccentAmber = TellyWarning
val AccentRed = TellyError
val AccentPurple = TellyTertiary
val CyberRose = Color(0xFFFF2A6D)

val TextPrimary = TellyOnSurface
val TextSecondary = TellyOnSurfaceVariant
val TextMuted = Color(0xFF889392)

// Gradients
val CyberGradientPrimary = Brush.horizontalGradient(
    listOf(TellyTealGlow, TellyTealDark)
)

val CyberGradientSuccess = Brush.horizontalGradient(
    listOf(TellySuccess, TellyTealPrimary)
)

val CyberGradientDanger = Brush.horizontalGradient(
    listOf(TellyError, Color(0xFFFF8A00))
)

val DarkCardGradient = Brush.verticalGradient(
    listOf(TellySurfaceContainerHigh, TellySurfaceContainer)
)

val RemoteDPadGradient = Brush.radialGradient(
    listOf(TellySurfaceContainerHigh, TellySurfaceContainer)
)
