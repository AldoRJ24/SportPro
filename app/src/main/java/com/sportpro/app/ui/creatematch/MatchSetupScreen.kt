package com.sportpro.app.ui.creatematch

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.data.ExternalDraft
import com.sportpro.app.core.data.ExternalPlayer
import com.sportpro.app.core.data.Formations
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.ui.components.Avatar
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.FormationChips
import com.sportpro.app.ui.components.FormationCounter
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.PositionBadge
import com.sportpro.app.ui.components.PositionChips
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.roleLabel
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.ErrorRed
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.WarnAmber

@Composable
fun MatchSetupScreen(matchId: String, onBack: () -> Unit, onGoLive: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: MatchSetupViewModel = viewModel(key = "setup-$matchId") { MatchSetupViewModel(container, matchId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    val match = ui.match
    val editable = match?.isScheduled == true

    SpScaffold(title = "Convocatoria y alineación", onBack = onBack, snackbarHostState = snack) { pad ->
        when {
            ui.loading && match == null -> LoadingBox(Modifier.padding(pad))
            ui.error != null && match == null -> ErrorBox(ui.error!!, onRetry = vm::load, modifier = Modifier.padding(pad))
            match != null -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 40.dp)) {
                if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("${match.homeName} vs ${match.awayName}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${match.formatPlayers} vs ${match.formatPlayers} · 1) convoca · 2) confirma · 3) arma la formación · 4) guarda",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!editable) Text("El partido ya inició: la alineación no se puede cambiar.", color = WarnAmber)
                    }
                }
                ui.sides.forEach { side ->
                    // ---------------- Convocatoria ----------------
                    item { SectionHeader("1 · Convocatoria · ${side.teamName}") }
                    item {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.callAll(side) }, enabled = !ui.busy && editable) { Text("Convocar a todos") }
                            OutlinedButton(onClick = { vm.confirmAll(side) }, enabled = !ui.busy && editable) { Text("Marcar confirmados") }
                        }
                    }
                    item {
                        Text(
                            "Toca la posición para cambiar la posición habitual del jugador en el equipo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    items(side.roster, key = { "c" + side.side + it.playerId }) { tp ->
                        val callup = ui.callupOf(tp.playerId)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = callup != null,
                                onCheckedChange = { vm.toggleCallup(side, tp.playerId) },
                                enabled = !ui.busy && editable,
                            )
                            Text("${tp.jerseyNumber ?: "-"}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(28.dp))
                            Text(
                                tp.player?.fullName ?: "Jugador",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TeamPositionMenu(tp, enabled = !ui.busy) { p -> vm.setTeamPosition(side, tp, p) }
                            Spacer(Modifier.width(6.dp))
                            when (callup?.status) {
                                "confirmed" -> Pill("Confirmado", Mint400)
                                "declined" -> Pill("No asiste", ErrorRed)
                                "pending" -> Pill("Pendiente", WarnAmber)
                                else -> {}
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }

                    // ---------------- Formación y alineación ----------------
                    item { SectionHeader("2 · Formación y alineación · ${side.teamName}") }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Formación (arquero + defensas + medios + delanteros)", style = MaterialTheme.typography.labelLarge)
                            FormationChips(
                                options = Formations.forFormat(match.formatPlayers),
                                selected = side.formation,
                                onSelect = { vm.setFormation(side, it) },
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                            FormationCounter(side.formation, side.starterPositions, Modifier.horizontalScroll(rememberScrollState()))
                            Text(
                                "Solo pueden alinearse jugadores que confirmaron asistencia (US-09). Siempre debe haber 1 arquero.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.autoLineup(side) }, enabled = editable) { Text("Armar automático") }
                                Button(onClick = { vm.saveLineup(side) }, enabled = !ui.busy && editable) { Text("Guardar alineación") }
                            }
                        }
                    }
                    val confirmed = side.roster.filter { ui.callupOf(it.playerId)?.status == "confirmed" }
                    if (confirmed.isEmpty()) {
                        item {
                            Text(
                                "Aún no hay convocados confirmados.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(confirmed, key = { "l" + side.side + it.playerId }) { tp ->
                        val role = side.roles[tp.playerId] ?: LineupRole.OUT
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${tp.jerseyNumber ?: "-"}  ${tp.player?.fullName ?: ""}",
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    PositionBadge(Position.of(tp.position))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(selected = role == LineupRole.STARTER, onClick = { vm.setRole(side, tp.playerId, LineupRole.STARTER) }, label = { Text("Titular") }, enabled = editable)
                                    FilterChip(selected = role == LineupRole.SUB, onClick = { vm.setRole(side, tp.playerId, LineupRole.SUB) }, label = { Text("Suplente") }, enabled = editable)
                                    FilterChip(selected = role == LineupRole.OUT, onClick = { vm.setRole(side, tp.playerId, LineupRole.OUT) }, label = { Text("No juega") }, enabled = editable)
                                }
                                if (role == LineupRole.STARTER) {
                                    Text("Posición en este partido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    PositionChips(
                                        selected = side.positionOf(tp.playerId),
                                        onSelect = { vm.setMatchPosition(side, tp.playerId, it) },
                                        enabled = editable,
                                    )
                                }
                            }
                        }
                    }
                }

                // ---------------- Rival externo ----------------
                if (match.hasExternalRival) {
                    item { SectionHeader("Plantel del rival externo · ${match.awayName}") }
                    item {
                        val problems = ui.externalProblems(match.formatPlayers)
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (problems.isEmpty()) Pill("Plantel completo", Mint400)
                            else Text(problems.joinToString(" · "), color = WarnAmber, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(ui.external, key = { "x" + it.id }) { x ->
                        ExternalPlayerRow(
                            x = x,
                            editable = editable && !ui.busy,
                            onPosition = { p -> vm.updateExternal(x, position = p.code) },
                            onStarter = { s -> vm.updateExternal(x, starter = s) },
                            onDelete = { vm.deleteExternal(x) },
                        )
                    }
                    if (editable) item { AddExternalCard(onAdd = vm::addExternal) }
                }

                // ---------------- Operador ----------------
                item { SectionHeader("3 · Operador del partido") }
                item {
                    Text(
                        "Quien registra los eventos desde su celular (un solo operador activo). El DT siempre puede registrar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(ui.members, key = { "m" + it.userId }) { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = ui.operatorId == m.userId, onClick = { vm.setOperator(m.userId) }, enabled = !ui.busy)
                        Avatar(m.profile?.fullName ?: "?", m.profile?.avatarPath, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(m.profile?.fullName?.ifBlank { null } ?: "Miembro", modifier = Modifier.weight(1f))
                        Text(roleLabel(m.role), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                item {
                    Button(onClick = onGoLive, modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp)) {
                        Text("Ir al partido")
                    }
                }
            }
        }
    }
}

/** Badge that opens a menu to change the player's usual position in the team (saved on the server). */
@Composable
private fun TeamPositionMenu(tp: TeamPlayerRow, enabled: Boolean, onSelect: (Position) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) { PositionBadge(Position.of(tp.position)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Position.entries.forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = { open = false; onSelect(p) })
            }
        }
    }
}

@Composable
private fun ExternalPlayerRow(
    x: ExternalPlayer,
    editable: Boolean,
    onPosition: (Position) -> Unit,
    onStarter: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${x.jerseyNumber ?: "sin dorsal"}  ${x.name}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    color = if (x.jerseyNumber == null) WarnAmber else MaterialTheme.colorScheme.onSurface,
                )
                PositionBadge(Position.of(x.fieldPosition))
                if (editable) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Quitar") }
            }
            if (editable) {
                PositionChips(selected = Position.of(x.fieldPosition), onSelect = onPosition)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = x.isStarter, onClick = { onStarter(true) }, label = { Text("Titular") })
                    FilterChip(selected = !x.isStarter, onClick = { onStarter(false) }, label = { Text("Suplente") })
                }
            }
        }
    }
}

@Composable
private fun AddExternalCard(onAdd: (ExternalDraft, () -> Unit) -> Unit) {
    var row by remember { mutableStateOf(ExternalDraft()) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Agregar jugador al plantel rival", style = MaterialTheme.typography.labelLarge)
        ExternalRowEditor(row = row, onChange = { row = it }, onRemove = null)
        Button(onClick = { onAdd(row) { row = ExternalDraft() } }) { Text("Agregar") }
    }
}
