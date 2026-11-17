package com.sportpro.app.ui.fees

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.data.FeeTeamPlayer
import com.sportpro.app.core.data.FeeTeamSummary
import com.sportpro.app.core.data.Fees
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.UiMessageEffect
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.LiveRed
import com.sportpro.app.ui.theme.SuccessGreen
import com.sportpro.app.ui.theme.WarnAmber

/** Aviso fijo y honesto: esta parte registra pagos, no cobra de verdad. */
@Composable
fun SimulatedNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WarnAmber.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            stringResource(R.string.fees_simulated_notice),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * Frase corta con la situación de un equipo o jugador y el color que le toca:
 * rojo si hay vencidos, ámbar si debe o hay pagos por confirmar, verde si está al día.
 */
@Composable
private fun debtStatus(debt: Double, overdue: Int, reported: Int): Pair<String, Color> = when {
    overdue > 0 -> Pair(
        stringResource(R.string.fees_debt_line, Fees.money(debt)) + " · " +
            pluralStringResource(R.plurals.fees_overdue_count, overdue, overdue),
        LiveRed,
    )
    reported > 0 -> Pair(pluralStringResource(R.plurals.fees_reported_count, reported, reported), WarnAmber)
    debt > 0.0 -> Pair(stringResource(R.string.fees_debt_line, Fees.money(debt)), WarnAmber)
    else -> Pair(stringResource(R.string.fees_up_to_date), SuccessGreen)
}

// ---------------------------------------------------------------------
// 1) Lista de equipos que gestiono
// ---------------------------------------------------------------------

/** Entrada del admin y del DT: cada equipo con su deuda. El admin ve todos los de su academia. */
@Composable
fun FeesOverviewScreen(onBack: () -> Unit, onOpenTeam: (teamId: String, name: String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: FeesOverviewViewModel = viewModel { FeesOverviewViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Al volver de la pantalla de un equipo, los totales pueden haber cambiado
    LaunchedEffect(Unit) { vm.load() }

    SpScaffold(title = stringResource(R.string.fees_title), onBack = onBack) { pad ->
        when {
            ui.loading && ui.teams.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && ui.teams.isEmpty() -> ErrorBox(ui.error!!, onRetry = vm::load, modifier = Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SimulatedNotice(Modifier.padding(16.dp)) }
                if (ui.teams.isEmpty()) {
                    item {
                        EmptyState(
                            stringResource(R.string.fees_overview_empty_title),
                            stringResource(R.string.fees_overview_empty_body),
                        )
                    }
                }
                items(ui.teams, key = { it.teamId }) { team ->
                    TeamSummaryCard(team, onClick = { onOpenTeam(team.teamId, team.teamName) })
                }
            }
        }
    }
}

@Composable
private fun TeamSummaryCard(team: FeeTeamSummary, onClick: () -> Unit) {
    val (statusText, statusColor) = debtStatus(team.debt, team.overdueCount, team.reportedCount)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                listOfNotNull(team.teamName, team.category).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                planLine(team) + " · " + pluralStringResource(R.plurals.fees_enrolled_count, team.enrolled, team.enrolled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
        }
    }
}

/** «S/ 120.00 al mes · vence el día 10» o «Sin mensualidad definida». */
@Composable
private fun planLine(team: FeeTeamSummary?): String {
    val amount = team?.planAmount
    val day = team?.dueDay
    return if (amount != null && day != null) stringResource(R.string.fees_plan_line, Fees.money(amount), day)
    else stringResource(R.string.fees_no_plan)
}

// ---------------------------------------------------------------------
// 2) Un equipo
// ---------------------------------------------------------------------

