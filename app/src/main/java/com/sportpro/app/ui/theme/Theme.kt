package com.sportpro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SportProColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueSoft,
    onPrimaryContainer = BrandBlueDark,
    secondary = Ink,
    onSecondary = Color.White,
    secondaryContainer = BrandBlueSoft,
    onSecondaryContainer = BrandBlueDark,
    tertiary = BrandBlueDark,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist100,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Mist50,
    surfaceContainer = Mist100,
    surfaceContainerHigh = Mist200,
    surfaceContainerHighest = Mist300,
    // Sin tinte de color al elevar: las superficies se quedan blancas o grises, no azuladas
    surfaceTint = Color.Transparent,
    outline = LineStrong,
    outlineVariant = Line,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    // Avisos inferiores (snackbar): fondo negro con texto blanco
    inverseSurface = Ink,
    inverseOnSurface = Color.White,
    inversePrimary = BrandBlueLight,
    scrim = Color.Black,
)

/** Tema claro en azul, blanco y negro: alto contraste para leerse bien al aire libre. */
@Composable
fun SportProTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SportProColors, typography = Typography, content = content)
}
