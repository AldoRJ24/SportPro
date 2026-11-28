package com.sportpro.app.ui.creatematch
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sportpro.app.core.data.ExternalDraft
import com.sportpro.app.core.data.ExternalPlayer
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.ui.components.Avatar
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.HalfPitchBoard
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.tokenSizeFor
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.rememberNowMs
import com.sportpro.app.core.data.LineupStatus
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.ui.components.PositionBadge
import com.sportpro.app.ui.components.PositionChips
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.roleLabel
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.ErrorRed
import com.sportpro.app.ui.theme.SuccessGreen
import com.sportpro.app.ui.theme.WarnAmber

@Composable
fun MatchSetupScreen(matchId: String, onBack: () -> Unit, onOpenEditor: (String) -> Unit, onGoLive: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: MatchSetupViewModel = viewModel(key = "setup-$matchId") { MatchSetupViewModel(container, matchId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Cada vez que la pantalla aparece (también al volver del editor de la cancha) se recarga
    LifecycleResumeEffect(vm) {
        vm.load()
        onPauseOrDispose { }
    }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    val match = ui.match
    // Se recalcula cada minuto: al pasar la hora límite los botones se apagan solos
    val now = rememberNowMs(60_000)
    val editable = ui.editable(now)
    val status = ui.status

    SpScaffold(title = stringResource(R.string.match_setup_convocatoria_y_alineacion), onBack = onBack, snackbarHostState = snack) { pad ->
        when {
            ui.loading && match == null -> LoadingBox(Modifier.padding(pad))
            ui.error != null && match == null -> ErrorBox(ui.error!!, onRetry = vm::load, modifier = Modifier.padding(pad))
            match != null -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 40.dp)) {
                if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.match_setup_x_vs_x, match.homeName, match.awayName), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.match_setup_x_vs_x_1_convoca, match.formatPlayers, match.formatPlayers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (status?.flow == true) {
                            LineupFlowInfo(status, ui.sides.map { it.side })
                        } else if (!editable) {
                            Text(stringResource(R.string.match_setup_el_partido_ya_inicio_la), color = WarnAmber)
                        }
                    }
                }
                ui.sides.forEach { side ->
                    // ---------------- Convocatoria ----------------
                    item { SectionHeader(stringResource(R.string.match_setup_1_convocatoria_x, side.teamName)) }
                    item {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.callAll(side) }, enabled = !ui.busy && editable) { Text(stringResource(R.string.match_setup_convocar_a_todos)) }
                            OutlinedButton(onClick = { vm.confirmAll(side) }, enabled = !ui.busy && editable) { Text(stringResource(R.string.match_setup_marcar_confirmados)) }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.match_setup_toca_la_posicion_para_cambiar),
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
                                tp.player?.fullName ?: stringResource(R.string.match_setup_jugador),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TeamPositionMenu(tp, enabled = !ui.busy) { p -> vm.setTeamPosition(side, tp, p) }
                            Spacer(Modifier.width(6.dp))
                            when (callup?.status) {
                                "confirmed" -> Pill(stringResource(R.string.match_setup_confirmado), SuccessGreen)
                                "declined" -> Pill(stringResource(R.string.match_setup_no_asiste), ErrorRed)
                                "pending" -> Pill(stringResource(R.string.match_setup_pendiente), WarnAmber)
                                else -> {}
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }

                    // ---------------- Alineación en la cancha (migración 018) ----------------
                    item { SectionHeader(stringResource(R.string.match_setup_2_formacion_y_alineacion_x, side.teamName)) }
                    item {
                        val confirmedCount = side.roster.count { ui.callupOf(it.playerId)?.status == "confirmed" }
                        LineupPreview(
                            side = side,
                            format = match.formatPlayers,
                            confirmedCount = confirmedCount,
                            editable = editable,
                            onOpen = { onOpenEditor(side.side) },
                        )
                    }
                }

                // ---------------- Rival externo ----------------
                if (match.hasExternalRival) {
                    item { SectionHeader(stringResource(R.string.match_setup_plantel_del_rival_externo_x, match.awayName)) }
                    item {
                        val problems = ui.externalProblems(match.formatPlayers)
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (problems.isEmpty()) Pill(stringResource(R.string.match_setup_plantel_completo), SuccessGreen)
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

                // ---------------- Operador (solo el DT local) ----------------
                if (ui.canManageHome) item { SectionHeader(stringResource(R.string.match_setup_3_operador_del_partido)) }
                if (ui.canManageHome) item {
                    Text(
                        stringResource(R.string.match_setup_quien_registra_los_eventos_desde),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (ui.canManageHome) items(ui.members, key = { "m" + it.userId }) { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = ui.operatorId == m.userId, onClick = { vm.setOperator(m.userId) }, enabled = !ui.busy)
                        Avatar(m.profile?.fullName ?: "?", m.profile?.avatarPath, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(m.profile?.fullName?.ifBlank { null } ?: stringResource(R.string.match_setup_miembro), modifier = Modifier.weight(1f))
                        Text(roleLabel(m.role), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                item {
                    Button(onClick = onGoLive, modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp)) {
                        Text(stringResource(R.string.match_setup_ir_al_partido))
                    }
                }
            }
        }
    }
}

/** Etiqueta que abre un menú para cambiar la posición habitual del jugador en el equipo (se guarda en el servidor). */
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${x.jerseyNumber ?: stringResource(R.string.match_setup_no_jersey)}  ${x.name}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    color = if (x.jerseyNumber == null) WarnAmber else MaterialTheme.colorScheme.onSurface,
                )
                PositionBadge(Position.of(x.fieldPosition))
                if (editable) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.match_setup_quitar)) }
            }
            if (editable) {
                PositionChips(selected = Position.of(x.fieldPosition), onSelect = onPosition)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = x.isStarter, onClick = { onStarter(true) }, label = { Text(stringResource(R.string.match_setup_titular)) })
                    FilterChip(selected = !x.isStarter, onClick = { onStarter(false) }, label = { Text(stringResource(R.string.match_setup_suplente)) })
                }
            }
        }
    }
}

