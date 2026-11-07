package com.sportpro.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.PositionBadge
import com.sportpro.app.ui.components.PositionChips
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.teams.TeamInviteCard
import kotlinx.coroutines.launch

/** Plantel del equipo: el DT invita jugadores (código/QR) y asigna la posición habitual de cada uno. */
@Composable
fun TeamRosterScreen(teamId: String, name: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var roster by remember { mutableStateOf<List<TeamPlayerRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var joinCode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(teamId, attempt) {
        error = null
        try { roster = container.club.teamPlayers(teamId) } catch (e: Exception) { error = e.friendlyMessage() }
        // Código para invitar jugadores (solo el DT/admin del equipo lo obtiene)
        joinCode = runCatching { container.club.teamJoinCode(teamId) }.getOrNull()
    }
    SpScaffold(title = "Plantel · $name", onBack = onBack, snackbarHostState = snack) { pad ->
        val list = roster
        when {
            error != null && list == null -> ErrorBox(error!!, onRetry = { attempt++ }, modifier = Modifier.padding(pad))
            list == null -> LoadingBox(Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    TeamInviteCard(
                        code = joinCode,
                        onRegenerate = {
                            scope.launch {
                                try {
                                    joinCode = container.club.teamJoinCode(teamId, regenerate = true)
                                    snack.showSnackbar("Nuevo código generado: el anterior ya no sirve")
                                } catch (e: Exception) {
                                    snack.showSnackbar(e.friendlyMessage())
                                }
                            }
                        },
                        modifier = Modifier.padding(16.dp),
                    )
                }
                item {
                    val counts = Position.entries.joinToString(" · ") { p -> "${p.short} ${list.count { it.position == p.code }}" }
                    Text(
                        "Posición habitual de cada jugador (se usa para armar la alineación). $counts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(list, key = { it.id }) { tp ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${tp.jerseyNumber ?: "-"}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(32.dp))
                                Text(tp.player?.fullName ?: "Jugador", modifier = Modifier.weight(1f))
                                PositionBadge(Position.of(tp.position))
                                Spacer(Modifier.width(4.dp))
                            }
                            PositionChips(
                                selected = Position.of(tp.position),
                                onSelect = { p ->
                                    scope.launch {
                                        try {
                                            container.club.setTeamPlayerPosition(tp.id, p.code)
                                            roster = list.map { if (it.id == tp.id) it.copy(position = p.code) else it }
                                        } catch (e: Exception) {
                                            snack.showSnackbar(e.friendlyMessage())
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
