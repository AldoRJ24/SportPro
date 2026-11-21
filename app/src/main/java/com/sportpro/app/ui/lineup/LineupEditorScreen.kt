package com.sportpro.app.ui.lineup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.data.LineupDraft
import com.sportpro.app.core.data.PitchFormation
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.ui.components.Avatar
import com.sportpro.app.ui.components.BenchRow
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.HalfPitchBoard
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.PositionBadge
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.rememberNowMs
import com.sportpro.app.ui.components.tokenSizeFor
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.FavoriteGold
import com.sportpro.app.ui.theme.SuccessGreen
import com.sportpro.app.ui.theme.WarnAmber

/**
 * Pantalla del editor de alineación en la cancha. El local se arma en la mitad de ARRIBA
 * (arco arriba) y el visitante en la de ABAJO, igual que se verá en el partido.
 */
@Composable
fun LineupEditorScreen(matchId: String, side: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: LineupEditorViewModel = viewModel(key = "lineup-$matchId-$side") { LineupEditorViewModel(container, matchId, side) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    // Se recalcula cada minuto: al llegar la hora límite el editor pasa a solo lectura
    val now = rememberNowMs(60_000)
    val editable = ui.editable(now) && !ui.busy
    var askLeave by remember { mutableStateOf(false) }
    var askCustomName by remember { mutableStateOf(false) }
    var askArchive by remember { mutableStateOf<PitchFormation?>(null) }

    // Con cambios sin guardar, «Atrás» pregunta antes de salir
    val back: () -> Unit = { if (ui.dirty) { askLeave = true } else { onBack() } }
    BackHandler(enabled = ui.dirty) { askLeave = true }

    SpScaffold(
        title = stringResource(R.string.lineup_editor_title, ui.teamName),
        onBack = back,
        snackbarHostState = snack,
    ) { pad ->
        val draft = ui.draft
        val match = ui.match
        when {
            ui.loading && draft == null -> LoadingBox(Modifier.padding(pad))
            ui.error != null && draft == null -> ErrorBox(ui.error!!, onRetry = vm::load, modifier = Modifier.padding(pad))
            draft != null && match != null -> Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                EditorHeader(ui, draft, editable)

                // ---------- Formaciones ----------
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ui.presets.forEach { f ->
                        FilterChip(
                            selected = !draft.formation.isCustom && draft.formation.label == f.label,
                            onClick = { vm.selectFormation(f) },
                            enabled = editable,
                            label = { Text(f.label) },
                        )
                    }
                    ui.customs.forEach { f ->
                        FilterChip(
                            selected = draft.formation.customId == f.customId,
                            onClick = { vm.selectFormation(f) },
                            enabled = editable,
                            label = { Text(f.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = FavoriteGold, modifier = Modifier.size(16.dp)) },
                            trailingIcon = if (editable) {
                                {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.lineup_editor_custom_archive),
                                        modifier = Modifier.size(16.dp).clickable { askArchive = f },
                                    )
                                }
                            } else null,
                        )
                    }
                }

                // ---------- Cancha ----------
                HalfPitchBoard(
                    slots = draft.slots,
                    top = side == "home",
                    player = ui::token,
                    tokenSize = tokenSizeFor(match.formatPlayers),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    selected = ui.picking,
                    onTap = if (editable) vm::openPicker else null,
                    onMove = if (editable) vm::move else null,
                )
                if (editable) {
                    Text(
                        stringResource(R.string.lineup_editor_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = vm::autoFill, enabled = draft.emptySlots > 0 && draft.bench.isNotEmpty()) {
                            Text(stringResource(R.string.match_setup_armar_automatico))
                        }
                        TextButton(onClick = vm::resetPositions, enabled = draft.isCustomized) {
                            Text(stringResource(R.string.lineup_editor_reset))
                        }
                        if (draft.isCustomized) {
                            TextButton(onClick = { askCustomName = true }) { Text(stringResource(R.string.lineup_editor_save_custom)) }
                        }
                    }
                }

                // ---------- Suplentes y quienes no juegan (fuera de la cancha) ----------
                if (ui.confirmed.isEmpty()) {
                    Text(
                        stringResource(R.string.match_setup_aun_no_hay_convocados_confirmados),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                SectionHeader(stringResource(R.string.lineup_editor_bench, draft.bench.size))
                if (draft.bench.isEmpty()) {
                    EmptyLine(stringResource(R.string.lineup_editor_bench_empty))
                } else {
                    BenchRow(
                        draft.bench, ui::token, Modifier.padding(horizontal = 12.dp),
                        onTap = if (editable) vm::toggleOut else null,
                    )
                    if (editable) EmptyLine(stringResource(R.string.lineup_editor_bench_hint))
                }
                if (draft.out.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.lineup_editor_out, draft.out.size))
                    BenchRow(
                        draft.out, ui::token, Modifier.padding(horizontal = 12.dp), dimmed = true,
                        onTap = if (editable) vm::toggleOut else null,
                    )
                    if (editable) EmptyLine(stringResource(R.string.lineup_editor_out_hint))
                }

                Button(
                    onClick = vm::save,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
                ) {
                    Text(stringResource(if (ui.flow) R.string.lineup_save_and_send else R.string.match_setup_guardar_alineacion))
                }
            }
        }
    }

    // Lista para elegir el jugador de la casilla tocada
    val picking = ui.picking
    val draft = ui.draft
    if (picking != null && draft != null && picking in draft.slots.indices) {
        PlayerPickerSheet(
            ui = ui,
            draft = draft,
            index = picking,
            onPick = { vm.assign(picking, it) },
            onClear = { vm.clear(picking) },
            onDismiss = vm::closePicker,
        )
    }

    if (askCustomName) {
        CustomNameDialog(
            onDismiss = { askCustomName = false },
            onSave = { name -> askCustomName = false; vm.saveCustom(name) },
        )
    }
    askArchive?.let { f ->
        AlertDialog(
            onDismissRequest = { askArchive = null },
            title = { Text(stringResource(R.string.lineup_editor_custom_archive_title, f.displayName)) },
            text = { Text(stringResource(R.string.lineup_editor_custom_archive_body)) },
            confirmButton = { TextButton(onClick = { askArchive = null; vm.archiveCustom(f) }) { Text(stringResource(R.string.lineup_editor_custom_archive)) } },
            dismissButton = { TextButton(onClick = { askArchive = null }) { Text(stringResource(R.string.match_detail_cancelar)) } },
        )
    }
    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text(stringResource(R.string.lineup_editor_leave_title)) },
            text = { Text(stringResource(R.string.lineup_editor_leave_body)) },
            confirmButton = { TextButton(onClick = { askLeave = false; onBack() }) { Text(stringResource(R.string.lineup_editor_leave)) } },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text(stringResource(R.string.lineup_editor_stay)) } },
        )
    }
}

