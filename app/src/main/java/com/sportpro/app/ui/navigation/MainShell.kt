package com.sportpro.app.ui.navigation

import com.sportpro.app.core.AppText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sportpro.app.R
import com.sportpro.app.core.InviteLinks
import com.sportpro.app.core.TeamLinks
import com.sportpro.app.ui.admin.AcademyTeamsScreen
import com.sportpro.app.ui.admin.InvitesScreen
import com.sportpro.app.ui.admin.RedeemInviteDialog
import com.sportpro.app.ui.components.LocalUnreadCount
import com.sportpro.app.ui.profile.FamilyViewModel
import com.sportpro.app.ui.calendar.CalendarScreen
import com.sportpro.app.ui.creatematch.CreateMatchScreen
import com.sportpro.app.ui.creatematch.MatchSetupScreen
import com.sportpro.app.ui.docs.PlayerDocsScreen
import com.sportpro.app.ui.docs.TeamDocsScreen
import com.sportpro.app.ui.explore.ExploreScreen
import com.sportpro.app.ui.fees.FeeStatementScreen
import com.sportpro.app.ui.fees.FeesOverviewScreen
import com.sportpro.app.ui.fees.TeamFeesScreen
import com.sportpro.app.ui.home.HomeScreen
import com.sportpro.app.ui.match.MatchDetailScreen
import com.sportpro.app.ui.matches.MatchesScreen
import com.sportpro.app.ui.matches.MatchesViewModel
import com.sportpro.app.core.data.NotificationTarget
import com.sportpro.app.ui.notifications.NotificationSettingsScreen
import com.sportpro.app.ui.notifications.NotificationsScreen
import com.sportpro.app.ui.profile.AddChildScreen
import com.sportpro.app.ui.profile.EditProfileScreen
import com.sportpro.app.ui.profile.PlayerStatsScreen
import com.sportpro.app.ui.profile.ProfileScreen
import com.sportpro.app.ui.profile.TeamRosterScreen
import com.sportpro.app.ui.profile.TeamStatsScreen
import com.sportpro.app.ui.teams.JoinTeamScreen
import com.sportpro.app.ui.tryouts.CreateTryoutScreen
import com.sportpro.app.ui.tryouts.TryoutDetailScreen
import com.sportpro.app.ui.tryouts.TryoutsScreen
import com.sportpro.app.ui.tryouts.publishableAcademies
import com.sportpro.app.ui.session.SessionViewModel
import com.sportpro.app.ui.summary.SummaryScreen

private data class Tab(
    val route: Any,
    val isCurrent: (NavDestination) -> Boolean,
    val label: String,
    val icon: ImageVector?,
    val iconRes: Int?,
)

private val tabs = listOf(
    Tab(HomeRoute, { it.hasRoute<HomeRoute>() }, AppText.get(R.string.main_shell_inicio), Icons.Filled.Home, null),
    Tab(MatchesRoute, { it.hasRoute<MatchesRoute>() }, AppText.get(R.string.main_shell_partidos), null, R.drawable.ic_ev_ball),
    Tab(ExploreRoute, { it.hasRoute<ExploreRoute>() }, AppText.get(R.string.main_shell_explorar), Icons.Filled.Search, null),
    Tab(CalendarRoute, { it.hasRoute<CalendarRoute>() }, AppText.get(R.string.main_shell_calendario), Icons.Filled.DateRange, null),
    Tab(ProfileRoute, { it.hasRoute<ProfileRoute>() }, AppText.get(R.string.main_shell_perfil), Icons.Filled.Person, null), // D-29: perfil abajo a la derecha
)

