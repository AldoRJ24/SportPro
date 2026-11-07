package com.sportpro.app.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.Position
import com.sportpro.app.ui.components.PositionBadge
import com.sportpro.app.ui.components.Crest
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.components.EventIcon

/** Hoja de registro rápido (con una mano): evento → equipo → jugador (o «Sin identificar») → se guarda con «Deshacer». */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecorderSheet(
    ui: MatchDetailUi,
    draft: EventDraft,
    onDismiss: () -> Unit,
    onMinute: (Int) -> Unit,
    onExtra: (Int) -> Unit,
    onSide: (String) -> Unit,
    onPenalty: (String) -> Unit,
    onOwnGoal: () -> Unit,
    onSubOut: (LineupPlayer) -> Unit,
    onPlayer: (LineupPlayer?) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventIcon(draft.type.icon, draft.type.code, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(draft.type.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onMinute(-1) }) { Text("−") }
                Text(
                    if (draft.extra > 0) " ${draft.minute}+${draft.extra}' " else " ${draft.minute}' ",
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedButton(onClick = { onMinute(1) }) { Text("+") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tiempo añadido", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { onExtra(-1) }) { Text("−") }
                Text("+${draft.extra}")
                OutlinedButton(onClick = { onExtra(1) }) { Text("+") }
            }

            when (draft.step) {
                DraftStep.SIDE -> {
                    Text("¿Qué equipo?", style = MaterialTheme.typography.titleMedium)
                    listOf("home", "away").forEach { side ->
                        FilledTonalButton(
                            onClick = { onSide(side) },
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                        ) {
                            TeamCrest(ui.sideName(side), if (side == "home") ui.match?.home?.crestPath else ui.match?.away?.crestPath, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(ui.sideName(side), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }

                DraftStep.PENALTY_RESULT -> {
                    Text("Resultado del penal", style = MaterialTheme.typography.titleMedium)
                    listOf("converted" to "Convertido (gol)", "missed" to "Fallado", "saved" to "Atajado").forEach { (code, label) ->
                        FilledTonalButton(onClick = { onPenalty(code) }, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text(label) }
                    }
                }

                DraftStep.SUB_OUT, DraftStep.SUB_IN, DraftStep.PLAYER -> {
                    val side = draft.side ?: "home"
                    val onField = ui.fieldPlayers(side)
                    val bench = ui.benchPlayers(side)
                    val title = when (draft.step) {
                        DraftStep.SUB_OUT -> "¿Quién sale? (en cancha · ${ui.sideName(side)})"
                        DraftStep.SUB_IN -> "¿Quién entra? Sale: ${draft.playerOut?.playerName ?: "?"}"
                        else -> if (draft.type.code == "penalty") "¿Quién pateó? (${ui.sideName(side)})" else "¿Quién? (${ui.sideName(side)})"
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (draft.type.code == "goal" && draft.step == DraftStep.PLAYER) {
                        FilterChip(selected = draft.ownGoal, onClick = onOwnGoal, label = { Text("Gol en contra") })
                    }
                    if (draft.type.code == "penalty" && draft.penaltyResult == "saved") {
                        val gk = ui.goalkeeperOnField(ui.otherSide(side))
                        Text(
                            "La atajada se anotará automáticamente al arquero de ${ui.sideName(ui.otherSide(side))}" +
                                (gk?.playerName?.let { ": $it" } ?: " (sin arquero identificado)."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (draft.type.code == "save" && draft.step == DraftStep.PLAYER) {
                        Text(
                            "No hay un arquero marcado en cancha para este equipo: elige quién atajó.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onField.isEmpty() && bench.isEmpty()) {
                        Text(
                            "Este equipo no tiene alineación registrada. Puedes guardar el evento sin jugador y asignarlo después.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // En el paso «entra» de un cambio se muestra la banca; en el resto, los jugadores en cancha.
                    val main = if (draft.step == DraftStep.SUB_IN) bench else onField
                    if (main.isNotEmpty()) {
                        Text(
                            if (draft.step == DraftStep.SUB_IN) "Banca" else "En cancha",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        main.forEach { p ->
                            PlayerChip(p, onClick = { if (draft.step == DraftStep.SUB_OUT) onSubOut(p) else onPlayer(p) })
                        }
                    }
                    // Tarjetas y faltas también pueden ser para suplentes en la banca (se muestran aparte).
                    if (draft.step == DraftStep.PLAYER && bench.isNotEmpty() && draft.type.code in setOf("yellow_card", "red_card", "foul")) {
                        var showBench by remember { mutableStateOf(false) }
                        TextButton(onClick = { showBench = !showBench }) {
                            Text(if (showBench) "Ocultar banca" else "Ver banca (${bench.size})")
                        }
                        if (showBench) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                bench.forEach { p -> PlayerChip(p, onClick = { onPlayer(p) }) }
                            }
                        }
                    }
                    if (draft.step == DraftStep.PLAYER) {
                        Button(onClick = { onPlayer(null) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text("Sin identificar (asignar después)")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlayerChip(p: LineupPlayer, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.width(108.dp).height(72.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.jerseyNumber?.toString() ?: "-", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp))
                PositionBadge(Position.of(p.fieldPosition))
            }
            Text(
                p.playerName ?: "Jugador",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
