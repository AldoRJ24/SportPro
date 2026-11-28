package com.sportpro.app.ui.profile

import com.sportpro.app.core.AppText
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R
import com.sportpro.app.core.data.Callup
import com.sportpro.app.core.data.Fees
import com.sportpro.app.core.data.PlayerRef
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.ui.components.Avatar
import com.sportpro.app.ui.components.BellAction
import com.sportpro.app.ui.components.Crest
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.teams.TeamJoinRequestsBlock
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.TeamName
import com.sportpro.app.ui.components.roleLabel
import com.sportpro.app.ui.family.FamilyCodeCard
import com.sportpro.app.ui.family.GuardianRequestCard
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.ButtonDefaults
import com.sportpro.app.ui.components.GroupedRow
import com.sportpro.app.ui.components.GroupedSection
import com.sportpro.app.ui.components.SegmentedControl
import com.sportpro.app.ui.theme.LiveRed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import com.sportpro.app.core.data.AcademySubscription
import com.sportpro.app.core.data.Plan
import com.sportpro.app.ui.components.GoldMid
import com.sportpro.app.ui.theme.ThemeMode
import com.sportpro.app.ui.theme.ThemeSettings
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
    onTeamFees: () -> Unit = {},
    onMyFees: () -> Unit = {},
    onPlayerDocs: (String, String) -> Unit = { _, _ -> },
    onTeamDocs: (String, String) -> Unit = { _, _ -> },
    onCalendar: () -> Unit = {},
    onNotificationSettings: () -> Unit = {},
    onPlans: (academyId: String, name: String) -> Unit = { _, _ -> },
) {
    val container = LocalAppContainer.current
    val ctx by session.context.collectAsStateWithLifecycle()
    val ctxError by session.contextError.collectAsStateWithLifecycle()
    val vm: FamilyViewModel = viewModel { FamilyViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(ctx) { vm.load(ctx) }
    // Al volver al perfil se recargan equipos, convocatorias y solicitudes: si el DT aceptó la
    // solicitud mientras tanto, el equipo nuevo aparece aunque no haya llegado el aviso en tiempo real.
    // (Atado a la pantalla y no a ctx: refreshContext cambia ctx y no debe volver a dispararlo.)
    val currentCtx by rememberUpdatedState(ctx)
    LifecycleResumeEffect(vm) {
        vm.load(currentCtx)
        session.refreshContext()
        onPauseOrDispose { }
    }
    // Plan de cada academia que administro (para la tarjeta «Plan de …»)
    val plans by produceState(emptyList<AcademySubscription>(), ctx?.userId) {
        value = if (ctx?.adminAcademies.orEmpty().isEmpty()) emptyList()
        else runCatching { container.subscriptions.mine() }.getOrDefault(emptyList())
    }
    LaunchedEffect(ui.message) {
        ui.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    SpScaffold(title = stringResource(R.string.profile_perfil), isTab = true, actions = { BellAction(onBell) }, snackbarHostState = snack) { pad ->
        // contentPadding (y no padding): la lista pasa por debajo de las barras de vidrio
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            item {
                // Cabecera: foto, nombre y «Editar perfil» como enlace azul (estilo tarjeta de contacto de iOS)
                Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(ctx?.displayName ?: "?", ctx?.profile?.avatarPath, size = 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ctx?.displayName ?: stringResource(R.string.profile_cargando), style = MaterialTheme.typography.titleLarge)
                        if (ctx?.isPlatformAdmin == true) Pill(stringResource(R.string.profile_superadmin), MaterialTheme.colorScheme.primary)
                        TextButton(onClick = onEditProfile, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.profile_editar_perfil)) }
                    }
                }
            }
            ctxError?.let { err ->
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(err, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { session.refreshContext() }) { Text(stringResource(R.string.profile_reintentar)) }
                    }
                }
            }
            val memberships = ctx?.memberships.orEmpty()
            if (memberships.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.profile_mis_academias_y_roles)) }
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
                item { SectionHeader(stringResource(R.string.profile_convocatorias_por_responder)) }
                items(ui.pendingCallups, key = { "cu" + it.id }) { c ->
                    CallupCard(c, onOpenMatch, onRespond = { accept -> vm.respond(c, accept, ctx) })
                }
            }

            // ---- Invitaciones: superadmin → administradores de academia ----
            if (ctx?.isPlatformAdmin == true) {
                item { SectionHeader(stringResource(R.string.profile_superadmin)) }
                item {
                    ActionCard(
                        title = stringResource(R.string.profile_invitacion_para_ser_admin_de),
                        subtitle = stringResource(R.string.profile_genera_un_codigo_qr_quien),
                        action = stringResource(R.string.profile_generar_qr),
                        onClick = { onInvites("adm", null, null) },
                    )
                }
            }

            // ---- ADM: invitar entrenadores y gestionar equipos de su academia ----
            ctx?.adminAcademies.orEmpty().forEach { m ->
                val academyName = m.academy?.name ?: AppText.get(R.string.profile_mi_academia)
                item { SectionHeader(stringResource(R.string.profile_administracion_x, academyName)) }
                // Plan de la academia (pago simulado): Básico, Black o Gold
                item {
                    val sub = plans.firstOrNull { it.academyId == m.academyId }
                    val plan = sub?.planOrNull
                    GroupedSection(Modifier.padding(bottom = 6.dp)) {
                        GroupedRow(
                            icon = Icons.Rounded.Star,
                            iconTint = when (plan) {
                                Plan.GOLD -> GoldMid
                                Plan.BLACK -> Color.Black
                                else -> MaterialTheme.colorScheme.primary
                            },
                            title = stringResource(R.string.subs_profile_card, academyName),
                            subtitle = if (plan == null) stringResource(R.string.subs_profile_none)
                            else stringResource(
                                R.string.subs_profile_plan,
                                plan.name.lowercase().replaceFirstChar { it.uppercase() }.replace("Basic", "Básico"),
                                stringResource(if (sub.billing == "annual") R.string.subs_annual_word else R.string.subs_monthly_word),
                            ),
                            onClick = { onPlans(m.academyId, academyName) },
                            divider = false,
                        )
                    }
                }
                item {
                    ActionCard(
                        title = stringResource(R.string.profile_invitar_entrenador_dt),
                        subtitle = stringResource(R.string.profile_qr_de_un_solo_uso, academyName),
                        action = stringResource(R.string.profile_generar_qr),
                        onClick = { onInvites("dt", m.academyId, academyName) },
                    )
                }
                item {
                    ActionCard(
                        title = stringResource(R.string.profile_equipos_y_entrenadores),
                        subtitle = stringResource(R.string.profile_crea_los_equipos_de_la),
                        action = stringResource(R.string.profile_abrir),
                        onClick = { onAcademyTeams(m.academyId, academyName) },
                    )
                }
            }

            // ---- Mis hijos (D-08 + código familiar y solicitudes, migración 003) ----
            if (ctx?.isGuardian == true || ctx?.children?.isNotEmpty() == true) {
                item { SectionHeader(stringResource(R.string.profile_mis_hijos)) }
                if (ctx?.isGuardianAccount == true) {
                    item { FamilyCodeCard(ui.guardianCode, Modifier.padding(horizontal = 16.dp)) }
                    item {
                        Text(
                            if (ui.requests.isEmpty()) stringResource(R.string.profile_solicitudes_pendientes_ninguna_por_ahora)
                            else stringResource(R.string.profile_solicitudes_pendientes_x, ui.requests.size),
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
                    item { EmptyState(stringResource(R.string.profile_aun_no_hay_perfiles), stringResource(R.string.profile_crea_el_perfil_de_tu)) }
                }
                items(children, key = { "ch" + it.id }) { child ->
                    val teams = ui.memberships.filter { it.playerId == child.id }
                    PlayerCard(
                        player = child,
                        teamsText = teams.joinToString { "${it.team?.name ?: "Equipo"} · #${it.jerseyNumber ?: "-"}" },
                        onStats = { onPlayerStats(child.id, child.fullName) },
                        // El apoderado sube y consulta los documentos de su hijo
                        onDocs = { onPlayerDocs(child.id, child.fullName) },
                    )
                }
                item {
                    OutlinedButton(onClick = onAddChild, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.profile_agregar_hijo_menor_de_13))
                    }
                }
            }

            // ---- Mi perfil de jugador ----
            ctx?.ownPlayer?.let { p ->
                item { SectionHeader(stringResource(R.string.profile_mi_perfil_de_jugador)) }
                item {
                    PlayerCard(
                        player = p,
                        teamsText = ui.memberships.filter { it.playerId == p.id }.joinToString { it.team?.name ?: "" },
                        onStats = { onPlayerStats(p.id, p.fullName) },
                        // Un jugador menor no gestiona sus documentos: lo hace su apoderado
                        onDocs = if (Fees.isAdult(p.birthDate)) ({ onPlayerDocs(p.id, p.fullName) }) else null,
                    )
                }
            }

            // ---- Unirse a un equipo (jugador con cuenta o apoderado de un hijo) ----
            if (ctx?.ownPlayer != null || ctx?.isGuardian == true) {
                item {
                    ActionCard(
                        title = stringResource(R.string.profile_unirme_a_un_equipo),
                        subtitle = stringResource(R.string.profile_escanea_el_qr_o_escribe),
                        action = stringResource(R.string.profile_abrir),
                        onClick = onJoinTeam,
                    )
                }
            }

            // ---- Mis mensualidades: las paga el apoderado; el jugador solo si ya es mayor de edad ----
            // (es solo para mostrar u ocultar la tarjeta: el servidor decide qué cobros ve cada uno)
            if (ctx?.isGuardian == true || Fees.isAdult(ctx?.ownPlayer?.birthDate)) {
                item {
                    ActionCard(
                        title = stringResource(R.string.fees_profile_payer_title),
                        subtitle = stringResource(R.string.fees_profile_payer_subtitle),
                        action = stringResource(R.string.common_open),
                        onClick = onMyFees,
                    )
                }
            }

            // ---- Equipos que gestiono (DT/ADM) ----
            val managed = ctx?.managedTeams.orEmpty()
            if (managed.isNotEmpty()) {
                // Solicitudes para unirse (se actualizan solas al llegar la notificación)
                item { TeamJoinRequestsBlock(onChanged = { session.refreshContext() }) }
                item { SectionHeader(stringResource(R.string.profile_equipos_que_gestiono)) }
                items(managed, key = { "mt" + it.id }) { t ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TeamCrest(t.name, t.crestPath, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                TeamName(t.name, id = t.id, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.profile_x_formato_x_x_por, t.category ?: "", t.defaultFormatPlayers, t.halfMinutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column {
                                TextButton(onClick = { onTeamRoster(t.id, t.name) }) { Text(stringResource(R.string.profile_plantel_e_invitar)) }
                                TextButton(onClick = { onTeamStats(t.id, t.name) }) { Text(stringResource(R.string.profile_estadisticas)) }
                                TextButton(onClick = { onTeamDocs(t.id, t.name) }) { Text(stringResource(R.string.docs_button)) }
                            }
                        }
                    }
                }
                // Mensualidades de esos equipos (el admin fija el monto y confirma pagos; el DT elige jugadores y mira)
                item {
                    ActionCard(
                        title = stringResource(R.string.fees_profile_staff_title),
                        subtitle = stringResource(R.string.fees_profile_staff_subtitle),
                        action = stringResource(R.string.common_open),
                        onClick = onTeamFees,
                    )
                }
            }

            // ---- Ajustes: calendario, notificaciones y apariencia ----
            item { SectionHeader(stringResource(R.string.settings_title)) }
            item {
                GroupedSection {
                    GroupedRow(
                        icon = Icons.Rounded.DateRange,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.calendar_calendario),
                        subtitle = stringResource(R.string.settings_calendar_sub),
                        onClick = onCalendar,
                    )
                    GroupedRow(
                        icon = Icons.Rounded.Notifications,
                        iconTint = LiveRed,
                        title = stringResource(R.string.notif_title),
                        subtitle = stringResource(R.string.settings_notifications_sub),
                        onClick = onNotificationSettings,
                        divider = false,
                    )
                }
            }
            item { AppearanceSetting() }
            item {
                // Cerrar sesión: texto rojo centrado en su propia tarjeta, como en iOS
                GroupedSection(Modifier.padding(top = 24.dp)) {
                    TextButton(
                        onClick = { session.signOut() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.profile_cerrar_sesion)) }
                }
            }
        }
    }
}

