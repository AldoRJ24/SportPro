package com.sportpro.app.ui.match
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.Position
import com.sportpro.app.ui.components.PositionBadge
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.TimelineEvent
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.core.data.offline.PendingEvent
import com.sportpro.app.ui.components.Crest
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.components.EventIcon
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.StatusBadge
import com.sportpro.app.ui.theme.ErrorRed
import com.sportpro.app.ui.theme.ScoreStyle
import com.sportpro.app.ui.theme.WarnAmber
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@Composable
fun MatchHeader(match: MatchRow, nowMs: Long, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StatusBadge(match, nowMs)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamCrest(match.homeName, match.home?.crestPath, 64.dp)
                    Text(match.homeName, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2)
                }
                Text("${match.homeScore} - ${match.awayScore}", style = ScoreStyle, modifier = Modifier.padding(horizontal = 12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamCrest(match.awayName, match.away?.crestPath, 64.dp)
                    Text(match.awayName, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(
                    match.category,
                    stringResource(R.string.match_parts_x_vs_x, match.formatPlayers, match.formatPlayers),
                    stringResource(R.string.match_parts_x_por_tiempo, match.halfMinutes),
                    match.venue,
                    formatDayTime(match.scheduledAt),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun JsonPrimitiveOrNull(e: kotlinx.serialization.json.JsonElement?): JsonPrimitive? = e as? JsonPrimitive

/** Detalle de un evento en una línea: «en contra», resultado del penal, asistencia o «sale X, entra Y». */
@Composable
private fun eventDetailText(e: TimelineEvent, nameOf: (String?) -> String?): String = buildList {
    val p = e.payload
    if (JsonPrimitiveOrNull(p["own_goal"])?.booleanOrNull == true) add(stringResource(R.string.match_parts_en_contra))
    when (JsonPrimitiveOrNull(p["result"])?.contentOrNull) {
        "converted" -> add("convertido")
        "missed" -> add("fallado")
        "saved" -> add("atajado")
    }
    JsonPrimitiveOrNull(p["assist_player_id"])?.contentOrNull?.let { id -> nameOf(id)?.let { add(stringResource(R.string.match_parts_asist_x, it)) } }
    if (e.code == "substitution") {
        val out = nameOf(JsonPrimitiveOrNull(p["player_out_id"])?.contentOrNull) ?: "?"
        val inn = nameOf(JsonPrimitiveOrNull(p["player_in_id"])?.contentOrNull) ?: "?"
        add(stringResource(R.string.match_parts_sale_x_entra_x, out, inn))
    }
}.joinToString(" · ")

/** «#9 Jorgito V.» o solo el nombre si no hay dorsal. Los cambios no llevan jugador propio. */
private fun eventPlayerLabel(e: TimelineEvent): String? =
    if (e.isControl || e.code == "substitution") null
    else e.playerName?.let { n -> e.jerseyNumber?.let { "#$it $n" } ?: n }

/** Etiquetas de estado de un evento: anulado, sin jugador, registrado tarde. */
@Composable
private fun EventPills(e: TimelineEvent) {
    if (e.isVoided) Pill(stringResource(R.string.match_parts_anulado), ErrorRed)
    if (!e.isControl && !e.isVoided && e.playerName == null && e.code != "substitution") Pill(stringResource(R.string.match_parts_sin_jugador_asignado), WarnAmber)
    if (e.recordedLate) Pill(stringResource(R.string.match_parts_registro_tardio), WarnAmber)
}

/** Línea compacta de evento (lista «Últimos registros»): «Tarjeta amarilla · Halcones · Jorgito V. · 23'». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineRow(
    e: TimelineEvent,
    sideName: (String) -> String,
    nameOf: (String?) -> String?,
    onLongPress: (() -> Unit)?,
) {
    val payloadText = eventDetailText(e, nameOf)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onLongPress != null) Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(e.minuteLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp))
        EventIcon(e.icon, e.code, size = 26.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = listOfNotNull(
                e.typeName,
                if (e.isControl) null else sideName(e.teamSide),
                eventPlayerLabel(e),
            ).joinToString(" · ")
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (e.isVoided) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (payloadText.isNotBlank()) Text(payloadText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { EventPills(e) }
            e.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Ancho de la columna central de la cronología, donde va el minuto. */
private val TimelineCenterWidth = 56.dp

/** Encabezado de la cronología por lados: local a la izquierda, visitante a la derecha. */
@Composable
fun TimelineSidesHeader(homeName: String, awayName: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            homeName, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(TimelineCenterWidth))
        Text(
            awayName, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Fila de la cronología separada por lados, igual que la pestaña «Alineación»:
 * el evento se dibuja en la mitad del equipo que lo hizo (local a la izquierda, visitante a la
 * derecha) y el minuto queda en el centro. Los eventos de control (inicio, entretiempo, final)
 * no son de ningún equipo y ocupan todo el ancho, centrados.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidedTimelineRow(
    e: TimelineEvent,
    nameOf: (String?) -> String?,
    onLongPress: (() -> Unit)?,
) {
    val press = if (onLongPress != null) Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress) else Modifier
    if (e.isControl) {
        Row(
            Modifier.fillMaxWidth().then(press).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EventIcon(e.icon, e.code, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                "${e.typeName} · ${e.minuteLabel}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (e.isVoided) TextDecoration.LineThrough else null,
            )
        }
        return
    }
    val isHome = e.teamSide == "home"
    Row(
        Modifier.fillMaxWidth().then(press).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (isHome) SidedEventBody(e, nameOf, alignEnd = true)
        }
        Text(
            e.minuteLabel,
            modifier = Modifier.width(TimelineCenterWidth),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            if (!isHome) SidedEventBody(e, nameOf, alignEnd = false)
        }
    }
}

/** Contenido de un evento dentro de su mitad: el icono siempre queda pegado al centro. */
@Composable
private fun RowScope.SidedEventBody(e: TimelineEvent, nameOf: (String?) -> String?, alignEnd: Boolean) {
    val detail = eventDetailText(e, nameOf)
    val player = eventPlayerLabel(e)
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    if (!alignEnd) {
        EventIcon(e.icon, e.code, size = 24.dp)
        Spacer(Modifier.width(8.dp))
    }
    Column(
        Modifier.weight(1f, fill = false),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            player ?: e.typeName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (e.isVoided) TextDecoration.LineThrough else null,
            textAlign = align,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Si arriba va el jugador, debajo va el tipo de evento con su detalle
        val second = listOfNotNull(if (player != null) e.typeName else null, detail.ifBlank { null }).joinToString(" · ")
        if (second.isNotBlank()) {
            Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { EventPills(e) }
        e.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
        }
    }
    if (alignEnd) {
        Spacer(Modifier.width(8.dp))
        EventIcon(e.icon, e.code, size = 24.dp)
    }
}

@Composable
fun PendingRow(p: PendingEvent) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(stringResource(R.string.match_parts_pendiente), WarnAmber)
        Spacer(Modifier.width(10.dp))
        Text(p.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// La alineación del partido se dibuja en la cancha: ver LivePitch.kt (LiveLineupTab).

/** Botón grande de evento con dibujo propio (§10: «dibujos interactivos», zona táctil de 48dp o más). */
@Composable
fun EventButton(name: String, icon: String?, code: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EventIcon(icon, code, size = 34.dp)
            Spacer(Modifier.size(6.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
