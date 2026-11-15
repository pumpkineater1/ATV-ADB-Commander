package com.atv.adbcommander.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ========================================================
// Pure Monochrome & Titanium Minimalist Palette (B&W + Tint)
// ========================================================

// Signature Monochromatic Accents with subtle silver-slate tint
val TellyTealPrimary = Color(0xFFFFFFFF)          // Pure crisp white highlight
val TellyTealDark = Color(0xFF22262B)             // Tinted deep graphite container
val TellyTealGlow = Color(0xFFE4E9EE)             // Silver-titanium highlight
val TellyTealInverse = Color(0xFF15181C)          // Deep slate for inverse surfaces
val TellyTealContainerLight = Color(0xFFE4E9EE)

// Deepest Obsidian & OLED Black Surfaces with Subtle Titanium Tint
val TellyDarkBackground = Color(0xFF08090A)       // True deep OLED black background
val TellyDarkSurface = Color(0xFF08090A)
val TellySurfaceContainer = Color(0xFF121518)      // Sleek obsidian card container
val TellySurfaceContainerHigh = Color(0xFF1C2025)  // Elevated chips & dialogs
val TellySurfaceContainerHighest = Color(0xFF272D34)
val TellySurfaceContainerLow = Color(0xFF0E1012)
val TellySurfaceContainerLowest = Color(0xFF040506)
val TellySurfaceVariant = Color(0xFF1E2328)

// Crisp Typography & Outlines
val TellyOnSurface = Color(0xFFFFFFFF)             // High contrast pure white
val TellyOnSurfaceVariant = Color(0xFF9EABB5)      // Modern silver-grey secondary text
val TellyOutline = Color(0xFF262C33)              // Hairline slate borders
val TellyOutlineSubtle = Color(0x24FFFFFF)         // Delicate translucent hairline border

// Status Accents (Refined & Clean)
val TellySuccess = Color(0xFF4ADE80)              // Clean emerald
val TellySuccessContainer = Color(0x224ADE80)
val TellyWarning = Color(0xFFFBBF24)              // Crisp amber
val TellyWarningContainer = Color(0x22FBBF24)
val TellyError = Color(0xFFF87171)                // Crisp coral red
val TellyErrorContainer = Color(0x22F87171)

// Secondary Tones
val TellySecondary = Color(0xFFC7D0D8)
val TellySecondaryContainer = Color(0xFF252B32)
val TellyTertiary = Color(0xFFD6DEE5)
val TellyTertiaryContainer = Color(0xFF283038)

// Legacy compatibility aliases
val DarkBackground = TellyDarkBackground
val DarkSurface = TellyDarkSurface
val DarkSurfaceVariant = TellySurfaceContainerHigh
val DarkCard = TellySurfaceContainer
val DarkCardElevated = TellySurfaceContainerHigh
val DarkCardGlass = Color(0xE6121518)

val BorderSubtle = TellyOutline
val BorderGlow = TellyTealPrimary.copy(alpha = 0.25f)
val BorderSubtleGlow = TellyTealDark.copy(alpha = 0.4f)

val PrimaryBlue = TellyTealPrimary
val PrimaryGlow = TellyTealGlow
val CyberCyan = TellyTealPrimary
val AccentCyan = TellyTealGlow
val AccentGreen = TellySuccess
val AccentAmber = TellyWarning
val AccentRed = TellyError
val AccentPurple = TellyTertiary
val CyberRose = Color(0xFFF87171)

val TextPrimary = TellyOnSurface
val TextSecondary = TellyOnSurfaceVariant
val TextMuted = Color(0xFF6B7682)

// Gradients
val CyberGradientPrimary = Brush.horizontalGradient(
    listOf(Color(0xFFFFFFFF), Color(0xFF8B98A5))
)

val CyberGradientSuccess = Brush.horizontalGradient(
    listOf(TellySuccess, Color(0xFF86EFAC))
)

val CyberGradientDanger = Brush.horizontalGradient(
    listOf(TellyError, Color(0xFFFCA5A5))
)

val DarkCardGradient = Brush.verticalGradient(
    listOf(TellySurfaceContainerHigh, TellySurfaceContainer)
)

val RemoteDPadGradient = Brush.radialGradient(
    listOf(TellySurfaceContainerHigh, TellySurfaceContainer)
)
