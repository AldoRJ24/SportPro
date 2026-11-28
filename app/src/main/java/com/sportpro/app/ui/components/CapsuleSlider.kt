package com.sportpro.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sportpro.app.R
import com.sportpro.app.ui.theme.BrandBlue
import com.sportpro.app.ui.theme.BrandBlueBright
import com.sportpro.app.ui.theme.LocalIsDark
import kotlin.math.roundToInt

/**
 * Deslizador en forma de cápsula gruesa, inspirado en la barra de brillo de One UI:
 *
 *  - El relleno crece desde la izquierda y su borde derecho es redondo, como una pastilla.
 *  - El arrastre es RELATIVO: se puede empezar a deslizar desde cualquier punto y el valor se
 *    mueve con el dedo (no salta a donde se tocó). Un toque corto sí salta a esa posición.
 *  - Cada vez que el número cambia suena un «tic» corto y el celular da una vibración mínima,
 *    como una ruedita con muescas. Así se siente cada minuto sin mirar la pantalla.
 *  - El relleno sigue al dedo con un resorte sin rebote: no hay saltos aunque el valor sea entero.
 *
 * [valueLabel] es el texto que se muestra dentro (por ejemplo «25 min»).
 */
@Composable
fun CapsuleSlider(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    height: Dp = 58.dp,
    accessibilityLabel: String = valueLabel,
) {
    val view = LocalView.current
    val tick = rememberTickSound()
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val span = (range.last - range.first).coerceAtLeast(1)
    val dark = LocalIsDark.current
    val track = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)
    val fillBrush = Brush.horizontalGradient(listOf(BrandBlue, BrandBlueBright))

    /** Cambia el valor (limitado al rango) y, si de verdad cambió, suena y vibra una vez. */
    fun emit(newValue: Int) {
        val v = newValue.coerceIn(range.first, range.last)
        if (v != currentValue) {
            tick()
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            currentOnChange(v)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(track)
            .semantics {
                contentDescription = accessibilityLabel
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat())
                setProgress { target -> emit(target.roundToInt()); true }
            },
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val minFillPx = with(LocalDensity.current) { height.toPx() }  // el relleno nunca es menor que un círculo
        // Posición «continua» del dedo, en unidades del valor: permite arrastres lentos y precisos
        var dragValue by remember { mutableFloatStateOf(value.toFloat()) }
        val fraction = (value - range.first).toFloat() / span
        val shown by animateFloatAsState(
            targetValue = fraction,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            label = "capsuleFill",
        )
        val fillPx = minFillPx + (widthPx - minFillPx) * shown

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(range) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragValue = currentValue.toFloat() },
                    ) { change, dx ->
                        change.consume()
                        // Recorrer todo el ancho = recorrer todo el rango
                        dragValue = (dragValue + dx / (widthPx - minFillPx).coerceAtLeast(1f) * span)
                            .coerceIn(range.first.toFloat(), range.last.toFloat())
                        emit(dragValue.roundToInt())
                    }
                }
                .pointerInput(range) {
                    detectTapGestures { offset ->
                        val f = ((offset.x - minFillPx / 2) / (widthPx - minFillPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        emit(range.first + (f * span).roundToInt())
                    }
                },
        ) {
            // Relleno con borde redondo (la «pastilla»)
            Box(
                Modifier
                    .width(with(LocalDensity.current) { fillPx.toDp() })
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(fillBrush),
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Box(Modifier.width(10.dp))
                }
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    // Sobre el relleno azul va en blanco; si el relleno es muy corto, el texto queda
                    // sobre el riel gris y se pinta del color normal para que siempre se lea
                    color = if (fraction > 0.28f) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun Modifier.fillMaxSize(): Modifier = this.fillMaxWidth().fillMaxHeight()

/**
 * Sonido corto de «tic» (res/raw/slider_tick.wav, 14 ms). Se carga una vez por pantalla con
 * SoundPool, que está hecho para efectos muy cortos y repetidos sin retraso. Usa el canal de
 * sonidos de la interfaz: respeta el volumen del sistema y el modo silencio.
 */
@Composable
private fun rememberTickSound(): () -> Unit {
    val context = LocalContext.current
    val player = remember { TickPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player::play
}

private class TickPlayer(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private var ready = false
    private val soundId = pool.load(context, R.raw.slider_tick, 1).also {
        pool.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
    }
    private var lastPlay = 0L

    fun play() {
        // Al deslizar muy rápido se limita a ~1 tic cada 25 ms: más seguido sonaría como un zumbido
        val now = System.nanoTime()
        if (!ready || now - lastPlay < 25_000_000L) return
        lastPlay = now
        pool.play(soundId, 0.45f, 0.45f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
