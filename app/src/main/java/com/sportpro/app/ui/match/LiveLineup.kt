package com.sportpro.app.ui.match

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportpro.app.R
import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TimelineEvent
import com.sportpro.app.ui.theme.Carbon950
import com.sportpro.app.ui.theme.CardYellow
import com.sportpro.app.ui.theme.Crimson400
import com.sportpro.app.ui.theme.LiveRed
import com.sportpro.app.ui.theme.PitchGreen
import com.sportpro.app.ui.theme.PitchGreenDark
import com.sportpro.app.ui.theme.TextHigh
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Lo que hizo cada jugador en el partido, calculado desde la cronología en vivo. */
private data class PlayerMatchStats(val goals: Int = 0, val yellows: Int = 0, val cameIn: Boolean = false)

private fun TimelineEvent.isOwnGoal(): Boolean =
    (payload["own_goal"] as? JsonPrimitive)?.booleanOrNull == true

private fun playerStats(timeline: List<TimelineEvent>, side: String): Map<String, PlayerMatchStats> {
    val out = mutableMapOf<String, PlayerMatchStats>()
    timeline.filter { !it.isVoided && it.teamSide == side }.forEach { e ->
        when (e.code) {
            "goal" -> if (!e.isOwnGoal()) e.actorKey?.let { k ->
                out[k] = (out[k] ?: PlayerMatchStats()).let { it.copy(goals = it.goals + 1) }
            }
            "penalty" -> if (e.payloadString("result") == "converted") e.actorKey?.let { k ->
                out[k] = (out[k] ?: PlayerMatchStats()).let { it.copy(goals = it.goals + 1) }
            }
            "yellow_card" -> e.actorKey?.let { k ->
                out[k] = (out[k] ?: PlayerMatchStats()).let { it.copy(yellows = it.yellows + 1) }
            }
            "substitution" -> e.payloadString("player_in_id")?.let { k ->
                out[k] = (out[k] ?: PlayerMatchStats()).copy(cameIn = true)
            }
        }
    }
    return out
}

// Altura de cada línea en la cancha (0 = arco rival, 1 = arco propio).
private fun lineY(p: Position?): Float = when (p) {
    Position.POR -> 0.88f
    Position.DEF -> 0.68f
    Position.MED, null -> 0.46f
    Position.DEL -> 0.22f
}

private fun formationLabel(field: List<LineupPlayer>): String {
    if (field.isEmpty()) return "—"
    val byPos = field.groupingBy { Position.of(it.fieldPosition) }.eachCount()
    val lines = listOf(Position.DEF, Position.MED, Position.DEL).map { byPos[it] ?: 0 }.filter { it > 0 }
    return if (lines.isEmpty()) "—" else lines.joinToString("-")
}

