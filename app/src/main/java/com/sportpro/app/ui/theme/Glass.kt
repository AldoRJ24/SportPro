package com.sportpro.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * Material «vidrio» de iOS: lo que pasa por detrás se ve desenfocado y con un velo del color
 * del fondo encima, así el texto de la barra se lee bien sobre cualquier contenido.
 *
 *  - El velo es más opaco en modo claro (blanco al 72 %) que en oscuro (negro al 62 %), igual
 *    que los materiales de Apple: sobre negro el desenfoque ya separa lo suficiente.
 *  - fallbackTint: en Android 10 y 11 no hay desenfoque en tiempo real; ahí la barra se dibuja
 *    casi opaca para que nunca se pierda la lectura.
 */
@Composable
fun glassStyle(dark: Boolean = LocalIsDark.current, blur: Dp = 24.dp): HazeStyle {
    val base = if (dark) Night else Paper
    return HazeStyle(
        backgroundColor = base,
        tint = HazeTint(base.copy(alpha = if (dark) 0.62f else 0.72f)),
        blurRadius = blur,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(base.copy(alpha = 0.94f)),
    )
}

/** Línea fina del borde del vidrio: en iOS es la luz que «toca» el canto del material. */
@Composable
fun glassHairline(dark: Boolean = LocalIsDark.current): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

/**
 * Estado del vidrio de la pantalla principal. MainShell marca su contenido como «fuente» y la
 * barra inferior lo desenfoca. Las pantallas no lo usan directamente.
 */
val LocalShellHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * Alto que ocupa la barra inferior flotante (con el borde del sistema incluido). Las pestañas lo
 * suman al final de sus listas para que el último elemento no quede tapado, pero la lista sigue
 * pasando POR DEBAJO de la barra (así se ve el desenfoque).
 */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

/**
 * Respuesta inmediata al tocar (regla de Apple: el aviso va al apoyar el dedo, no al soltarlo).
 * El elemento se encoge un poco con un resorte críticamente amortiguado: sin rebote, y si se
 * suelta a mitad del gesto vuelve desde donde está, sin saltos.
 */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
