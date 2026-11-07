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
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text(value.toString(), fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
            "Calculadas en el servidor a partir de los eventos vigentes de partidos finalizados (las correcciones y anulaciones se reflejan sin duplicar).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** US-11: JUG sees own stats, DT the team's, PAD their child's (enforced by the server function). */
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
                else -> StatGrid(
                    listOf(
                        "Partidos" to s.matchesPlayed, "Goles" to s.goals, "Asistencias" to s.assists,
                        "Amarillas" to s.yellowCards, "Rojas" to s.redCards, "Faltas" to s.fouls,
                        "Atajadas" to s.saves, "Goles en contra" to s.ownGoals,
                    ),
                )
            }
        }
    }
}

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
                else -> StatGrid(
                    listOf(
                        "Partidos" to s.matchesPlayed, "Ganados" to s.wins, "Empatados" to s.draws,
                        "Perdidos" to s.losses, "Goles a favor" to s.goalsFor, "Goles en contra" to s.goalsAgainst,
                    ),
                )
            }
        }
    }
}
