package com.sportpro.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.PitchFormations
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.SlotAssignment
import com.sportpro.app.ui.theme.BrandBlue
import com.sportpro.app.ui.theme.PitchGreen
import com.sportpro.app.ui.theme.PitchGreenStripe
import com.sportpro.app.ui.theme.PitchLine
import kotlin.math.roundToInt

/** Lo que hace falta para dibujar la ficha de un jugador. */
data class TokenInfo(val name: String, val photoPath: String?, val jersey: Int?)

// Medidas reales de una cancha de fútbol 11 (en metros), para que las áreas se vean proporcionadas
private const val PITCH_LENGTH = 105f
private const val PITCH_WIDTH = 68f

/**
 * Césped con sus líneas, dibujado a mano (sin imágenes):
 * - [half] = null: cancha completa (arcos arriba y abajo);
 * - [half] = true: solo la mitad de ARRIBA (arco arriba, medio campo abajo);
 * - [half] = false: solo la mitad de ABAJO (medio campo arriba, arco abajo).
 */
@Composable
fun PitchSurface(modifier: Modifier = Modifier, half: Boolean? = null) {
    Canvas(modifier) {
        val margin = 6.dp.toPx()
        val stroke = Stroke(width = 1.5.dp.toPx())
        // Franjas de césped cortado
        val stripes = if (half == null) 12 else 6
        repeat(stripes) { i ->
            drawRect(
                color = if (i % 2 == 0) PitchGreen else PitchGreenStripe,
                topLeft = Offset(0f, size.height * i / stripes),
                size = Size(size.width, size.height / stripes + 1f),
            )
        }
        val left = margin
        val right = size.width - margin
        val top = margin
        val bottom = size.height - margin
        val w = right - left
        // Largo de la cancha COMPLETA en píxeles (si se dibuja una mitad, es el doble de lo visible)
        val fullLength = if (half == null) bottom - top else (bottom - top) * 2
        val metersY = fullLength / PITCH_LENGTH
        val metersX = w / PITCH_WIDTH

        drawRect(PitchLine, Offset(left, top), Size(w, bottom - top), style = stroke)
        val centerRadius = 9.15f * metersX
        when (half) {
            null -> {
                val midY = (top + bottom) / 2
                drawLine(PitchLine, Offset(left, midY), Offset(right, midY), stroke.width)
                drawCircle(PitchLine, centerRadius, Offset(size.width / 2, midY), style = stroke)
                drawGoalArea(goalAtTop = true, edge = top, left, w, metersX, metersY, stroke)
                drawGoalArea(goalAtTop = false, edge = bottom, left, w, metersX, metersY, stroke)
            }
            true -> {
                // El borde de abajo es la línea de medio campo: se ve medio círculo central
                drawArc(PitchLine, 180f, 180f, false, Offset(size.width / 2 - centerRadius, bottom - centerRadius),
                    Size(centerRadius * 2, centerRadius * 2), style = stroke)
                drawGoalArea(goalAtTop = true, edge = top, left, w, metersX, metersY, stroke)
            }
            false -> {
                drawArc(PitchLine, 0f, 180f, false, Offset(size.width / 2 - centerRadius, top - centerRadius),
                    Size(centerRadius * 2, centerRadius * 2), style = stroke)
                drawGoalArea(goalAtTop = false, edge = bottom, left, w, metersX, metersY, stroke)
            }
        }
    }
}

// Área grande (16,5 m × 40,3 m) y área chica (5,5 m × 18,3 m) pegadas a la línea del arco.
private fun DrawScope.drawGoalArea(
    goalAtTop: Boolean, edge: Float, left: Float, w: Float, metersX: Float, metersY: Float, stroke: Stroke,
) {
    listOf(16.5f to 40.3f, 5.5f to 18.3f).forEach { (depthM, widthM) ->
        val aw = widthM * metersX
        val ad = depthM * metersY
        val x = left + (w - aw) / 2
        val y = if (goalAtTop) edge else edge - ad
        drawRect(PitchLine, Offset(x, y), Size(aw, ad), style = stroke)
    }
}

/**
 * Ficha de un jugador: su foto en un círculo (o sus iniciales si no hay foto o si es un menor
 * que el usuario no puede ver) y, debajo, el dorsal y el nombre corto.
 * [onPitch] cambia los colores del texto para que se lean sobre el césped.
 * [badges] se dibuja encima de la foto (el partido en vivo pone ahí goles, tarjetas, cambios y MVP).
 */
