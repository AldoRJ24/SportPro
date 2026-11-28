package com.sportpro.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Academias y equipos con plan Gold (sus nombres se pintan dorados). Se carga al entrar a la app
 * y cada vez que un administrador cambia de plan. Es información de vitrina, no un permiso.
 */
object GoldNames {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()
    fun set(ids: Set<String>) { _ids.value = ids }
}

// Tonos de oro: del más oscuro al brillo casi blanco, para que el degradado parezca metal pulido
val GoldDeep = Color(0xFF9A6B12)
val GoldMid = Color(0xFFD4A62A)
val GoldLight = Color(0xFFF7DC7A)
val GoldShine = Color(0xFFFFF6D5)

/**
 * Nombre de una academia o equipo. Si [id] es Gold, el texto es dorado con un brillo que lo
 * recorre en diagonal cada ~2,6 s (como luz sobre oro). Si no, es un texto normal.
 * Con «reducir animaciones» del sistema el brillo igual es lento y suave, sin parpadeos.
 */
@Composable
fun TeamName(
    text: String,
    id: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 1,
    color: Color = Color.Unspecified,
) {
    val gold by GoldNames.ids.collectAsStateWithLifecycle()
    if (id == null || id !in gold) {
        Text(text, modifier = modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, color = color)
        return
    }
    GoldText(text, modifier, style, maxLines)
}

/** Texto dorado animado (también lo usa la tarjeta del plan Gold). */
@Composable
fun GoldText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, maxLines: Int = 1) {
    val shift by rememberInfiniteTransition(label = "gold").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "goldShift",
    )
    // Degradado diagonal que se desplaza: el tramo claro («brillo») cruza el texto de izquierda a derecha
    val span = 420f
    val brush = Brush.linearGradient(
        colors = listOf(GoldDeep, GoldMid, GoldLight, GoldShine, GoldLight, GoldMid, GoldDeep),
        start = Offset(-span + shift * span * 2, 0f),
        end = Offset(shift * span * 2, span * 0.35f),
        tileMode = TileMode.Mirror,
    )
    Text(
        text,
        modifier = modifier,
        style = style.copy(brush = brush, fontWeight = style.fontWeight ?: FontWeight.SemiBold),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