/** Pestaña «Alineación»: quién está en cancha ahora, dibujado sobre el campo y actualizado en vivo. */
@Composable
fun LiveLineupTab(ui: MatchDetailUi, modifier: Modifier = Modifier) {
    val match = ui.match ?: return
    var side by rememberSaveable { mutableStateOf("home") }
    val field = ui.fieldPlayers(side)
    val bench = ui.benchPlayers(side)
    val stats = remember(ui.timeline, side) { playerStats(ui.timeline, side) }
    val shirt = if (side == "home") Crimson400 else TextHigh
    val shirtText = if (side == "home") Color.White else Carbon950

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("home", "away").forEachIndexed { i, s ->
                SegmentedButton(
                    selected = side == s,
                    onClick = { side = s },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                ) { Text(ui.sideName(s), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (match.isLive) {
                LiveChip()
                Spacer(Modifier.width(10.dp))
            }
            Text(
                "EN CANCHA · ${field.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formationLabel(field),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Pitch(field, stats, shirt, shirtText)

        if (field.isEmpty()) {
            Text(
                "Sin alineación registrada para ${ui.sideName(side)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (bench.isNotEmpty()) {
            Text("BANCA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            bench.forEach { p -> BenchRow(p, shirt, shirtText) }
        }
    }
}

@Composable
private fun LiveChip() {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        Modifier
            .background(LiveRed.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(pulse).background(LiveRed, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text("EN VIVO", style = MaterialTheme.typography.labelMedium, color = LiveRed)
    }
}

@Composable
private fun Pitch(
    field: List<LineupPlayer>,
    stats: Map<String, PlayerMatchStats>,
    shirt: Color,
    shirtText: Color,
) {
    val rows = field.groupBy { lineY(Position.of(it.fieldPosition)) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(PitchGreen, PitchGreenDark))),
    ) {
        PitchLines(Modifier.fillMaxSize())
        val w = maxWidth
        val h = maxHeight
        rows.forEach { (y, players) ->
            players.forEachIndexed { i, p ->
                val x = (i + 1f) / (players.size + 1f)
                PlayerToken(
                    player = p,
                    stats = stats[p.key] ?: PlayerMatchStats(),
                    shirt = shirt,
                    shirtText = shirtText,
                    modifier = Modifier.offset(x = w * x - 36.dp, y = h * y - 20.dp),
                )
            }
        }
    }
}

@Composable
private fun PitchLines(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val line = Color.White.copy(alpha = 0.55f)
        val stroke = 2.dp.toPx()
        val m = 12.dp.toPx()
        // Franjas de césped
        val stripes = 8
        val stripeH = size.height / stripes
        for (i in 0 until stripes step 2) {
            drawRect(Color.White.copy(alpha = 0.04f), Offset(0f, i * stripeH), Size(size.width, stripeH))
        }
        drawRect(line, Offset(m, m), Size(size.width - 2 * m, size.height - 2 * m), style = Stroke(stroke))
        drawLine(line, Offset(m, size.height / 2), Offset(size.width - m, size.height / 2), stroke)
        drawCircle(line, radius = size.width * 0.15f, center = center, style = Stroke(stroke))
        drawCircle(line, radius = 3.dp.toPx(), center = center)
        val areaW = size.width * 0.56f
        val areaH = size.height * 0.14f
        val smallW = size.width * 0.28f
        val smallH = size.height * 0.06f
        drawRect(line, Offset((size.width - areaW) / 2, m), Size(areaW, areaH), style = Stroke(stroke))
        drawRect(line, Offset((size.width - areaW) / 2, size.height - m - areaH), Size(areaW, areaH), style = Stroke(stroke))
        drawRect(line, Offset((size.width - smallW) / 2, m), Size(smallW, smallH), style = Stroke(stroke))
        drawRect(line, Offset((size.width - smallW) / 2, size.height - m - smallH), Size(smallW, smallH), style = Stroke(stroke))
    }
}

@Composable
private fun PlayerToken(
    player: LineupPlayer,
    stats: PlayerMatchStats,
    shirt: Color,
    shirtText: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier
                    .size(40.dp)
                    .shadow(6.dp, CircleShape)
                    .background(shirt, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    player.jerseyNumber?.toString() ?: "–",
                    color = shirtText,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                )
            }
            if (stats.yellows > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(width = 9.dp, height = 12.dp)
                        .background(CardYellow, RoundedCornerShape(2.dp)),
                )
            }
            if (stats.cameIn) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-2).dp)
                        .size(14.dp)
                        .background(Color(0xFF2ECC71), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("▲", color = Color.White, fontSize = 7.sp) }
            }
            if (stats.goals > 0) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 8.dp, y = 4.dp)
                        .background(Carbon950, RoundedCornerShape(50))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(R.drawable.ic_ev_ball), contentDescription = "Goles", modifier = Modifier.size(12.dp))
                    if (stats.goals > 1) Text("${stats.goals}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            player.playerName?.substringBefore(' ') ?: "Jugador",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun BenchRow(p: LineupPlayer, shirt: Color, shirtText: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            Modifier.size(30.dp).background(shirt.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(p.jerseyNumber?.toString() ?: "–", color = shirtText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(p.playerName ?: "Jugador", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