@Composable
fun PlayerToken(
    info: TokenInfo,
    size: Dp,
    onPitch: Boolean,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    highlighted: Boolean = false,
    badges: (@Composable BoxScope.() -> Unit)? = null,
) {
    Column(
        modifier.width(size + 34.dp).alpha(if (dimmed) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size)) {
            Avatar(info.name, info.photoPath, size = size, ring = if (highlighted) BrandBlue else Color.White)
            badges?.invoke(this)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            info.jersey?.let {
                Text(
                    "$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (onPitch) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                PitchFormations.pitchName(info.name),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (onPitch) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Casilla vacía: círculo punteado con «+» y la posición que falta (ARQ, DEF, MED, DEL). */
@Composable
fun EmptySlotToken(pos: Position, size: Dp, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val color = if (highlighted) BrandBlue else Color.White
    Column(modifier.width(size + 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(Color.White.copy(alpha = 0.18f))
                    drawCircle(
                        color, radius = this.size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Text(pos.short, style = MaterialTheme.typography.labelSmall, color = Color.White, textAlign = TextAlign.Center)
    }
}

/**
 * MITAD de la cancha con las casillas de un equipo. La usan el editor (con [onTap] y [onMove])
 * y la vista previa de solo lectura (sin ellos).
 * - [top] = true: mitad de arriba (local, girado); false: mitad de abajo (visitante).
 * - Cada ficha se centra en su lugar; al arrastrarla se informa el lugar NUEVO en coordenadas
 *   propias del equipo (0..1), ya convertidas desde la pantalla.
 */
@Composable
fun HalfPitchBoard(
    slots: List<SlotAssignment>,
    top: Boolean,
    player: (String) -> TokenInfo?,
    tokenSize: Dp,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onTap: ((Int) -> Unit)? = null,
    onMove: ((Int, Float, Float) -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f / 1.05f)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val tokenWPx = with(density) { (tokenSize + 34.dp).toPx() }
        val tokenHPx = with(density) { tokenSize.toPx() }
        PitchSurface(Modifier.matchParentSize(), half = top)

        slots.forEachIndexed { i, s ->
            key(i) {
                val currentSlot by rememberUpdatedState(s.slot)
                val currentOnMove by rememberUpdatedState(onMove)
                val currentOnTap by rememberUpdatedState(onTap)
                val (sx, sy) = PitchFormations.toHalfScreen(s.slot.x, s.slot.y, top)
                // El centro del círculo queda justo sobre el lugar de la casilla
                val tokenModifier = Modifier
                    .offset { IntOffset((sx * wPx - tokenWPx / 2).roundToInt(), (sy * hPx - tokenHPx / 2).roundToInt()) }
                    .then(
                        if (onTap != null) Modifier.pointerInput(i) { detectTapGestures { currentOnTap?.invoke(i) } } else Modifier,
                    )
                    .then(
                        if (onMove != null) {
                            Modifier.pointerInput(i, wPx, hPx, top) {
                                var pos = Offset.Zero
                                detectDragGestures(
                                    onDragStart = {
                                        val (x0, y0) = PitchFormations.toHalfScreen(currentSlot.x, currentSlot.y, top)
                                        pos = Offset(x0 * wPx, y0 * hPx)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        pos += amount
                                        val (x, y) = PitchFormations.fromHalfScreen(pos.x / wPx, pos.y / hPx, top)
                                        currentOnMove?.invoke(i, x, y)
                                    },
                                )
                            }
                        } else Modifier,
                    )
                val info = s.playerId?.let(player)
                if (info != null) {
                    PlayerToken(info, tokenSize, onPitch = true, modifier = tokenModifier, highlighted = selected == i)
                } else {
                    EmptySlotToken(s.slot.pos, tokenSize, modifier = tokenModifier, highlighted = selected == i)
                }
            }
        }
    }
}

/** Tamaño de ficha según cuántos jugadores hay en cancha (en fútbol 11 se achica para que entren). */
fun tokenSizeFor(format: Int): Dp = when {
    format >= 10 -> 40.dp
    format >= 8 -> 44.dp
    else -> 50.dp
}

/**
 * Fila de «bolitas» fuera de la cancha (suplentes): foto chica, dorsal y nombre.
 * En el partido en vivo, [badges] muestra lo que hizo cada uno (por ejemplo, el ícono de cambio).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BenchRow(
    ids: List<String>,
    player: (String) -> TokenInfo?,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onTap: ((String) -> Unit)? = null,
    badges: (@Composable BoxScope.(String) -> Unit)? = null,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val currentOnTap by rememberUpdatedState(onTap)
        ids.forEach { id ->
            val info = player(id) ?: return@forEach
            val content: (@Composable BoxScope.() -> Unit)? = if (badges != null) { { badges(id) } } else null
            PlayerToken(
                info, 36.dp, onPitch = false, dimmed = dimmed, badges = content,
                modifier = if (onTap != null) Modifier.pointerInput(id) { detectTapGestures { currentOnTap?.invoke(id) } } else Modifier,
            )
        }
    }
}

/**
 * CANCHA COMPLETA con los dos equipos (partido en vivo y alineaciones publicadas):
 * el local en la mitad de arriba (arco arriba) y el visitante en la de abajo.
 * Solo se dibujan las casillas ocupadas. [dimmed] atenúa a un jugador (por ejemplo, expulsado)
 * y [badges] dibuja sus íconos (goles, asistencias, tarjetas, cambios, MVP).
 * Deja espacio abajo para el nombre del arquero visitante, que queda pegado al borde.
 */
@Composable
fun FullPitchBoard(
    home: List<SlotAssignment>,
    away: List<SlotAssignment>,
    player: (String) -> TokenInfo?,
    tokenSize: Dp,
    modifier: Modifier = Modifier,
    dimmed: (String) -> Boolean = { false },
    badges: (@Composable BoxScope.(String) -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(0.62f)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val tokenWPx = with(density) { (tokenSize + 34.dp).toPx() }
        val tokenHPx = with(density) { tokenSize.toPx() }
        PitchSurface(Modifier.matchParentSize(), half = null)
        listOf(true to home, false to away).forEach { (top, slots) ->
            slots.forEachIndexed { i, s ->
                val id = s.playerId ?: return@forEachIndexed
                val info = player(id) ?: return@forEachIndexed
                key(top, i) {
                    val (sx, sy) = PitchFormations.toScreen(s.slot.x, s.slot.y, top)
                    val content: (@Composable BoxScope.() -> Unit)? = if (badges != null) { { badges(id) } } else null
                    PlayerToken(
                        info, tokenSize, onPitch = true, dimmed = dimmed(id), badges = content,
                        modifier = Modifier.offset {
                            IntOffset((sx * wPx - tokenWPx / 2).roundToInt(), (sy * hPx - tokenHPx / 2).roundToInt())
                        },
                    )
                }
            }
        }
    }
}
