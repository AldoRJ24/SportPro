package com.sportpro.app.ui.subscription

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.Plan
import com.sportpro.app.ui.components.GoldDeep
import com.sportpro.app.ui.components.GoldLight
import com.sportpro.app.ui.components.GoldMid
import com.sportpro.app.ui.components.GoldShine
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Cuánto hay que mantener presionado para pagar. */
private const val HOLD_MS = 5_000

/**
 * Botón de pago «mantén presionado 5 segundos». Mientras el dedo está apoyado, la barra se llena
 * con un FLUIDO (borde ondulado que se mueve): negro para el plan Black, dorado para el Gold.
 *  - Si se suelta antes de los 5 s, el fluido baja suavemente hasta vaciarse y hay que empezar de
 *    nuevo (así un toque accidental nunca paga).
 *  - Cada segundo cumplido da una vibración corta; al completarse, una más fuerte y [onPaid].
 *  - Con lector de pantalla, la acción «Pagar» está disponible directamente (no hace falta sostener).
 */
@Composable
fun HoldToPayBar(
    plan: Plan,
    idleLabel: String,
    holdingLabel: (secondsLeft: Int) -> String,
    doneLabel: String,
    enabled: Boolean,
    onPaid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val currentOnPaid by rememberUpdatedState(onPaid)

    // Vibración por cada segundo cumplido (20 % de la barra)
    LaunchedEffect(Unit) {
        var lastStep = 0
        snapshotFlow { (progress.value * 5).toInt() }.collect { step ->
            if (step > lastStep && step < 5) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            lastStep = step
        }
    }

    // Movimiento del oleaje: siempre activo, así el fluido «respira» aunque no se esté llenando
    val wave by rememberInfiniteTransition(label = "liquid").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    val shine by rememberInfiniteTransition(label = "liquidShine").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "shinePhase",
    )

    fun pay() {
        if (done) return
        done = true
        holding = false
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        currentOnPaid()
    }

    val track = MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(shape)
            .background(track)
            .semantics {
                contentDescription = idleLabel
                onClick(label = idleLabel) { if (enabled) pay(); enabled }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (done) return@awaitEachGesture
                    holding = true
                    // Llenar lo que falta al ritmo de 5 s por barra completa (si quedó algo, sigue desde ahí)
                    val fill = scope.launch {
                        val remaining = ((1f - progress.value) * HOLD_MS).toInt().coerceAtLeast(1)
                        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                        pay()
                    }
                    waitForUpOrCancellation()
                    if (!done) {
                        // Se soltó antes: el fluido se vacía (más rápido que el llenado, pero sin salto)
                        fill.cancel()
                        holding = false
                        scope.launch { progress.animateTo(0f, tween((progress.value * 900).toInt().coerceAtLeast(150), easing = FastOutSlowInEasing)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            if (p <= 0f) return@Canvas
            val w = size.width
            val h = size.height
            val edge = w * p
            val amp = 7.dp.toPx() * (if (p >= 1f) 0f else 1f)   // al llenarse del todo, el borde se aquieta
            val brush = when (plan) {
                Plan.GOLD -> Brush.linearGradient(
                    listOf(GoldDeep, GoldMid, GoldLight, GoldShine, GoldLight, GoldMid),
                    start = Offset(-w + shine * w * 2, 0f), end = Offset(shine * w * 2, h),
                )
                else -> Brush.verticalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF0A0A0A), Color.Black))
            }
            // Dos capas de oleaje desfasadas: la de atrás más clara da profundidad al «líquido»
            listOf(0.5f to 0.45f, 0f to 1f).forEach { (offset, alpha) ->
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(edge, 0f)
                    val steps = 24
                    for (i in 0..steps) {
                        val y = h * i / steps
                        val x = edge + amp * sin(2 * PI * (y / h + wave + offset)).toFloat()
                        lineTo(x, y)
                    }
                    lineTo(0f, h)
                    close()
                }
                drawPath(path, brush, alpha = alpha)
            }
            // Brillo superior (menisco): una franja clara arriba hace que parezca un fluido con volumen
            drawRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = h * 0.45f),
                size = androidx.compose.ui.geometry.Size(edge, h * 0.45f),
            )
        }
        val p = progress.value
        val left = ((1f - p) * HOLD_MS / 1000f).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(1)
        // El texto cambia a claro cuando el fluido ya cubre el centro de la barra
        val onFill = p > 0.5f && plan != Plan.BASIC
        Text(
            when {
                done -> doneLabel
                holding -> holdingLabel(left)
                else -> idleLabel
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                !onFill -> MaterialTheme.colorScheme.onSurface
                plan == Plan.GOLD -> Color(0xFF3B2A05)
                else -> Color.White
            },
        )
    }
}
