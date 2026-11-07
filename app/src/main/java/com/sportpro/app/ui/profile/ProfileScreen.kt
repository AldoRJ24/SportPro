package com.sportpro.app.ui.profile

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.data.Callup
import com.sportpro.app.core.data.PlayerRef
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.ui.components.Avatar
import com.sportpro.app.ui.components.BellAction
import com.sportpro.app.ui.components.Crest
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.teams.TeamJoinRequestsBlock
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.PrototypeBanner
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.roleLabel
import com.sportpro.app.ui.family.FamilyCodeCard
import com.sportpro.app.ui.family.GuardianRequestCard
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.WarnAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    session: SessionViewModel,
    onOpenMatch: (String) -> Unit,
    onPlayerStats: (String, String) -> Unit,
    onTeamStats: (String, String) -> Unit,
    onTeamRoster: (String, String) -> Unit,
    onInvites: (String, String?, String?) -> Unit,
    onAcademyTeams: (String, String) -> Unit,
    onAddChild: () -> Unit,
    onEditProfile: () -> Unit,
    onBell: () -> Unit,
    onJoinTeam: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val ctx by session.context.collectAsStateWithLifecycle()
    val ctxError by session.contextError.collectAsStateWithLifecycle()
    val vm: FamilyViewModel = viewModel { FamilyViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(ctx) { vm.load(ctx) }
    LaunchedEffect(ui.message) {
        ui.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    SpScaffold(title = "Perfil", isTab = true, actions = { BellAction(onBell) }, snackbarHostState = snack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(ctx?.displayName ?: "?", ctx?.profile?.avatarPath, size = 72.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ctx?.displayName ?: "Cargando…", style = MaterialTheme.typography.titleLarge)
                        if (ctx?.isPlatformAdmin == true) Pill("Superadmin", Mint400)
                    }
                    OutlinedButton(onClick = onEditProfile) { Text("Editar perfil") }
                }
            }
            ctxError?.let { err ->
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(err, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { session.refreshContext() }) { Text("Reintentar") }
                    }
                }
            }
            val memberships = ctx?.memberships.orEmpty()
            if (memberships.isNotEmpty()) {
                item { SectionHeader("Mis academias y roles") }
                item {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        memberships.forEach { m ->
                            Pill("${m.academy?.name ?: "Academia"} · ${roleLabel(m.role)}", MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            // ---- Convocatorias pendientes (US-04) ----
            if (ui.pendingCallups.isNotEmpty()) {
                item { SectionHeader("Convocatorias por responder") }
                items(ui.pendingCallups, key = { "cu" + it.id }) { c ->
                    CallupCard(c, onOpenMatch, onRespond = { accept -> vm.respond(c, accept, ctx) })
                }
            }

            // ---- Invitaciones: superadmin → administradores de academia ----
            if (ctx?.isPlatformAdmin == true) {
                item { SectionHeader("Superadmin") }
                item {
                    ActionCard(
                        title = "Invitación para ser admin de academias",
                        subtitle = "Genera un código QR. Quien lo escanee se registra como administrador y crea su academia.",
                        action = "Generar QR",
                        onClick = { onInvites("adm", null, null) },
                    )
                }
            }

            // ---- ADM: invitar entrenadores y gestionar equipos de su academia ----
            ctx?.adminAcademies.orEmpty().forEach { m ->
                val academyName = m.academy?.name ?: "Mi academia"
                item { SectionHeader("Administración · $academyName") }
                item {
                    ActionCard(
                        title = "Invitar entrenador (DT)",
                        subtitle = "QR de un solo uso: quien lo escanee se registra como DT de $academyName.",
                        action = "Generar QR",
                        onClick = { onInvites("dt", m.academyId, academyName) },
                    )
                }
                item {
                    ActionCard(
                        title = "Equipos y entrenadores",
                        subtitle = "Crea los equipos de la academia y asigna a cada uno su DT.",
                        action = "Abrir",
                        onClick = { onAcademyTeams(m.academyId, academyName) },
                    )
                }
            }

            // ---- Mis hijos (D-08 + código familiar y solicitudes, migración 003) ----
            if (ctx?.isGuardian == true || ctx?.children?.isNotEmpty() == true) {
                item { SectionHeader("Mis hijos") }
                if (ctx?.isGuardianAccount == true) {
                    item { FamilyCodeCard(ui.guardianCode, Modifier.padding(horizontal = 16.dp)) }
                    item {
                        Text(
                            if (ui.requests.isEmpty()) "Solicitudes pendientes: ninguna por ahora."
                            else "Solicitudes pendientes (${ui.requests.size})",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(ui.requests, key = { "rq" + it.linkId }) { r ->
                        GuardianRequestCard(
                            r,
                            onAccept = { vm.respondLink(r, true) { session.refreshContext() } },
                            onReject = { vm.respondLink(r, false) { } },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                val children = ctx?.children.orEmpty()
                if (children.isEmpty()) {
                    item { EmptyState("Aún no hay perfiles", "Crea el perfil de tu hijo menor de 13 años.") }
                }
                items(children, key = { "ch" + it.id }) { child ->
                    val teams = ui.memberships.filter { it.playerId == child.id }
                    PlayerCard(
                        player = child,
                        teamsText = teams.joinToString { "${it.team?.name ?: "Equipo"} · #${it.jerseyNumber ?: "-"}" },
                        onStats = { onPlayerStats(child.id, child.fullName) },
                    )
                }
                item {
                    OutlinedButton(onClick = onAddChild, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Agregar hijo menor de 13")
                    }
                }
            }

            // ---- Mi perfil de jugador ----
            ctx?.ownPlayer?.let { p ->
                item { SectionHeader("Mi perfil de jugador") }
                item {
                    PlayerCard(
                        player = p,
                        teamsText = ui.memberships.filter { it.playerId == p.id }.joinToString { it.team?.name ?: "" },
                        onStats = { onPlayerStats(p.id, p.fullName) },
                    )
                }
            }

            // ---- Unirse a un equipo (jugador con cuenta o apoderado de un hijo) ----
            if (ctx?.ownPlayer != null || ctx?.isGuardian == true) {
                item {
                    ActionCard(
                        title = "Unirme a un equipo",
                        subtitle = "Escanea el QR o escribe el código que te dio el DT. Él recibe tu solicitud y la acepta.",
                        action = "Abrir",
                        onClick = onJoinTeam,
                    )
                }
            }

            // ---- Equipos que gestiono (DT/ADM) ----
            val managed = ctx?.managedTeams.orEmpty()
            if (managed.isNotEmpty()) {
                // Solicitudes para unirse (se actualizan solas al llegar la notificación)
                item { TeamJoinRequestsBlock(onChanged = { session.refreshContext() }) }
                item { SectionHeader("Equipos que gestiono") }
                items(managed, key = { "mt" + it.id }) { t ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TeamCrest(t.name, t.crestPath, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${t.category ?: ""} · formato ${t.defaultFormatPlayers} · ${t.halfMinutes}' por tiempo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column {
                                TextButton(onClick = { onTeamRoster(t.id, t.name) }) { Text("Plantel e invitar") }
                                TextButton(onClick = { onTeamStats(t.id, t.name) }) { Text("Estadísticas") }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.padding(8.dp)) }
            item {
                PrototypeBanner(
                    "Mensualidades (modo simulación), documentos y ajustes de notificaciones: próxima migración.",
                    Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                OutlinedButton(onClick = { session.signOut() }, modifier = Modifier.padding(16.dp)) { Text("Cerrar sesión") }
            }
        }
    }
}

@Composable
fun CallupCard(c: Callup, onOpenMatch: (String) -> Unit, onRespond: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("CONVOCATORIA", WarnAmber)
                Spacer(Modifier.width(8.dp))
                Text(c.player?.fullName ?: "Jugador", style = MaterialTheme.typography.titleSmall)
            }
            Text(c.match?.title ?: "Partido", style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(formatDayTime(c.match?.scheduledAt), c.match?.venue).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRespond(true) }) { Text("Confirmar") }
                OutlinedButton(onClick = { onRespond(false) }) { Text("No asistirá") }
                TextButton(onClick = { onOpenMatch(c.matchId) }) { Text("Ver") }
            }
        }
    }
}

@Composable
private fun PlayerCard(player: PlayerRef, teamsText: String, onStats: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Crest(player.fullName, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(player.fullName, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(player.position, teamsText.ifBlank { null }).joinToString(" · ").ifBlank { "Sin equipo asignado" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onStats) { Text("Estadísticas") }
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClick) { Text(action) }
        }
    }
}