/** Estructura principal inspirada en Instagram (D-21): menú inferior con iconos, «+» arriba a la izquierda y campana arriba a la derecha en las pestañas. */
@Composable
fun MainShell(session: SessionViewModel) {
    val container = LocalAppContainer.current
    val nav = rememberNavController()
    val matchesVm: MatchesViewModel = viewModel { MatchesViewModel(container) }
    val ctx by session.context.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = tabs.any { t -> destination?.hierarchy?.any { t.isCurrent(it) } == true }
    val canCreate = ctx?.isStaff == true
    val onCreate: (() -> Unit)? = if (canCreate) ({ nav.navigate(CreateMatchRoute) }) else null
    val onBell: () -> Unit = { nav.navigate(NotificationsRoute) }
    val openMatch: (String) -> Unit = { id -> nav.navigate(MatchDetailRoute(id)) }
    // Contador de la campana (tiempo real) + invitación abierta cuando ya hay sesión iniciada
    val shellFamily: FamilyViewModel = viewModel(key = "shell-family") { FamilyViewModel(container) }
    val familyUi by shellFamily.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ctx) { shellFamily.load(ctx) }
    val pendingInvite by InviteLinks.pending.collectAsStateWithLifecycle()

    // QR de equipo escaneado con la cámara del teléfono: abre «Unirme a un equipo».
    // Esa pantalla consume el código pendiente y busca el equipo sola.
    val pendingTeam by TeamLinks.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTeam) {
        if (pendingTeam != null) nav.navigate(JoinTeamRoute) { launchSingleTop = true }
    }

    // Cuando el DT responde mi solicitud llega una notificación «team_join_answer». En ese momento
    // se recarga el contexto de la sesión para que el equipo nuevo aparezca en el perfil al instante
    // (antes había que cerrar y abrir la app para verlo). La clave es el id de la notificación más
    // reciente, así solo se recarga una vez por cada aviso nuevo.
    val newest = familyUi.notifications.firstOrNull()
    LaunchedEffect(newest?.id) {
        if (newest != null && newest.isUnread && newest.kind == "team_join_answer") session.refreshContext()
    }

    pendingInvite?.let { code ->
        RedeemInviteDialog(
            code = code,
            onDismiss = { InviteLinks.clear() },
            onRedeemed = {
                InviteLinks.clear()
                session.refreshContext()
            },
        )
    }

    CompositionLocalProvider(LocalUnreadCount provides familyUi.unread) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    tabs.forEach { t ->
                        val selected = destination?.hierarchy?.any { t.isCurrent(it) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateToTab(t.route) },
                            icon = {
                                if (t.icon != null) Icon(t.icon, contentDescription = t.label)
                                else Icon(painterResource(t.iconRes!!), contentDescription = t.label)
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            NavHost(nav, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(ctx, onOpenMatch = openMatch, onCreate = onCreate, onBell = onBell)
                }
                composable<MatchesRoute> {
                    MatchesScreen(matchesVm, onOpenMatch = openMatch, onCreate = onCreate, onBell = onBell)
                }
                composable<ExploreRoute> {
                    ExploreScreen(
                        onBell = onBell,
                        onFollowChanged = { matchesVm.refresh(true) },
                        onTryouts = { nav.navigate(TryoutsRoute) },
                    )
                }
                // ---- Avisos de pruebas: búsqueda → detalle; publicar → detalle del aviso nuevo ----
                composable<TryoutsRoute> {
                    TryoutsScreen(
                        canPublish = ctx.publishableAcademies().isNotEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpen = { id -> nav.navigate(TryoutDetailRoute(id)) },
                        onCreate = { nav.navigate(CreateTryoutRoute) },
                    )
                }
                composable<TryoutDetailRoute> { entry ->
                    TryoutDetailScreen(entry.toRoute<TryoutDetailRoute>().tryoutId, session, onBack = { nav.popBackStack() })
                }
                composable<CreateTryoutRoute> {
                    CreateTryoutScreen(
                        session = session,
                        onBack = { nav.popBackStack() },
                        onCreated = { id -> nav.navigate(TryoutDetailRoute(id)) { popUpTo<CreateTryoutRoute> { inclusive = true } } },
                    )
                }
                composable<CalendarRoute> { CalendarScreen(matchesVm, onOpenMatch = openMatch, onBell = onBell) }
                composable<ProfileRoute> {
                    ProfileScreen(
                        session = session,
                        onOpenMatch = openMatch,
                        onPlayerStats = { id, name -> nav.navigate(PlayerStatsRoute(id, name)) },
                        onTeamStats = { id, name -> nav.navigate(TeamStatsRoute(id, name)) },
                        onTeamRoster = { id, name -> nav.navigate(TeamRosterRoute(id, name)) },
                        onInvites = { kind, id, name -> nav.navigate(InvitesRoute(kind, id, name)) },
                        onAcademyTeams = { id, name -> nav.navigate(AcademyTeamsRoute(id, name)) },
                        onAddChild = { nav.navigate(AddChildRoute) },
                        onEditProfile = { nav.navigate(EditProfileRoute) },
                        onBell = onBell,
                        onJoinTeam = { nav.navigate(JoinTeamRoute) },
                        onTeamFees = { nav.navigate(FeesOverviewRoute) },
                        onMyFees = { nav.navigate(FeeStatementRoute()) },
                        onPlayerDocs = { id, name -> nav.navigate(PlayerDocsRoute(id, name)) },
                        onTeamDocs = { id, name -> nav.navigate(TeamDocsRoute(id, name)) },
                    )
                }
                // ---- Documentos: plantel del equipo → documentos de un jugador ----
                composable<TeamDocsRoute> { entry ->
                    val r = entry.toRoute<TeamDocsRoute>()
                    TeamDocsScreen(
                        teamId = r.teamId,
                        name = r.name,
                        onBack = { nav.popBackStack() },
                        onOpenPlayer = { id, name -> nav.navigate(PlayerDocsRoute(id, name)) },
                    )
                }
                composable<PlayerDocsRoute> { entry ->
                    val r = entry.toRoute<PlayerDocsRoute>()
                    PlayerDocsScreen(r.playerId, r.name, onBack = { nav.popBackStack() })
                }
                // ---- Mensualidades: equipos → un equipo → movimientos ----
                composable<FeesOverviewRoute> {
                    FeesOverviewScreen(
                        onBack = { nav.popBackStack() },
                        onOpenTeam = { id, name -> nav.navigate(TeamFeesRoute(id, name)) },
                    )
                }
                composable<TeamFeesRoute> { entry ->
                    val r = entry.toRoute<TeamFeesRoute>()
                    TeamFeesScreen(
                        teamId = r.teamId,
                        name = r.name,
                        onBack = { nav.popBackStack() },
                        onOpenStatement = { playerId, title -> nav.navigate(FeeStatementRoute(r.teamId, playerId, title)) },
                    )
                }
                composable<FeeStatementRoute> { entry ->
                    val r = entry.toRoute<FeeStatementRoute>()
                    FeeStatementScreen(r.teamId, r.playerId, r.title, onBack = { nav.popBackStack() })
                }
                composable<MatchDetailRoute> { entry ->
                    val r = entry.toRoute<MatchDetailRoute>()
                    MatchDetailScreen(
                        matchId = r.matchId,
                        onBack = { nav.popBackStack() },
                        onSetup = { nav.navigate(MatchSetupRoute(r.matchId)) },
                        onSummary = { nav.navigate(SummaryRoute(r.matchId)) },
                    )
                }
                composable<CreateMatchRoute> {
                    CreateMatchScreen(
                        session = session,
                        onBack = { nav.popBackStack() },
                        onCreated = { id ->
                            matchesVm.refresh(true)
                            nav.navigate(MatchSetupRoute(id)) { popUpTo<CreateMatchRoute> { inclusive = true } }
                        },
                    )
                }
                composable<MatchSetupRoute> { entry ->
                    val r = entry.toRoute<MatchSetupRoute>()
                    MatchSetupScreen(
                        matchId = r.matchId,
                        onBack = { nav.popBackStack() },
                        onGoLive = {
                            nav.navigate(MatchDetailRoute(r.matchId)) { popUpTo<MatchSetupRoute> { inclusive = true } }
                        },
                    )
                }
                composable<NotificationsRoute> {
                    NotificationsScreen(
                        session,
                        onBack = { nav.popBackStack() },
                        onOpenMatch = openMatch,
                        // Tocar una notificación abre su pantalla. El destino lo decide la función pura
                        // notificationTarget(); aquí solo se traduce a una ruta de navegación.
                        onOpenTarget = { target ->
                            when (target) {
                                is NotificationTarget.Match -> openMatch(target.matchId)
                                NotificationTarget.Feed -> nav.navigateToTab(HomeRoute)
                                NotificationTarget.Profile -> nav.navigateToTab(ProfileRoute)
                                NotificationTarget.MyFees -> nav.navigate(FeeStatementRoute())
                                is NotificationTarget.TeamFees ->
                                    nav.navigate(FeeStatementRoute(teamId = target.teamId, playerId = target.playerId))
                                is NotificationTarget.PlayerDocs -> nav.navigate(PlayerDocsRoute(target.playerId))
                                is NotificationTarget.Tryout -> nav.navigate(TryoutDetailRoute(target.tryoutId))
                            }
                        },
                        onSettings = { nav.navigate(NotificationSettingsRoute) },
                    )
                }
                composable<NotificationSettingsRoute> {
                    NotificationSettingsScreen(onBack = { nav.popBackStack() })
                }
                composable<InvitesRoute> { entry ->
                    val r = entry.toRoute<InvitesRoute>()
                    InvitesScreen(r.kind, r.academyId, r.academyName, onBack = { nav.popBackStack() })
                }
                composable<AcademyTeamsRoute> { entry ->
                    val r = entry.toRoute<AcademyTeamsRoute>()
                    AcademyTeamsScreen(
                        academyId = r.academyId,
                        academyName = r.name,
                        onBack = { nav.popBackStack() },
                        onInviteCoach = { nav.navigate(InvitesRoute("dt", r.academyId, r.name)) },
                        // Nueva foto → recargar el contexto para que «Equipos que gestiono» la muestre ya
                        onLogoChanged = { session.refreshContext() },
                    )
                }
                composable<SummaryRoute> { entry ->
                    SummaryScreen(entry.toRoute<SummaryRoute>().matchId, onBack = { nav.popBackStack() })
                }
                composable<PlayerStatsRoute> { entry ->
                    val r = entry.toRoute<PlayerStatsRoute>()
                    PlayerStatsScreen(r.playerId, r.name, onBack = { nav.popBackStack() })
                }
                composable<TeamStatsRoute> { entry ->
                    val r = entry.toRoute<TeamStatsRoute>()
                    TeamStatsScreen(r.teamId, r.name, onBack = { nav.popBackStack() })
                }
                composable<TeamRosterRoute> { entry ->
                    val r = entry.toRoute<TeamRosterRoute>()
                    TeamRosterScreen(r.teamId, r.name, onBack = { nav.popBackStack() })
                }
                composable<EditProfileRoute> { EditProfileScreen(session, onBack = { nav.popBackStack() }) }
                composable<JoinTeamRoute> { JoinTeamScreen(session, onBack = { nav.popBackStack() }) }
                composable<AddChildRoute> {
                    AddChildScreen(onBack = { nav.popBackStack() }, onCreated = { session.refreshContext() })
                }
            }
        }
    }
    }
}

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