/**
 * Apariencia: Claro (predeterminado), Oscuro o Sistema. El cambio se ve al instante y se guarda
 * en este celular.
 */
@Composable
private fun AppearanceSetting() {
    val mode by ThemeSettings.mode.collectAsStateWithLifecycle()
    val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
        SegmentedControl(
            options = listOf(stringResource(R.string.theme_light), stringResource(R.string.theme_dark), stringResource(R.string.theme_system)),
            selected = modes.indexOf(mode),
            onSelect = { ThemeSettings.set(modes[it]) },
        )
        Text(
            stringResource(R.string.settings_appearance_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
fun CallupCard(c: Callup, onOpenMatch: (String) -> Unit, onRespond: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(stringResource(R.string.profile_convocatoria), WarnAmber)
                Spacer(Modifier.width(8.dp))
                Text(c.player?.fullName ?: stringResource(R.string.profile_jugador), style = MaterialTheme.typography.titleSmall)
            }
            Text(c.match?.title ?: stringResource(R.string.profile_partido), style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(formatDayTime(c.match?.scheduledAt), c.match?.venue).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRespond(true) }) { Text(stringResource(R.string.profile_confirmar)) }
                OutlinedButton(onClick = { onRespond(false) }) { Text(stringResource(R.string.profile_no_asistira)) }
                TextButton(onClick = { onOpenMatch(c.matchId) }) { Text(stringResource(R.string.profile_ver)) }
            }
        }
    }
}

@Composable
private fun PlayerCard(player: PlayerRef, teamsText: String, onStats: () -> Unit, onDocs: (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Crest(player.fullName, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(player.fullName, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(player.position, teamsText.ifBlank { null }).joinToString(" · ").ifBlank { stringResource(R.string.profile_sin_equipo_asignado) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column {
                TextButton(onClick = onStats) { Text(stringResource(R.string.profile_estadisticas)) }
                if (onDocs != null) TextButton(onClick = onDocs) { Text(stringResource(R.string.docs_button)) }
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
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
