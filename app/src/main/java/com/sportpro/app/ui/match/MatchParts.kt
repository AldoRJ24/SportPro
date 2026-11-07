package com.sportpro.app.ui.match

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    "${match.formatPlayers} vs ${match.formatPlayers}",
                    "${match.halfMinutes}' por tiempo",
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

/** One chronology line: «Tarjeta amarilla · Halcones · Jorgito V. · 23'». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineRow(
    e: TimelineEvent,
    sideName: (String) -> String,
    nameOf: (String?) -> String?,
    onLongPress: (() -> Unit)?,
) {
    val payloadText = buildList {
        val p = e.payload
        if (JsonPrimitiveOrNull(p["own_goal"])?.booleanOrNull == true) add("en contra")
        when (JsonPrimitiveOrNull(p["result"])?.contentOrNull) {
            "converted" -> add("convertido")
            "missed" -> add("fallado")
            "saved" -> add("atajado")
        }
        JsonPrimitiveOrNull(p["assist_player_id"])?.contentOrNull?.let { id -> nameOf(id)?.let { add("asist. $it") } }
        if (e.code == "substitution") {
            val out = nameOf(JsonPrimitiveOrNull(p["player_out_id"])?.contentOrNull) ?: "?"
            val inn = nameOf(JsonPrimitiveOrNull(p["player_in_id"])?.contentOrNull) ?: "?"
            add("sale $out, entra $inn")
        }
    }.joinToString(" · ")
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
                if (e.isControl || e.code == "substitution") null else (e.playerName?.let { n -> e.jerseyNumber?.let { "#$it $n" } ?: n }),
            ).joinToString(" · ")
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (e.isVoided) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (payloadText.isNotBlank()) Text(payloadText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e.isVoided) Pill("ANULADO", ErrorRed)
                if (!e.isControl && !e.isVoided && e.playerName == null && e.code != "substitution") Pill("Sin jugador asignado", WarnAmber)
                if (e.recordedLate) Pill("Registro tardío", WarnAmber)
            }
            e.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PendingRow(p: PendingEvent) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill("PENDIENTE", WarnAmber)
        Spacer(Modifier.width(10.dp))
        Text(p.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LineupColumn(title: String, players: List<LineupPlayer>, modifier: Modifier = Modifier) {
    Column(modifier.padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
        val starters = players.filter { it.isStarter }
            .sortedWith(compareBy({ Position.of(it.fieldPosition)?.ordinal ?: 9 }, { it.jerseyNumber ?: 99 }))
        val subs = players.filterNot { it.isStarter }
        Text("Titulares (${starters.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        starters.forEach { PlayerLine(it) }
        if (subs.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Suplentes (${subs.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            subs.forEach { PlayerLine(it) }
        }
        if (players.isEmpty()) Text("Sin alineación registrada", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlayerLine(p: LineupPlayer) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(p.jerseyNumber?.toString() ?: "-", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(28.dp))
        PositionBadge(Position.of(p.fieldPosition))
        Spacer(Modifier.width(6.dp))
        Text(p.playerName ?: "Jugador", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Big tappable event button with own drawing (§10: «dibujos interactivos», ≥48dp targets). */
@Composable
fun EventButton(name: String, icon: String?, code: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
