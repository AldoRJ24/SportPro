package com.sportpro.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.data.NotificationRow
import com.sportpro.app.core.data.NotificationTarget
import com.sportpro.app.core.data.formatDay
import com.sportpro.app.core.data.formatTime
import com.sportpro.app.core.data.groupByDay
import com.sportpro.app.core.data.notificationDay
import com.sportpro.app.core.data.target
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.family.GuardianRequestCard
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.profile.CallupCard
import com.sportpro.app.ui.profile.FamilyViewModel
import com.sportpro.app.ui.session.SessionViewModel
import com.sportpro.app.ui.theme.Mint400
import java.time.LocalDate

/**
 * Campana de notificaciones.
 *  - Arriba, lo que pide una respuesta: solicitudes de vínculo y convocatorias.
 *  - Debajo, el historial agrupado por día (Hoy, Ayer, fecha).
 *  - Tocar una notificación la marca como leída y abre su destino ([onOpenTarget]).
 *  - «Marcar todo» las marca todas; el engranaje abre los ajustes por categoría.
 * Llegan en tiempo real mientras la app está abierta (Realtime de Supabase).
 */
@Composable
fun NotificationsScreen(
    session: SessionViewModel,
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit,
    onOpenTarget: (NotificationTarget) -> Unit,
    onSettings: () -> Unit,
) {
    val container = LocalAppContainer.current
    val ctx by session.context.collectAsStateWithLifecycle()
    val vm: FamilyViewModel = viewModel { FamilyViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(ctx) { vm.load(ctx) }
    LaunchedEffect(ui.message) { ui.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    // Se agrupa solo cuando cambia la lista, no en cada redibujado de la pantalla
    val groups = remember(ui.notifications) { groupByDay(ui.notifications) { notificationDay(it.createdAt) } }
    val today = remember { LocalDate.now() }

    SpScaffold(
        title = stringResource(R.string.notif_title),
        onBack = onBack,
        snackbarHostState = snack,
        actions = {
            // Solo aparece si hay algo sin leer
            if (ui.unread > 0) {
                TextButton(onClick = { vm.markAllRead() }) { Text(stringResource(R.string.notif_mark_all)) }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.notif_settings_title))
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Primera carga: barra de progreso. Si falló, se dice en vez de mostrar «Todo al día».
            if (ui.loading && ui.notifications.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            ui.error?.let { err ->
                item { Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
            // ---- Lo que espera una respuesta ----
            if (ui.requests.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.notif_section_links)) }
                items(ui.requests, key = { "rq" + it.linkId }) { r ->
                    GuardianRequestCard(
                        r,
                        onAccept = { vm.respondLink(r, true) { session.refreshContext() } },
                        onReject = { vm.respondLink(r, false) { } },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (ui.pendingCallups.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.notif_section_callups)) }
                items(ui.pendingCallups, key = { "cu" + it.id }) { c ->
                    CallupCard(c, onOpenMatch = onOpenMatch, onRespond = { accept -> vm.respond(c, accept, ctx) })
                }
            }

            // ---- Historial agrupado por día ----
            if (ui.notifications.isEmpty() && !ui.loading && ui.error == null) {
                item {
                    EmptyState(
                        stringResource(R.string.notif_empty_title),
                        stringResource(R.string.notif_empty_body),
                    )
                }
            }
            groups.forEach { (day, rows) ->
                item(key = "day-$day") { SectionHeader(dayLabel(day, today, rows.first().createdAt)) }
                items(rows, key = { "n" + it.id }) { n ->
                    val target = n.target()
                    NotificationLine(
                        n = n,
                        opens = target != null,
                        onClick = {
                            vm.markRead(n.id)
                            target?.let(onOpenTarget)
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            item {
                Text(
                    stringResource(R.string.notif_realtime_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** «Hoy», «Ayer» o la fecha completa. */
@Composable
private fun dayLabel(day: LocalDate?, today: LocalDate, sampleIso: String?): String = when (day) {
    null -> stringResource(R.string.notif_day_unknown)
    today -> stringResource(R.string.notif_day_today)
    today.minusDays(1) -> stringResource(R.string.notif_day_yesterday)
    else -> formatDay(sampleIso)
}

/**
 * Una notificación. El punto verde indica «sin leer»; la flecha, que al tocarla se abre una
 * pantalla ([opens]). Si no abre nada, tocarla solo la marca como leída.
 */
@Composable
private fun NotificationLine(n: NotificationRow, opens: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (n.isUnread) {
            Spacer(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(Mint400))
        } else {
            Spacer(Modifier.size(8.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, style = MaterialTheme.typography.titleSmall)
            n.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            // El día ya está en el encabezado del grupo: aquí basta la hora
            Text(formatTime(n.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (opens) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.notif_open_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