/**
 * Mensualidades de un equipo. Lo que se ve depende del rol que el SERVIDOR devuelve en
 * `can_admin`: el admin fija el monto; el DT elige jugadores, genera cobros y mira.
 * Si alguien forzara un botón que no le toca, la función SQL lo rechaza igual.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamFeesScreen(
    teamId: String,
    name: String,
    onBack: () -> Unit,
    onOpenStatement: (playerId: String?, title: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: TeamFeesViewModel = viewModel(key = "fees-team-$teamId") { TeamFeesViewModel(container, teamId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var showPlanDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load(silent = true) }   // refresca al volver del estado de cuenta
    UiMessageEffect(ui.message, snack, vm::consumeMessage)

    val summary = ui.summary
    SpScaffold(title = stringResource(R.string.fees_team_title, name), onBack = onBack, snackbarHostState = snack) { pad ->
        when {
            ui.loading && summary == null -> LoadingBox(Modifier.padding(pad))
            ui.error != null && summary == null -> ErrorBox(ui.error!!, onRetry = { vm.load() }, modifier = Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SimulatedNotice(Modifier.padding(16.dp)) }

                // ---- Plan y totales ----
                item { SectionHeader(stringResource(R.string.fees_plan_header)) }
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(planLine(summary), style = MaterialTheme.typography.titleMedium)
                            if (summary != null) {
                                val (statusText, statusColor) = debtStatus(summary.debt, summary.overdueCount, summary.reportedCount)
                                Text(statusText, color = statusColor)
                                if (summary.pendingCount > 0) {
                                    Text(
                                        pluralStringResource(R.plurals.fees_pending_count, summary.pendingCount, summary.pendingCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (summary?.canAdmin == true) {
                                OutlinedButton(onClick = { showPlanDialog = true }, enabled = !ui.busy) {
                                    Text(stringResource(if (summary.planAmount == null) R.string.fees_plan_define else R.string.fees_plan_change))
                                }
                            } else if (summary?.planAmount == null) {
                                Text(
                                    stringResource(R.string.fees_plan_ask_admin),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ---- Acciones del equipo ----
                item {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Sin plan no hay monto que cobrar: el botón queda apagado
                        Button(onClick = vm::generate, enabled = !ui.busy && summary?.planAmount != null) {
                            Text(stringResource(R.string.fees_generate))
                        }
                        OutlinedButton(onClick = vm::remind, enabled = !ui.busy && (summary?.overdueCount ?: 0) > 0) {
                            Text(stringResource(R.string.fees_remind))
                        }
                        TextButton(onClick = { onOpenStatement(null, name) }) {
                            Text(stringResource(R.string.fees_view_movements))
                        }
                    }
                }

                // ---- Jugadores ----
                item { SectionHeader(stringResource(R.string.fees_players_header)) }
                item {
                    Text(
                        stringResource(R.string.fees_players_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (ui.players.isEmpty()) {
                    item {
                        EmptyState(
                            stringResource(R.string.fees_players_empty_title),
                            stringResource(R.string.fees_players_empty_body),
                        )
                    }
                }
                items(ui.players, key = { it.playerId }) { player ->
                    PlayerFeeRow(
                        player = player,
                        onToggle = { enabled -> vm.setPlayer(player, enabled) },
                        onClick = { onOpenStatement(player.playerId, player.playerName) },
                    )
                }
            }
        }
    }

    if (showPlanDialog) {
        PlanDialog(
            initialAmount = summary?.planAmount,
            initialDay = summary?.dueDay,
            onDismiss = { showPlanDialog = false },
            onSave = { amount, day ->
                showPlanDialog = false
                vm.setPlan(amount, day)
            },
        )
    }
}

@Composable
private fun PlayerFeeRow(player: FeeTeamPlayer, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val switchLabel = stringResource(R.string.fees_player_switch_cd, player.playerName)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${player.jerseyNumber ?: "-"}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(player.playerName, style = MaterialTheme.typography.bodyLarge)
                if (!player.enabled && player.debt <= 0.0) {
                    Text(
                        stringResource(R.string.fees_player_not_enrolled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Aunque se le quite la mensualidad, si aún debe algo se sigue mostrando
                    val (statusText, statusColor) = debtStatus(player.debt, player.overdueCount, player.reportedCount)
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                    player.lastPaidOn?.let {
                        Text(
                            stringResource(R.string.fees_player_last_paid, Fees.dateLabel(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = player.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = switchLabel },
            )
        }
    }
}

/** El admin escribe el monto y el día. Se valida aquí para avisar rápido; el servidor valida otra vez. */
@Composable
private fun PlanDialog(initialAmount: Double?, initialDay: Int?, onDismiss: () -> Unit, onSave: (Double, Int) -> Unit) {
    var amountText by rememberSaveable { mutableStateOf(initialAmount?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "") }
    var dayText by rememberSaveable { mutableStateOf(initialDay?.toString() ?: "") }
    val amount = Fees.parseAmount(amountText)
    val day = Fees.parseDueDay(dayText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fees_plan_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.fees_plan_amount_label)) },
                    singleLine = true,
                    // El error solo aparece cuando ya escribió algo (no al abrir el diálogo)
                    isError = amountText.isNotBlank() && amount == null,
                    supportingText = {
                        if (amountText.isNotBlank() && amount == null) Text(stringResource(R.string.fees_plan_amount_error))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it },
                    label = { Text(stringResource(R.string.fees_plan_day_label)) },
                    singleLine = true,
                    isError = dayText.isNotBlank() && day == null,
                    supportingText = {
                        if (dayText.isNotBlank() && day == null) Text(stringResource(R.string.fees_plan_day_error))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (amount != null && day != null) onSave(amount, day) },
                enabled = amount != null && day != null,
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
