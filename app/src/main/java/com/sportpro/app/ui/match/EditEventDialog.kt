package com.sportpro.app.ui.match
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.TimelineEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Corregir o anular un evento sin perder la trazabilidad (US-20, Regla C):
 * el servidor guarda la versión anterior en match_event_revisions y exige un motivo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditEventDialog(
    event: TimelineEvent,
    players: List<LineupPlayer>,
    onDismiss: () -> Unit,
    onCorrect: (JsonObject, String) -> Unit,
    onVoid: (String) -> Unit,
) {
    var selectedKey by remember { mutableStateOf<String?>(null) } // null = no change
    var clearPlayer by remember { mutableStateOf(false) }
    var minute by remember { mutableIntStateOf(event.minute) }
    var extra by remember { mutableIntStateOf(event.minuteExtra) }
    val currentAssist = (event.payload["assist_player_id"] as? JsonPrimitive)?.contentOrNull
    var assistKey by remember { mutableStateOf(currentAssist) }
    var reason by remember { mutableStateOf("") }
    var confirmVoid by remember { mutableStateOf(false) }

    if (confirmVoid) {
        AlertDialog(
            onDismissRequest = { confirmVoid = false },
            title = { Text(stringResource(R.string.edit_event_anular_evento)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.edit_event_el_evento_no_se_borra))
                    OutlinedTextField(reason, { reason = it }, label = { Text(stringResource(R.string.edit_event_motivo_obligatorio)) })
                }
            },
            confirmButton = {
                TextButton(enabled = reason.isNotBlank(), onClick = { onVoid(reason.trim()) }) { Text(stringResource(R.string.edit_event_anular)) }
            },
            dismissButton = { TextButton(onClick = { confirmVoid = false }) { Text(stringResource(R.string.edit_event_cancelar)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_event_corregir_x_x, event.typeName, event.minuteLabel)) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!event.isControl && event.code != "substitution") {
                    Text(stringResource(R.string.edit_event_jugador_x, event.playerName ?: stringResource(R.string.edit_event_sin_asignar)), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        players.forEach { p ->
                            FilterChip(
                                selected = selectedKey == p.key,
                                onClick = { selectedKey = p.key; clearPlayer = false },
                                label = { Text("${p.jerseyNumber ?: "-"} ${p.playerName ?: ""}") },
                            )
                        }
                        FilterChip(
                            selected = clearPlayer,
                            onClick = { clearPlayer = !clearPlayer; selectedKey = null },
                            label = { Text(stringResource(R.string.edit_event_sin_jugador)) },
                        )
                    }
                }
                if (event.code == "goal") {
                    Text(stringResource(R.string.edit_event_asistencia_opcional), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        players.filter { it.playerId != null }.forEach { p ->
                            FilterChip(
                                selected = assistKey == p.key,
                                onClick = { assistKey = if (assistKey == p.key) null else p.key },
                                label = { Text("${p.jerseyNumber ?: "-"} ${p.playerName ?: ""}") },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.edit_event_minuto), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { minute = (minute - 1).coerceAtLeast(0) }) { Text("−") }
                    Text(if (extra > 0) "$minute+$extra'" else "$minute'", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { minute = (minute + 1).coerceAtMost(200) }) { Text("+") }
                    TextButton(onClick = { extra = (extra + 1).coerceAtMost(30) }) { Text(stringResource(R.string.edit_event_anadido)) }
                    if (extra > 0) TextButton(onClick = { extra = 0 }) { Text(stringResource(R.string.edit_event_quitar)) }
                }
                Text(stringResource(R.string.edit_event_motivo_de_la_correccion), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(stringResource(R.string.edit_event_jugador_identificado_despues), stringResource(R.string.edit_event_minuto_corregido), stringResource(R.string.edit_event_error_de_registro)).forEach { r ->
                        FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r) })
                    }
                }
                OutlinedTextField(reason, { reason = it }, label = { Text(stringResource(R.string.edit_event_motivo)) }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { confirmVoid = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_event_anular_evento_2)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reason.isNotBlank(),
                onClick = {
                    val player = players.firstOrNull { it.key == selectedKey }
                    val payload = if (event.code == "goal" && assistKey != currentAssist) {
                        MatchDetailViewModel.payloadWith(event.payload, "assist_player_id", assistKey)
                    } else null
                    val patch = MatchDetailViewModel.patchFor(
                        player = player,
                        clearPlayer = clearPlayer,
                        minute = if (minute != event.minute) minute else null,
                        extra = if (extra != event.minuteExtra) extra else null,
                        payload = payload,
                    )
                    onCorrect(patch, reason.trim())
                },
            ) { Text(stringResource(R.string.edit_event_guardar_correccion)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_event_cancelar)) } },
    )
}
