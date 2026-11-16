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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sportpro.app.R
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.core.data.formatDay
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.PositionBadge
import com.sportpro.app.ui.components.PositionChips
import com.sportpro.app.ui.components.ReasonDialog
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.teams.TeamInviteCard
import kotlinx.coroutines.launch

/**
 * Plantel del equipo. El DT (o el admin):
 *  - invita jugadores con el código / QR del equipo;
 *  - asigna la posición habitual y el dorsal de cada uno;
 *  - da de baja a un jugador con motivo (no se borra: pasa a la lista de «Bajas»).
 * Cada cambio lo valida el servidor; aquí solo se muestra el resultado o el error.
 */
@Composable
fun TeamRosterScreen(teamId: String, name: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var roster by remember { mutableStateOf<List<TeamPlayerRow>?>(null) }
    var former by remember { mutableStateOf<List<TeamPlayerRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    // Cambiar este número vuelve a cargar todo (reintentar, o refrescar tras una acción)
    var attempt by remember { mutableIntStateOf(0) }
    var joinCode by remember { mutableStateOf<String?>(null) }
    var editingJersey by remember { mutableStateOf<TeamPlayerRow?>(null) }
    var removing by remember { mutableStateOf<TeamPlayerRow?>(null) }

    LaunchedEffect(teamId, attempt) {
        error = null
        try { roster = container.club.teamPlayers(teamId) } catch (e: Exception) { error = e.friendlyMessage() }
        // Las bajas y el código son secundarios: si fallan, el plantel se muestra igual
        former = runCatching { container.club.formerTeamPlayers(teamId) }.getOrDefault(former)
        // Código para invitar jugadores (solo el DT/admin del equipo lo obtiene)
        joinCode = runCatching { container.club.teamJoinCode(teamId) }.getOrNull()
    }

    /** Ejecuta una acción del servidor; si sale bien muestra [doneRes] y recarga el plantel. */
    fun act(doneRes: Int, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                attempt++
                snack.showSnackbar(resources.getString(doneRes))
            } catch (e: Exception) {
                snack.showSnackbar(e.friendlyMessage())
            }
        }
    }

    SpScaffold(title = stringResource(R.string.roster_title, name), onBack = onBack, snackbarHostState = snack) { pad ->
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
                                    snack.showSnackbar(resources.getString(R.string.roster_code_regenerated))
                                } catch (e: Exception) {
                                    snack.showSnackbar(e.friendlyMessage())
                                }
                            }
                        },
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (list.isEmpty()) {
                    item {
                        EmptyState(
                            stringResource(R.string.roster_empty_title),
                            stringResource(R.string.roster_empty_body),
                        )
                    }
                } else {
                    item {
                        val counts = Position.entries.joinToString(" · ") { p -> "${p.short} ${list.count { it.position == p.code }}" }
                        Text(
                            stringResource(R.string.roster_hint, counts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { tp ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // El dorsal es un botón: tocarlo abre el diálogo para cambiarlo
                                OutlinedButton(
                                    onClick = { editingJersey = tp },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) { Text(tp.jerseyNumber?.toString() ?: stringResource(R.string.roster_no_jersey)) }
                                Spacer(Modifier.width(10.dp))
                                Text(tp.player?.fullName ?: stringResource(R.string.roster_player_fallback), modifier = Modifier.weight(1f))
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
                            TextButton(onClick = { removing = tp }) { Text(stringResource(R.string.roster_remove)) }
                        }
                    }
                }

                // ---- Bajas: historial de quienes salieron del equipo y por qué ----
                if (former.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.roster_former_header)) }
                    items(former, key = { "f" + it.id }) { tp ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(tp.player?.fullName ?: stringResource(R.string.roster_player_fallback), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.roster_former_line, formatDay(tp.leftAt), tp.inactiveReason.orEmpty()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    editingJersey?.let { tp ->
        JerseyDialog(
            playerName = tp.player?.fullName.orEmpty(),
            initial = tp.jerseyNumber,
            onDismiss = { editingJersey = null },
            onSave = { number ->
                editingJersey = null
                act(R.string.roster_jersey_saved) { container.club.setJersey(tp.id, number) }
            },
        )
    }
    removing?.let { tp ->
        ReasonDialog(
            title = stringResource(R.string.roster_remove_title, tp.player?.fullName.orEmpty()),
            onDismiss = { removing = null },
            onSubmit = { reason ->
                removing = null
                act(R.string.roster_removed) { container.club.removeTeamPlayer(tp.id, reason) }
            },
        )
    }
}

/** Diálogo del dorsal: un número de 0 a 99, o vacío para dejar al jugador sin dorsal. */
@Composable
private fun JerseyDialog(playerName: String, initial: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.toString() ?: "") }
    val number = text.toIntOrNull()
    val valid = text.isEmpty() || (number != null && number in 0..99)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.roster_jersey_title, playerName)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(2) },
                label = { Text(stringResource(R.string.roster_jersey_label)) },
                singleLine = true,
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(number) }, enabled = valid) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
