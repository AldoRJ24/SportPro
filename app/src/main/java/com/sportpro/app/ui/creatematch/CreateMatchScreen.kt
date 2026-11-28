package com.sportpro.app.ui.creatematch
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.ui.components.CapsuleSlider
import com.sportpro.app.ui.components.DateField
import com.sportpro.app.ui.components.TimeField
import com.sportpro.app.ui.components.toDateDigits
import com.sportpro.app.ui.components.toTimeDigits
import androidx.compose.ui.res.painterResource
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.parseDateDigits
import com.sportpro.app.ui.components.parseTimeDigits
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.core.data.RivalTeam
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateMatchScreen(
    session: SessionViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    /** Se envió un desafío al entrenador rival (aún no hay partido): se abre la lista de desafíos. */
    onChallengeSent: () -> Unit,
) {
    val container = LocalAppContainer.current
    val ctx by session.context.collectAsStateWithLifecycle()
    val vm: CreateMatchViewModel = viewModel { CreateMatchViewModel(container) }
    val f by vm.form.collectAsStateWithLifecycle()
    val teams = ctx?.managedTeams.orEmpty()
    // Equipos donde soy DT. Los demás que gestiono (como administrador) van como propuesta a su DT.
    val staffIds = ctx?.staffTeamIds.orEmpty()

    SpScaffold(title = stringResource(R.string.create_match_nuevo_partido), onBack = onBack) { pad ->
        if (teams.isEmpty()) {
            EmptyState(stringResource(R.string.create_match_no_gestionas_equipos), stringResource(R.string.create_match_solo_el_dt_o_el), Modifier.padding(pad))
            return@SpScaffold
        }
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.create_match_tu_equipo_local), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                teams.forEach { t ->
                    // Si no lo dirijo yo, se muestra su entrenador: a él le llegará la propuesta
                    val coach = f.coaches[t.id]?.takeIf { t.id !in staffIds }?.coachName
                    FilterChip(
                        selected = f.homeTeamId == t.id,
                        onClick = { vm.selectHome(t) },
                        label = {
                            Text(listOfNotNull(t.name, t.category, coach?.let { stringResource(R.string.create_match_dt_x, it) }).joinToString(" · "))
                        },
                    )
                }
            }

            Text(stringResource(R.string.create_match_rival), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = f.externalRival, onClick = { vm.update { it.copy(externalRival = true) } }, label = { Text(stringResource(R.string.create_match_externo)) })
                FilterChip(selected = !f.externalRival, onClick = { vm.update { it.copy(externalRival = false) } }, label = { Text(stringResource(R.string.create_match_registrado_en_sportpro)) })
            }
            if (f.externalRival) {
                OutlinedTextField(
                    f.externalName, { v -> vm.update { it.copy(externalName = v) } },
                    label = { Text(stringResource(R.string.create_match_nombre_del_rival)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                val starters = f.externalStarters
                Text(
                    stringResource(R.string.create_match_plantel_del_rival_x_x, starters.size, f.formatPlayers),
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
                    OutlinedButton(onClick = vm::addSubstitute) { Text(stringResource(R.string.create_match_agregar_suplente)) }
                    OutlinedButton(onClick = vm::suggestPositions) { Text(stringResource(R.string.create_match_completar_posiciones)) }
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
                    stringResource(R.string.create_match_rival_externo_escudo_por_defecto),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                RegisteredRivalPicker(
                    f = f,
                    onQuery = vm::setAcademyQuery,
                    onAcademy = vm::selectAcademy,
                    onTeam = vm::selectRival,
                )
            }

            Text(stringResource(R.string.create_match_formato_jugadores_en_cancha_por), style = MaterialTheme.typography.titleMedium)
            // En varias filas si no entran (con letra grande los chips ya no se aprietan ni se cortan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 7, 9, 11).forEach { n ->
                    FilterChip(
                        selected = !f.customFormat && f.formatPlayers == n,
                        onClick = { vm.setFormat(n, custom = false) },
                        label = { Text(stringResource(R.string.create_match_x_jugadores, n)) },
                    )
                }
                FilterChip(selected = f.customFormat, onClick = { vm.setFormat(f.formatPlayers, custom = true) }, label = { Text(stringResource(R.string.create_match_otro)) })
            }
            if (f.customFormat) {
                CapsuleSlider(
                    value = f.formatPlayers,
                    range = 5..11,
                    onValueChange = { n -> vm.setFormat(n, custom = true) },
                    valueLabel = stringResource(R.string.create_match_x_jugadores, f.formatPlayers),
                )
            }
            Text(stringResource(R.string.create_match_duracion_titulo), style = MaterialTheme.typography.titleMedium)
            // Barra tipo «brillo de One UI»: se desliza con el dedo y hace «tic» en cada minuto
            CapsuleSlider(
                value = f.halfMinutes,
                range = 5..45,
                onValueChange = vm::setHalfMinutes,
                valueLabel = stringResource(R.string.create_match_x_min, f.halfMinutes),
                icon = painterResource(R.drawable.ic_clock),
                accessibilityLabel = stringResource(R.string.create_match_duracion_de_cada_tiempo_x, f.halfMinutes),
            )

            // Fecha con calendario y hora con reloj: se tocan, no hay que escribir dígitos
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                DateField(
                    date = parseDateDigits(f.dateDigits),
                    onDate = { d -> vm.update { it.copy(dateDigits = d.toDateDigits()) } },
                    label = stringResource(R.string.create_match_fecha),
                    modifier = Modifier.weight(1.35f),
                )
                TimeField(
                    time = parseTimeDigits(f.timeDigits),
                    onTime = { t -> vm.update { it.copy(timeDigits = t.toTimeDigits()) } },
                    label = stringResource(R.string.create_match_hora),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                f.venue, { v -> vm.update { it.copy(venue = v) } }, label = { Text(stringResource(R.string.create_match_sede_opcional)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.create_match_quien_puede_verlo_en_vivo), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("followers" to stringResource(R.string.create_match_seguidores), "public" to stringResource(R.string.create_match_publico), "private" to stringResource(R.string.create_match_solo_academia)).forEach { (k, label) ->
                    FilterChip(selected = f.visibility == k, onClick = { vm.update { it.copy(visibility = k) } }, label = { Text(label) })
                }
            }
            if (f.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            f.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            // Contra el equipo de otro entrenador no se crea el partido: se le envía un desafío.
            // Si soy administrador y no dirijo el equipo local, es una propuesta que aceptan los dos DT.
            val adminProposal = f.isAdminProposal(staffIds)
            val challenge = f.needsApproval(staffIds)
            if (adminProposal) {
                Text(
                    stringResource(R.string.create_match_propuesta_ayuda),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (challenge) {
                Text(
                    stringResource(R.string.create_match_desafio_ayuda, f.rivalTeam?.coachName ?: stringResource(R.string.create_match_el_entrenador_rival)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { vm.create(staffIds, onCreated, onChallengeSent) },
                enabled = !f.busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(stringResource(when {
                    adminProposal -> R.string.create_match_enviar_propuesta
                    challenge -> R.string.create_match_enviar_desafio
                    else -> R.string.create_match_crear_y_preparar_convocatoria
                }))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_match_cancelar)) }
        }
    }
}

/**
 * Rival registrado en SportPro, en dos pasos:
 *  1. elegir la ACADEMIA rival (con buscador por nombre o ciudad);
 *  2. elegir uno de sus EQUIPOS. Cada equipo muestra su entrenador, para saber a quién se
 *     desafía. Los que están fuera de la división (-1 a +1) aparecen apagados con el motivo.
 * Quién está disponible lo decide el servidor; aquí solo se muestra.
 */
@Composable
private fun RegisteredRivalPicker(
    f: CreateMatchForm,
    onQuery: (String) -> Unit,
    onAcademy: (String?) -> Unit,
    onTeam: (RivalTeam) -> Unit,
) {
    if (f.homeTeamId == null) {
        Text(stringResource(R.string.create_match_elige_primero_tu_equipo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val academy = f.rivalAcademy
    if (academy == null) {
        // ---- Paso 1: academia ----
        OutlinedTextField(
            value = f.academyQuery,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.create_match_buscar_academia)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val shown = f.shownAcademies
        if (shown.isEmpty()) {
            Text(stringResource(R.string.create_match_sin_academias), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Lista corta: con el buscador se llega rápido a la academia
        shown.take(8).forEach { a ->
            Card(
                onClick = { onAcademy(a.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamCrest(a.name, a.crestPath, 40.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        a.city?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (shown.size > 8) {
            Text(stringResource(R.string.create_match_hay_mas_academias, shown.size - 8), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // ---- Paso 2: equipo de esa academia ----
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamCrest(academy.name, academy.crestPath, 36.dp)
        Text(academy.name, Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { onAcademy(null) }) { Text(stringResource(R.string.create_match_cambiar_academia)) }
    }
    if (f.rivalsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (!f.rivalsLoading && f.rivalTeams.isEmpty()) {
        Text(stringResource(R.string.create_match_academia_sin_equipos), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    f.rivalTeams.forEach { t ->
        val selected = f.awayTeamId == t.teamId
        Card(
            onClick = { onTeam(t) },
            enabled = t.eligible,
            modifier = Modifier.fillMaxWidth(),
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    listOfNotNull(t.name, t.category).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.create_match_entrenador_x, t.coachName ?: stringResource(R.string.create_match_sin_entrenador)),
                    style = MaterialTheme.typography.bodySmall,
                )
                // Fuera de división: se ve, pero no se puede elegir, y se explica por qué
                if (!t.eligible) {
                    Text(
                        t.reason ?: stringResource(R.string.create_match_fuera_de_division),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
