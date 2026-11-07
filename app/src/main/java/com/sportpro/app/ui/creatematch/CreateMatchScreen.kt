package com.sportpro.app.ui.creatematch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.ui.components.DATE_MASK
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.MaskedDigitsField
import com.sportpro.app.ui.components.TIME_MASK
import com.sportpro.app.ui.components.dateHint
import com.sportpro.app.ui.components.parseDateDigits
import com.sportpro.app.ui.components.parseTimeDigits
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateMatchScreen(session: SessionViewModel, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val container = LocalAppContainer.current
    val ctx by session.context.collectAsStateWithLifecycle()
    val vm: CreateMatchViewModel = viewModel { CreateMatchViewModel(container) }
    val f by vm.form.collectAsStateWithLifecycle()
    val teams = ctx?.managedTeams.orEmpty()

    SpScaffold(title = "Nuevo partido", onBack = onBack) { pad ->
        if (teams.isEmpty()) {
            EmptyState("No gestionas equipos", "Solo el DT o el administrador de la academia crean partidos.", Modifier.padding(pad))
            return@SpScaffold
        }
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tu equipo (local)", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                teams.forEach { t ->
                    FilterChip(selected = f.homeTeamId == t.id, onClick = { vm.selectHome(t) }, label = { Text("${t.name} · ${t.category ?: ""}") })
                }
            }

            Text("Rival", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = f.externalRival, onClick = { vm.update { it.copy(externalRival = true) } }, label = { Text("Externo") })
                FilterChip(selected = !f.externalRival, onClick = { vm.update { it.copy(externalRival = false) } }, label = { Text("Registrado en SportPro") })
            }
            if (f.externalRival) {
                OutlinedTextField(
                    f.externalName, { v -> vm.update { it.copy(externalName = v) } },
                    label = { Text("Nombre del rival") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                val starters = f.externalStarters
                Text(
                    "Plantel del rival: ${starters.size}/${f.formatPlayers} titulares · dorsal, nombre y posición de cada uno (1 arquero).",
                    style = MaterialTheme.typography.titleSmall,
                )
                f.externalRoster.forEachIndexed { i, row ->
                    ExternalRowEditor(
                        row = row,
                        onChange = { vm.setRow(i, it) },
                        onRemove = if (!row.starter || f.externalStarters.size > f.formatPlayers) ({ vm.removeRow(i) }) else null,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::addSubstitute) { Text("Agregar suplente") }
                    OutlinedButton(onClick = vm::suggestPositions) { Text("Completar posiciones") }
                }
                val problems = f.rosterProblems()
                if (problems.isNotEmpty()) {
                    Text(
                        problems.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    "Rival externo: escudo por defecto y sin estadísticas para sus jugadores.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val rivals = teams.filter { it.id != f.homeTeamId }
                if (rivals.isEmpty()) Text("No gestionas otros equipos. Usa un rival externo.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rivals.forEach { t ->
                        FilterChip(
                            selected = f.awayTeamId == t.id,
                            onClick = { vm.update { it.copy(awayTeamId = t.id) } },
                            label = { Text("${t.name} · ${t.category ?: ""}") },
                        )
                    }
                }
            }

            Text("Formato (jugadores en cancha por equipo)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 7, 9, 11).forEach { n ->
                    FilterChip(
                        selected = !f.customFormat && f.formatPlayers == n,
                        onClick = { vm.update { it.copy(formatPlayers = n, customFormat = false) } },
                        label = { Text("$n") },
                    )
                }
                FilterChip(selected = f.customFormat, onClick = { vm.update { it.copy(customFormat = true) } }, label = { Text("Otro") })
            }
            if (f.customFormat) {
                Text("${f.formatPlayers} vs ${f.formatPlayers}")
                Slider(
                    value = f.formatPlayers.toFloat(),
                    onValueChange = { v -> vm.update { it.copy(formatPlayers = v.toInt()) } },
                    valueRange = 5f..11f,
                    steps = 5,
                )
            }
            Text("Duración de cada tiempo: ${f.halfMinutes} min", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = f.halfMinutes.toFloat(),
                onValueChange = { v -> vm.update { it.copy(halfMinutes = v.toInt()) } },
                valueRange = 5f..45f,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                MaskedDigitsField(
                    value = f.dateDigits,
                    onValueChange = { v -> vm.update { it.copy(dateDigits = v) } },
                    mask = DATE_MASK,
                    label = "Fecha",
                    isError = f.dateDigits.length == 8 && parseDateDigits(f.dateDigits) == null,
                    supportingText = dateHint(f.dateDigits),
                    modifier = Modifier.weight(1.4f),
                )
                MaskedDigitsField(
                    value = f.timeDigits,
                    onValueChange = { v -> vm.update { it.copy(timeDigits = v) } },
                    mask = TIME_MASK,
                    label = "Hora (24 h)",
                    isError = f.timeDigits.length == 4 && parseTimeDigits(f.timeDigits) == null,
                    supportingText = if (f.timeDigits.length < 4) "HH:MM" else null,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                f.venue, { v -> vm.update { it.copy(venue = v) } }, label = { Text("Sede (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Text("¿Quién puede verlo en vivo?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("followers" to "Seguidores", "public" to "Público", "private" to "Solo academia").forEach { (k, label) ->
                    FilterChip(selected = f.visibility == k, onClick = { vm.update { it.copy(visibility = k) } }, label = { Text(label) })
                }
            }
            if (f.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            f.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.create(onCreated) },
                enabled = !f.busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("Crear y preparar convocatoria") }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
    }
}