@Composable
private fun AddExternalCard(onAdd: (ExternalDraft, () -> Unit) -> Unit) {
    var row by remember { mutableStateOf(ExternalDraft()) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.match_setup_agregar_jugador_al_plantel_rival), style = MaterialTheme.typography.labelLarge)
        ExternalRowEditor(row = row, onChange = { row = it }, onRemove = null)
        Button(onClick = { onAdd(row) { row = ExternalDraft() } }) { Text(stringResource(R.string.match_setup_agregar)) }
    }
}

/**
 * Vista previa de la alineación guardada de un lado: formación, titulares y suplentes, la
 * mitad de la cancha de ese equipo (solo lectura) y el botón para abrir el editor.
 */
@Composable
private fun LineupPreview(side: SideSetup, format: Int, confirmedCount: Int, editable: Boolean, onOpen: () -> Unit) {
    val (formation, slots) = side.preview(format)
    val starters = side.starters.size
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (starters == 0) {
            Text(
                stringResource(
                    if (confirmedCount == 0) R.string.match_setup_aun_no_hay_convocados_confirmados
                    else R.string.lineup_preview_empty,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formation.label, style = MaterialTheme.typography.titleMedium)
                Pill(
                    stringResource(R.string.lineup_editor_starters, starters, format),
                    if (starters == format) SuccessGreen else WarnAmber,
                )
                Text(
                    pluralStringResource(R.plurals.lineup_preview_subs, side.subs, side.subs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HalfPitchBoard(
                slots = slots,
                top = side.side == "home",
                player = side::token,
                tokenSize = tokenSizeFor(format) - 6.dp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
            )
        }
        Text(
            stringResource(R.string.match_setup_solo_pueden_alinearse_jugadores_que),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (editable) R.string.lineup_preview_open else R.string.lineup_preview_view))
        }
    }
}

/**
 * Partido acordado por desafío: hasta cuándo se puede editar y el estado de cada lado.
 * La alineación del rival no se muestra (es secreta hasta la confirmación); solo si ya la envió.
 */
@Composable
private fun LineupFlowInfo(status: LineupStatus, mySides: List<String>) {
    val small = MaterialTheme.typography.bodySmall
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (status.confirmed) {
            Pill(stringResource(R.string.lineup_confirmed_pill), SuccessGreen)
            Text(stringResource(R.string.lineup_confirmed_help), style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.lineup_deadline, formatDayTime(status.deadline)), style = small, color = WarnAmber)
            Text(stringResource(R.string.lineup_flow_help), style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status.sides.forEach { s ->
            val mine = s.side in mySides
            val label = stringResource(if (mine) R.string.lineup_mine else R.string.lineup_rival)
            val state = when {
                s.autoFilled -> stringResource(R.string.lineup_state_autofilled)
                s.submitted -> stringResource(R.string.lineup_state_sent)
                else -> stringResource(R.string.lineup_state_not_sent)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Pill(state, if (s.submitted || s.autoFilled) SuccessGreen else WarnAmber)
            }
        }
    }
}
