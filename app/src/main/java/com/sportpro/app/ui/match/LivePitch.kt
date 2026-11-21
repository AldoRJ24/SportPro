package com.sportpro.app.ui.match

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportpro.app.R
import com.sportpro.app.core.data.LiveLineup
import com.sportpro.app.core.data.LiveSide
import com.sportpro.app.core.data.PlayerMatchStats
import com.sportpro.app.core.data.lineupsPending
import com.sportpro.app.ui.components.BenchRow
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.FullPitchBoard
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.components.TokenInfo
import com.sportpro.app.ui.components.tokenSizeFor
import com.sportpro.app.ui.theme.BrandBlue
import com.sportpro.app.ui.theme.ErrorRed
import com.sportpro.app.ui.theme.Ink
import com.sportpro.app.ui.theme.SuccessGreen

/**
 * Pestaña «Alineación» del partido: la cancha completa con los dos equipos (local arriba,
 * visitante abajo) y los suplentes afuera. Sobre cada ficha aparecen sus goles, asistencias,
 * tarjetas y cambios, y la estrella azul del MVP. Todo se calcula de la cronología, que se
 * actualiza por Realtime: cuando alguien registra un evento, los íconos cambian solos.
 */
@Composable
fun LiveLineupTab(ui: MatchDetailUi) {
    val match = ui.match ?: return
    // Partido acordado: hasta la confirmación, la alineación ajena es secreta
    if (match.lineupsPending) {
        EmptyState(stringResource(R.string.match_detail_alineacion), stringResource(R.string.lineup_published_later))
        return
    }
    if (ui.lineup.isEmpty()) {
        EmptyState(stringResource(R.string.match_detail_alineacion), stringResource(R.string.match_parts_sin_alineacion_registrada))
        return
    }
    val format = match.formatPlayers
    // Se recalcula solo cuando cambian la alineación, la cronología o las formaciones
    val home = remember(ui.lineup, ui.timeline, ui.formations) {
        LiveLineup.side(format, ui.formationOf("home"), ui.lineup, ui.timeline, "home")
    }
    val away = remember(ui.lineup, ui.timeline, ui.formations) {
        LiveLineup.side(format, ui.formationOf("away"), ui.lineup, ui.timeline, "away")
    }
    val stats = remember(ui.timeline) { LiveLineup.stats(ui.timeline) }
    val mvp = remember(ui.timeline) { LiveLineup.mvp(ui.timeline) }
    val token: (String) -> TokenInfo? = { key ->
        ui.lineup.firstOrNull { it.key == key }?.let { TokenInfo(it.playerName ?: "?", it.photoPath, it.jerseyNumber) }
    }
    val badges: @Composable BoxScope.(String) -> Unit = { key -> PlayerEventBadges(stats[key], key == mvp) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TeamBar(match.homeName, match.home?.crestPath, home)
        FullPitchBoard(
            home = home.slots,
            away = away.slots,
            player = token,
            tokenSize = tokenSizeFor(format),
            modifier = Modifier.padding(horizontal = 8.dp),
            dimmed = { stats[it]?.sentOff == true },
            badges = badges,
        )
        // Espacio para el nombre del arquero visitante, que queda pegado al borde de abajo
        Spacer(Modifier.height(18.dp))
        TeamBar(match.awayName, match.away?.crestPath, away)

        mvp?.let { key ->
            val name = token(key)?.name ?: "?"
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MvpStar(20.dp)
                Text(
                    stringResource(R.string.live_mvp, name) + " · " +
                        pluralStringResource(R.plurals.live_goals, stats[key]?.goals ?: 0, stats[key]?.goals ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        listOf(match.homeName to home, match.awayName to away).forEach { (name, side) ->
            if (side.bench.isNotEmpty()) {
                SectionHeader(stringResource(R.string.live_bench, name))
                BenchRow(side.bench, token, Modifier.padding(horizontal = 12.dp), badges = badges)
            }
        }
        Text(
            stringResource(R.string.live_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Escudo, nombre del equipo y su formación (si tiene jugadores en la cancha). */
@Composable
private fun TeamBar(name: String, crest: String?, side: LiveSide) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamCrest(name, crest, 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (side.slots.any { it.playerId != null }) {
            Text(side.formation.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Íconos alrededor de la foto de un jugador:
 * - arriba a la derecha: goles (pelota), goles en contra (pelota roja) y asistencias (botín),
 *   con el número si son más de uno;
 * - arriba a la izquierda: tarjetas y, debajo, el cambio (verde si entró, rojo si salió);
 * - abajo a la izquierda: estrella azul si es el MVP.
 */
@Composable
fun BoxScope.PlayerEventBadges(stats: PlayerMatchStats?, isMvp: Boolean) {
    if (stats != null) {
        Column(
            Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-4).dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (stats.goals > 0) CountBadge(R.drawable.ic_ev_ball, stats.goals, Ink)
            if (stats.ownGoals > 0) CountBadge(R.drawable.ic_ev_ball, stats.ownGoals, ErrorRed)
            if (stats.assists > 0) CountBadge(R.drawable.ic_ev_assist, stats.assists, Ink)
        }
        Column(
            Modifier.align(Alignment.TopStart).offset(x = (-8).dp, y = (-4).dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when {
                stats.reds > 0 || stats.yellows >= 2 -> CardMark(R.drawable.ic_ev_card_red)
                stats.yellows == 1 -> CardMark(R.drawable.ic_ev_card_yellow)
            }
            if (stats.subIn || stats.subOut) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(if (stats.subOut) ErrorRed else SuccessGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_ev_swap), contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
    if (isMvp) MvpStar(18.dp, Modifier.align(Alignment.BottomStart).offset(x = (-6).dp, y = 2.dp))
}

// Círculo oscuro con el ícono del evento y, si hay más de uno, el número al lado.
@Composable
private fun CountBadge(icon: Int, count: Int, background: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
        if (count > 1) {
            Box(
                Modifier.padding(start = 1.dp).size(14.dp).clip(CircleShape).background(background),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Tarjeta amarilla o roja (los dibujos ya traen su color).
@Composable
private fun CardMark(icon: Int) {
    Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(15.dp))
}

/** Estrella azul del MVP. */
@Composable
fun MvpStar(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(BrandBlue), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.live_mvp_short), tint = Color.White, modifier = Modifier.size(size * 0.7f))
    }
}
