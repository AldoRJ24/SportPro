package com.sportpro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val LightColors = lightColorScheme(
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
    // Las tarjetas usan surfaceVariant: gris agrupado de iOS, separa sin bordes ni sombras
    surfaceVariant = Mist100,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Mist50,
    surfaceContainer = Mist50,
    surfaceContainerHigh = Paper,
    // Card() sin colores propios usa este tono: igual que surfaceVariant para que todas las tarjetas coincidan
    surfaceContainerHighest = Mist100,
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

private val DarkColors = darkColorScheme(
    primary = BrandBlueNight,
    onPrimary = Color.White,
    primaryContainer = BrandBlueNightSoft,
    onPrimaryContainer = BrandBlueSoft,
    secondary = NightInk,
    onSecondary = Night,
    secondaryContainer = BrandBlueNightSoft,
    onSecondaryContainer = BrandBlueSoft,
    tertiary = BrandBlueLight,
    onTertiary = Night,
    background = Night,
    onBackground = NightInk,
    surface = Night,
    onSurface = NightInk,
    surfaceVariant = Night100,
    onSurfaceVariant = NightInkSoft,
    surfaceContainerLowest = Night,
    surfaceContainerLow = Night100,
    surfaceContainer = Night100,
    surfaceContainerHigh = Night200,
    surfaceContainerHighest = Night100,
    surfaceTint = Color.Transparent,
    outline = NightLineStrong,
    outlineVariant = NightLine,
    error = ErrorRedNight,
    onError = Color.White,
    errorContainer = Color(0xFF4A1512),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = NightInk,
    inverseOnSurface = Night,
    inversePrimary = BrandBlue,
    scrim = Color.Black,
)

/**
 * Esquinas amplias y suaves, como las de iOS. Las tarjetas grandes llevan más radio que los
 * botones y los chips: la curva crece con el tamaño de la superficie.
 */
private val SportProShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** true cuando la app se está dibujando en modo oscuro (lo leen el vidrio y las barras del sistema). */
val LocalIsDark = staticCompositionLocalOf { false }

/** Decide si toca modo oscuro según lo elegido en Apariencia. */
@Composable
fun rememberIsDark(): Boolean {
    val mode by ThemeSettings.mode.collectAsStateWithLifecycle()
    return when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}

/** Tema SportPro: claro por defecto, con modo oscuro opcional (Perfil → Apariencia). */
@Composable
fun SportProTheme(dark: Boolean = rememberIsDark(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Typography,
            shapes = SportProShapes,
            content = content,
        )
    }
}
