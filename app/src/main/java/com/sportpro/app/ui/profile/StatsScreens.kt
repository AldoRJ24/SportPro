package com.sportpro.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportpro.app.core.data.PlayerStats
import com.sportpro.app.core.data.TeamStats
import com.sportpro.app.core.data.friendlyMessage
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer

/** Una casilla de estadística: el número grande y debajo su nombre. */
@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(14.dp)) {
            Text(value.toString(), fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Cuadrícula de casillas de estadística (dos por fila) con la nota de cómo se calculan. */
@Composable
private fun StatGrid(tiles: List<Pair<String, Int>>) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
        Text(
            stringResource(R.string.stats_calculadas_en_el_servidor_a),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** US-11: el jugador ve sus estadísticas, el DT las del equipo y el apoderado las de su hijo (lo hace cumplir la función del servidor). */
@Composable
fun PlayerStatsScreen(playerId: String, name: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    var stats by remember { mutableStateOf<PlayerStats?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(playerId, attempt) {
        error = null
        try { stats = container.club.playerStats(playerId) } catch (e: Exception) { error = e.friendlyMessage() }
    }
    SpScaffold(title = name, onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxWidth().verticalScroll(rememberScrollState())) {
            val s = stats
            when {
                error != null -> ErrorBox(error!!, onRetry = { attempt++ })
                s == null -> LoadingBox()
                else -> {
                    // Sin partidos finalizados todos los números son 0: se explica por qué
                    if (s.matchesPlayed == 0) {
                        EmptyState(stringResource(R.string.stats_empty_title), stringResource(R.string.stats_empty_body))
                    }
                    StatGrid(
                    listOf(
                        stringResource(R.string.stats_partidos) to s.matchesPlayed, stringResource(R.string.stats_goles) to s.goals, stringResource(R.string.stats_asistencias) to s.assists,
                        stringResource(R.string.stats_amarillas) to s.yellowCards, stringResource(R.string.stats_rojas) to s.redCards, stringResource(R.string.stats_faltas) to s.fouls,
                        stringResource(R.string.stats_atajadas) to s.saves, stringResource(R.string.stats_goles_en_contra) to s.ownGoals,
                    ),
                    )
                }
            }
        }
    }
}

/** Estadísticas del equipo: partidos jugados, ganados, empatados, perdidos y goles. Las calcula el servidor. */
@Composable
fun TeamStatsScreen(teamId: String, name: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    var stats by remember { mutableStateOf<TeamStats?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(teamId, attempt) {
        error = null
        try { stats = container.club.teamStats(teamId) } catch (e: Exception) { error = e.friendlyMessage() }
    }
    SpScaffold(title = name, onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxWidth().verticalScroll(rememberScrollState())) {
            val s = stats
            when {
                error != null -> ErrorBox(error!!, onRetry = { attempt++ })
                s == null -> LoadingBox()
                else -> {
                    // Sin partidos finalizados todos los números son 0: se explica por qué
                    if (s.matchesPlayed == 0) {
                        EmptyState(stringResource(R.string.stats_empty_title), stringResource(R.string.stats_empty_body))
                    }
                    StatGrid(
                    listOf(
                        stringResource(R.string.stats_partidos) to s.matchesPlayed, stringResource(R.string.stats_ganados) to s.wins, stringResource(R.string.stats_empatados) to s.draws,
                        stringResource(R.string.stats_perdidos) to s.losses, stringResource(R.string.stats_goles_a_favor) to s.goalsFor, stringResource(R.string.stats_goles_en_contra) to s.goalsAgainst,
                    ),
                    )
                }
            }
        }
    }
}
