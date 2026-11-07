package com.sportpro.app.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.data.TimelineEvent
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.OnlineDot
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.rememberNowMs
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.LiveRed

@Composable
fun MatchDetailScreen(matchId: String, onBack: () -> Unit, onSetup: () -> Unit, onSummary: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: MatchDetailViewModel = viewModel(key = "match-$matchId") { MatchDetailViewModel(container, matchId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val now = rememberNowMs()
    val snack = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var editing by remember { mutableStateOf<TimelineEvent?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }
    val match = ui.match
    val pendingHere = pending.filter { it.matchId == matchId }

    // Snackbar with «Deshacer» for every saved event (§9.4)
    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            if (msg.undoClientEventId != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val result = snack.showSnackbar(
                message = msg.text,
                actionLabel = if (msg.undoClientEventId != null) "Deshacer" else null,
                duration = if (msg.undoClientEventId != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && msg.undoClientEventId != null) {
                vm.undo(msg.undoClientEventId, msg.undoEventId)
            }
        }
    }

    // Screen always on while recording a live match (field use)
    val view = LocalView.current
    val keepOn = ui.canRecord && match?.isLive == true
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    SpScaffold(
        title = match?.let { "${it.homeName} vs ${it.awayName}" } ?: "Partido",
        onBack = onBack,
        snackbarHostState = snack,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
        },
    ) { pad ->
        when {
            ui.loading && match == null -> LoadingBox(Modifier.padding(pad))
            ui.error != null && match == null -> ErrorBox(ui.error!!, onRetry = vm::load, modifier = Modifier.padding(pad))
            match != null -> Column(Modifier.padding(pad).fillMaxSize()) {
                if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                MatchHeader(match, now)
                if (ui.canRecord || pendingHere.isNotEmpty()) {
                    OnlineDot(online, pendingHere.size, Modifier.padding(horizontal = 20.dp))
                }
                ControlBar(
                    ui = ui,
                    onStart = { vm.changeState("start") },
                    onHalftime = { vm.changeState("halftime") },
                    onSecondHalf = { vm.changeState("second_half") },
                    onFinish = { confirmFinish = true },
                    onSetup = onSetup,
                    onSummary = onSummary,
                )
                val tabs = buildList {
                    if (ui.canRecord) add("Registrar")
                    add("Cronología")
                    add("Alineación")
                }
                var tab by rememberSaveable(tabs.size) { mutableIntStateOf(0) }
                PrimaryTabRow(selectedTabIndex = tab.coerceIn(0, tabs.lastIndex)) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
                when (tabs.getOrNull(tab)) {
                    "Registrar" -> RecordTab(ui, pendingHere.size, onPick = vm::startDraft, onEdit = { editing = it })
                    "Cronología" -> TimelineTab(ui, pendingHere, onEdit = if (ui.canCorrect) ({ e: TimelineEvent -> editing = e }) else null)
                    else -> Row(Modifier.fillMaxWidth()) {
                        LineupColumn(match.homeName, ui.playersOf("home"), Modifier.weight(1f))
                        LineupColumn(match.awayName, ui.playersOf("away"), Modifier.weight(1f))
                    }
                }
            }
        }
    }

    ui.draft?.let { d ->
        RecorderSheet(
            ui = ui,
            draft = d,
            onDismiss = vm::cancelDraft,
            onMinute = vm::adjustMinute,
            onExtra = vm::adjustExtra,
            onSide = vm::chooseSide,
            onPenalty = vm::choosePenaltyResult,
            onOwnGoal = vm::toggleOwnGoal,
            onSubOut = vm::chooseSubOut,
            onPlayer = vm::choosePlayer,
        )
    }

    editing?.let { e ->
        EditEventDialog(
            event = e,
            players = ui.playersOf(e.teamSide),
            onDismiss = { editing = null },
            onCorrect = { patch, reason -> vm.correct(e, patch, reason); editing = null },
            onVoid = { reason -> vm.void(e, reason); editing = null },
        )
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("¿Finalizar el partido?") },
            text = { Text("Se consolidan las estadísticas. Luego el DT aún puede corregir eventos con motivo.") },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.changeState("finish") }) { Text("Finalizar") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ControlBar(
    ui: MatchDetailUi,
    onStart: () -> Unit,
    onHalftime: () -> Unit,
    onSecondHalf: () -> Unit,
    onFinish: () -> Unit,
    onSetup: () -> Unit,
    onSummary: () -> Unit,
) {
    val m = ui.match ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (m.isScheduled && ui.isManager) {
            OutlinedButton(onClick = onSetup, modifier = Modifier.weight(1f)) { Text("Convocatoria y alineación") }
        }
        if (ui.canControl) {
            when (m.status) {
                "scheduled" -> Button(onClick = onStart, enabled = !ui.busy, modifier = Modifier.weight(1f)) { Text("Iniciar partido") }
                "live" -> {
                    if (m.period == 1) OutlinedButton(onClick = onHalftime, enabled = !ui.busy, modifier = Modifier.weight(1f)) { Text("Fin 1.er tiempo") }
                    Button(
                        onClick = onFinish, enabled = !ui.busy, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = LiveRed),
                    ) { Text("Finalizar") }
                }
                "halftime" -> {
                    Button(onClick = onSecondHalf, enabled = !ui.busy, modifier = Modifier.weight(1f)) { Text("Iniciar 2.º tiempo") }
                    OutlinedButton(onClick = onFinish, enabled = !ui.busy, modifier = Modifier.weight(1f)) { Text("Finalizar") }
                }
            }
        }
        if (m.isFinished && ui.isManager) {
            Button(onClick = onSummary, modifier = Modifier.weight(1f)) { Text("Resumen IA") }
        }
    }
}

@Composable
private fun RecordTab(ui: MatchDetailUi, pendingCount: Int, onPick: (com.sportpro.app.core.data.EventType) -> Unit, onEdit: (TimelineEvent) -> Unit) {
    val types = ui.recordableTypes
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 92.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (types.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Catálogo vacío", "No hay tipos de evento activos.") }
        }
        items(types, key = { it.id }) { t ->
            EventButton(t.name, t.icon, t.code, onClick = { onPick(t) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Últimos registros" + if (pendingCount > 0) " · $pendingCount sin enviar" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val last = ui.ordered.filterNot { it.isControl }.takeLast(5).reversed()
        items(last, key = { "r" + it.eventId }, span = { GridItemSpan(maxLineSpan) }) { e ->
            TimelineRow(e, ui::sideName, ui::nameOf, onLongPress = if (ui.canCorrect) ({ onEdit(e) }) else null)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Mantén presionado un registro para corregirlo o anularlo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(40.dp),
            )
        }
    }
}

@Composable
private fun TimelineTab(ui: MatchDetailUi, pending: List<com.sportpro.app.core.data.offline.PendingEvent>, onEdit: ((TimelineEvent) -> Unit)?) {
    val events = ui.ordered.reversed()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(pending, key = { "p" + it.clientEventId }) { PendingRow(it) }
        if (events.isEmpty() && pending.isEmpty()) {
            item { EmptyState("Sin eventos aún", "La cronología se actualiza en tiempo real.") }
        }
        items(events, key = { it.eventId }) { e ->
            TimelineRow(e, ui::sideName, ui::nameOf, onLongPress = onEdit?.let { f -> { f(e) } })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
