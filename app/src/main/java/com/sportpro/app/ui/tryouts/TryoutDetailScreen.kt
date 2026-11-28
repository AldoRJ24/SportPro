package com.sportpro.app.ui.tryouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.data.Fees
import com.sportpro.app.core.data.MyTryoutApplication
import com.sportpro.app.core.data.PlayerRef
import com.sportpro.app.core.data.Tryout
import com.sportpro.app.core.data.TryoutApplicant
import com.sportpro.app.core.data.TryoutEligibility
import com.sportpro.app.core.data.Tryouts
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.core.data.timeAgo
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.ReasonDialog
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.UiMessageEffect
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel
import com.sportpro.app.ui.theme.LiveRed
import com.sportpro.app.ui.theme.SuccessGreen

/**
 * Detalle de un aviso de prueba. Según quién lo abre:
 *  - apoderado (o jugador adulto): elige a quién inscribir y envía la inscripción; puede retirarla;
 *  - quien gestiona el aviso (`can_manage` del servidor): ve los inscritos, acepta o rechaza,
 *    y cierra, reabre o cancela el aviso.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TryoutDetailScreen(tryoutId: String, session: SessionViewModel, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: TryoutDetailViewModel = viewModel(key = "tryout-$tryoutId") { TryoutDetailViewModel(container, tryoutId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx by session.context.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    UiMessageEffect(ui.message, snack, vm::consumeMessage)

    // A quién puedo inscribir: mis hijos vinculados y, si ya soy mayor de edad, yo mismo.
    // (Solo decide qué chips mostrar; el servidor comprueba el permiso al inscribir.)
    val candidates: List<PlayerRef> = ctx?.children.orEmpty() +
        listOfNotNull(ctx?.ownPlayer?.takeIf { Fees.isAdult(it.birthDate) })
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var rejecting by remember { mutableStateOf<TryoutApplicant?>(null) }

    val tryout = ui.tryout
    SpScaffold(title = stringResource(R.string.tryouts_detail_title), onBack = onBack, snackbarHostState = snack) { pad ->
        when {
            ui.loading && tryout == null && ui.mine.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && tryout == null -> ErrorBox(ui.error!!, onRetry = { vm.load() }, modifier = Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (tryout == null) {
                    // Cerrado, cancelado o ya pasó: no aparece en la búsqueda, pero mis inscripciones sí
                    item {
                        EmptyState(
                            stringResource(R.string.tryouts_gone_title),
                            stringResource(R.string.tryouts_gone_body),
                        )
                    }
                } else {
                    item { TryoutInfoCard(tryout) }

                    // ---- Inscribir ----
                    if (tryout.isOpen && candidates.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.tryouts_apply_header)) }
                        item {
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    candidates.forEach { p ->
                                        // Quien ya tiene una inscripción viva no se puede volver a elegir
                                        val already = ui.mine.any { it.playerId == p.id && it.isLive }
                                        FilterChip(
                                            selected = selectedId == p.id,
                                            onClick = { selectedId = p.id },
                                            enabled = !already,
                                            label = { Text(p.fullName) },
                                        )
                                    }
                                }
                                val chosen = candidates.firstOrNull { it.id == selectedId }
                                val eligibility = chosen?.let { Tryouts.eligibility(it.birthDate, tryout) }
                                // Se avisa de la edad ANTES de enviar; el servidor la valida de todos modos
                                when (eligibility) {
                                    TryoutEligibility.TOO_YOUNG, TryoutEligibility.TOO_OLD -> Text(
                                        stringResource(R.string.tryouts_apply_age_error, tryout.minAge, tryout.maxAge),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LiveRed,
                                    )
                                    else -> Unit
                                }
                                if (tryout.slotsLeft == 0) {
                                    Text(stringResource(R.string.tryouts_apply_full), style = MaterialTheme.typography.bodySmall, color = LiveRed)
                                }
                                Button(
                                    onClick = { chosen?.let { vm.apply(it.id); selectedId = null } },
                                    enabled = !ui.busy && chosen != null && tryout.slotsLeft > 0 &&
                                        (eligibility == TryoutEligibility.OK || eligibility == TryoutEligibility.UNKNOWN),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.tryouts_apply_action)) }
                            }
                        }
                    }
                }

                // ---- Mis inscripciones a este aviso ----
                if (ui.mine.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.tryouts_mine_header)) }
                    items(ui.mine, key = { "m" + it.id }) { a ->
                        MyApplicationCard(a, busy = ui.busy, onWithdraw = { vm.withdraw(a.id) })
                    }
                }

                // ---- Gestión (admin de la academia o DT que publicó) ----
                if (tryout?.canManage == true) {
                    item { SectionHeader(stringResource(R.string.tryouts_manage_header)) }
                    item {
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (tryout.status) {
                                "open" -> OutlinedButton(onClick = { vm.setStatus("closed") }, enabled = !ui.busy) {
                                    Text(stringResource(R.string.tryouts_manage_close))
                                }
                                "closed" -> OutlinedButton(onClick = { vm.setStatus("open") }, enabled = !ui.busy) {
                                    Text(stringResource(R.string.tryouts_manage_reopen))
                                }
                            }
                            if (tryout.status != "cancelled") {
                                TextButton(onClick = { vm.setStatus("cancelled") }, enabled = !ui.busy) {
                                    Text(stringResource(R.string.tryouts_manage_cancel))
                                }
                            }
                        }
                    }
                    item {
                        SectionHeader(pluralStringResource(R.plurals.tryouts_applicants, ui.applicants.size, ui.applicants.size))
                    }
                    if (ui.applicants.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.tryouts_applicants_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    items(ui.applicants, key = { "a" + it.id }) { a ->
                        ApplicantCard(
                            a = a,
                            busy = ui.busy,
                            canAccept = tryout.slotsLeft > 0,
                            onAccept = { vm.accept(a.id) },
                            onReject = { rejecting = a },
                        )
                    }
                }
            }
        }
    }

    rejecting?.let { a ->
        ReasonDialog(
            title = stringResource(R.string.tryouts_reject_title),
            onDismiss = { rejecting = null },
            onSubmit = { reason -> rejecting = null; vm.reject(a.id, reason) },
        )
    }
}

@Composable
private fun TryoutInfoCard(t: Tryout) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TryoutStatusPill(t)
            }
            Text("${t.academyName} · ${t.category}", color = muted)
            Text(formatDayTime(t.eventAt), style = MaterialTheme.typography.bodyLarge)
            Text("${t.venue} · ${t.district}")
            Text(stringResource(R.string.tryouts_ages, t.minAge, t.maxAge), color = muted)
            Text(stringResource(R.string.tryouts_positions_wanted, positionsLabel(t.positions)), color = muted)
            Text(
                pluralStringResource(R.plurals.tryouts_slots_left, t.slotsLeft, t.slotsLeft, t.slots),
                color = if (t.slotsLeft == 0) LiveRed else SuccessGreen,
            )
            t.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun MyApplicationCard(a: MyTryoutApplication, busy: Boolean, onWithdraw: () -> Unit) {
    val (statusText, statusColor) = applicationStatus(a.status)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(a.playerName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Pill(statusText, statusColor)
            }
            a.reason?.let {
                Text(stringResource(R.string.tryouts_reason, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Solo se retira lo que sigue vivo (pendiente o aceptada)
            if (a.isLive) {
                TextButton(onClick = onWithdraw, enabled = !busy) { Text(stringResource(R.string.tryouts_withdraw)) }
            }
        }
    }
}

/** Un inscrito visto por quien gestiona. El nombre de un menor llega del servidor como «Nombre I.». */
@Composable
private fun ApplicantCard(a: TryoutApplicant, busy: Boolean, canAccept: Boolean, onAccept: () -> Unit, onReject: () -> Unit) {
    val (statusText, statusColor) = applicationStatus(a.status)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(
                        a.playerName,
                        a.playerAge?.let { pluralStringResource(R.plurals.tryouts_years, it, it) },
                        a.playerPosition?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Pill(statusText, statusColor)
            }
            Text(
                stringResource(
                    if (a.byGuardian) R.string.tryouts_applied_by_guardian else R.string.tryouts_applied_by_player,
                    a.requestedByName,
                ) + " · " + timeAgo(a.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            a.reason?.let { Text(stringResource(R.string.tryouts_reason, it), style = MaterialTheme.typography.bodySmall, color = muted) }
            if (a.status == "pending") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sin cupos libres no se puede aceptar a nadie más (el servidor también lo impide)
                    Button(onClick = onAccept, enabled = !busy && canAccept) { Text(stringResource(R.string.tryouts_accept)) }
                    OutlinedButton(onClick = onReject, enabled = !busy) { Text(stringResource(R.string.tryouts_reject)) }
                }
            }
        }
    }
}