/** Formación elegida, titulares completos o no, y el plazo (si el partido es acordado). */
@Composable
private fun EditorHeader(ui: LineupEditorUi, draft: LineupDraft, editable: Boolean) {
    val total = draft.slots.size
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(draft.formation.displayName, style = MaterialTheme.typography.titleMedium)
            Pill(
                stringResource(R.string.lineup_editor_starters, total - draft.emptySlots, total),
                if (draft.isComplete) SuccessGreen else WarnAmber,
            )
            if (ui.dirty) Pill(stringResource(R.string.lineup_editor_unsaved), WarnAmber)
        }
        if (ui.flow && ui.status?.confirmed != true) {
            Text(
                stringResource(R.string.lineup_deadline, formatDayTime(ui.status?.deadline)),
                style = MaterialTheme.typography.bodySmall,
                color = WarnAmber,
            )
        }
        if (!editable && !ui.busy) {
            Text(
                stringResource(R.string.lineup_editor_read_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * Lista para elegir quién va en una casilla. Primero los suplentes (los que juegan
 * habitualmente en esa posición, arriba), luego los que no juegan y al final los que ya
 * están en la cancha (si se elige uno, intercambian lugar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerPickerSheet(
    ui: LineupEditorUi,
    draft: LineupDraft,
    index: Int,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val slot = draft.slots[index]
    val pos = slot.slot.pos
    val usual = ui.usual
    val jersey = ui.jersey
    // Los de la misma posición habitual primero; luego por dorsal
    val order = compareBy<String>({ if (usual[it] == pos) 0 else 1 }, { jersey[it] ?: 99 })
    val onField = draft.slots.mapNotNull { it.playerId }.filter { it != slot.playerId }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    stringResource(R.string.lineup_editor_pick_title, pos.label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (slot.playerId != null) {
                item {
                    TextButton(onClick = onClear, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.lineup_editor_pick_clear))
                    }
                }
            }
            pickerSection(R.string.lineup_editor_pick_bench, draft.bench.sortedWith(order), ui, pos, onPick)
            pickerSection(R.string.lineup_editor_pick_out, draft.out.sortedWith(order), ui, pos, onPick)
            pickerSection(R.string.lineup_editor_pick_swap, onField.sortedWith(order), ui, pos, onPick)
            if (draft.bench.isEmpty() && draft.out.isEmpty() && onField.isEmpty()) {
                item { EmptyLine(stringResource(R.string.match_setup_aun_no_hay_convocados_confirmados)) }
            }
        }
    }
}

// Una sección de la lista de jugadores (con título) dentro de la hoja inferior.
private fun androidx.compose.foundation.lazy.LazyListScope.pickerSection(
    title: Int, ids: List<String>, ui: LineupEditorUi, pos: Position, onPick: (String) -> Unit,
) {
    if (ids.isEmpty()) return
    item(key = "h$title") { SectionHeader(stringResource(title)) }
    items(ids, key = { "p$title$it" }) { id ->
        val row = ui.row(id) ?: return@items
        val name = row.player?.fullName ?: "?"
        Row(
            Modifier.fillMaxWidth().clickable { onPick(id) }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name, ui.photos[id], size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Text("${row.jerseyNumber ?: "-"}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(28.dp))
            Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val usual = Position.of(row.position)
            PositionBadge(usual)
            if (usual == pos) {
                Spacer(Modifier.width(4.dp))
                Pill(stringResource(R.string.lineup_editor_pick_usual), SuccessGreen)
            }
        }
    }
}

/** Pide el nombre para guardar la disposición actual como formación propia. */
@Composable
private fun CustomNameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lineup_editor_save_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lineup_editor_custom_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text(stringResource(R.string.lineup_editor_custom_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.lineup_editor_custom_save_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.match_detail_cancelar)) } },
    )
}
