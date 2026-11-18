package com.sportpro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SportProColors = darkColorScheme(
    primary = Crimson400,
    onPrimary = Crimson950,
    primaryContainer = Crimson800,
    onPrimaryContainer = Crimson100,
    secondary = Coral300,
    onSecondary = Carbon950,
    secondaryContainer = Carbon700,
    onSecondaryContainer = TextHigh,
    tertiary = WarnAmber,
    background = Carbon900,
    onBackground = TextHigh,
    surface = Carbon900,
    onSurface = TextHigh,
    surfaceVariant = Carbon800,
    onSurfaceVariant = TextMid,
    surfaceContainer = Carbon850,
    surfaceContainerHigh = Carbon800,
    surfaceContainerHighest = Carbon700,
    surfaceContainerLow = Carbon850,
    surfaceContainerLowest = Carbon950,
    outline = Carbon600,
    outlineVariant = Carbon700,
    error = ErrorRed,
)

/** Tema oscuro deportivo en rojos, de alto contraste (usable al aire libre durante los partidos). */
@Composable
fun SportProTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SportProColors, typography = Typography, content = content)
}
