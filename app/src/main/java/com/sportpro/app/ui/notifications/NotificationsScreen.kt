package com.sportpro.app.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.data.NotificationRow
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.PrototypeBanner
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.family.GuardianRequestCard
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.profile.CallupCard
import com.sportpro.app.ui.profile.FamilyViewModel
import com.sportpro.app.ui.session.SessionViewModel
import com.sportpro.app.ui.theme.Mint400

/** Campana: «Notificaciones» (vinculaciones, invitaciones, convocatorias: reales) y «Avisos» del DT (prototipo). */
@Composable
fun NotificationsScreen(session: SessionViewModel, onBack: () -> Unit, onOpenMatch: (String) -> Unit) {
    val container = LocalAppContainer.current
    val ctx by session.context.collectAsStateWithLifecycle()
    val vm: FamilyViewModel = viewModel { FamilyViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(ctx) { vm.load(ctx) }
    LaunchedEffect(ui.notifications.size) { vm.markAllRead() }
    LaunchedEffect(ui.message) { ui.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    SpScaffold(title = "Notificaciones", onBack = onBack, snackbarHostState = snack) { pad ->
        Column(Modifier.padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Notificaciones") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Avisos") })
            }
            if (tab == 0) {
                LazyColumn {
                    if (ui.requests.isNotEmpty()) {
                        item { SectionHeader("Solicitudes de vinculación") }
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
                        item { SectionHeader("Convocatorias por responder") }
                        items(ui.pendingCallups, key = { "cu" + it.id }) { c ->
                            CallupCard(c, onOpenMatch = onOpenMatch, onRespond = { accept -> vm.respond(c, accept, ctx) })
                        }
                    }
                    item { SectionHeader("Recientes") }
                    if (ui.notifications.isEmpty()) {
                        item { EmptyState("Todo al día", "Aquí verás solicitudes, invitaciones aceptadas y convocatorias.") }
                    }
                    items(ui.notifications, key = { "n" + it.id }) { n ->
                        NotificationLine(n)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    item {
                        PrototypeBanner(
                            "Notificaciones push (FCM) con la app cerrada: fase posterior. Dentro de la app llegan en tiempo real.",
                            Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                Column(Modifier.padding(16.dp)) {
                    PrototypeBanner("Buzón de avisos del DT (mensajes y cambios de horario para todo el equipo, con historial): próxima migración.")
                }
            }
        }
    }
}

@Composable
private fun NotificationLine(n: NotificationRow) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        if (n.isUnread) {
            Spacer(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(Mint400))
        } else {
            Spacer(Modifier.size(8.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, style = MaterialTheme.typography.titleSmall)
            n.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(formatDayTime(n.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
