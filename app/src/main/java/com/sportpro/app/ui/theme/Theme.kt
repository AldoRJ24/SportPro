package com.sportpro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SportProColors = darkColorScheme(
    primary = Mint400,
    onPrimary = Mint900,
    primaryContainer = Petrol700,
    onPrimaryContainer = Mint300,
    secondary = Teal300,
    onSecondary = Petrol950,
    secondaryContainer = Petrol700,
    onSecondaryContainer = TextHigh,
    tertiary = WarnAmber,
    background = Petrol900,
    onBackground = TextHigh,
    surface = Petrol900,
    onSurface = TextHigh,
    surfaceVariant = Petrol800,
    onSurfaceVariant = TextMid,
    surfaceContainer = Petrol850,
    surfaceContainerHigh = Petrol800,
    surfaceContainerHighest = Petrol700,
    surfaceContainerLow = Petrol850,
    surfaceContainerLowest = Petrol950,
    outline = Petrol600,
    outlineVariant = Petrol700,
    error = ErrorRed,
)

/** Dark, high-contrast theme (usable outdoors during matches). */
@Composable
fun SportProTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SportProColors, typography = Typography, content = content)
}
