package com.sportpro.app.ui.navigation

import com.sportpro.app.core.AppText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.sportpro.app.ui.challenges.ChallengesScreen
import com.sportpro.app.ui.creatematch.CreateMatchScreen
import com.sportpro.app.ui.creatematch.MatchSetupScreen
import com.sportpro.app.ui.lineup.LineupEditorScreen
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
import com.sportpro.app.ui.subscription.SubscriptionScreen
import com.sportpro.app.ui.components.GoldNames
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sportpro.app.ui.theme.LocalBottomBarInset
import com.sportpro.app.ui.theme.LocalShellHaze
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Pestaña del menú inferior. Los iconos se eligen dentro de la composición (necesitan pintarse). */
private data class Tab(
    val route: Any,
    val isCurrent: (NavDestination) -> Boolean,
    val label: String,
)

/**
 * Cuatro pestañas (el Calendario ahora se abre desde Perfil). Con «+» en el centro quedan dos a
 * cada lado, así el botón del DT queda en el medio exacto de la barra.
 */
private val tabs = listOf(
    Tab(HomeRoute, { it.hasRoute<HomeRoute>() }, AppText.get(R.string.main_shell_inicio)),
    Tab(MatchesRoute, { it.hasRoute<MatchesRoute>() }, AppText.get(R.string.main_shell_partidos)),
    Tab(ExploreRoute, { it.hasRoute<ExploreRoute>() }, AppText.get(R.string.main_shell_explorar)),
    Tab(ProfileRoute, { it.hasRoute<ProfileRoute>() }, AppText.get(R.string.main_shell_perfil)), // D-29: perfil abajo a la derecha
)

/** Icono relleno (pestaña elegida) y de contorno (sin elegir), como los símbolos de iOS. */
@Composable
private fun tabIcons(route: Any): Pair<Painter, Painter> = when (route) {
    HomeRoute -> rememberVectorPainter(Icons.Filled.Home) to rememberVectorPainter(Icons.Outlined.Home)
    ExploreRoute -> rememberVectorPainter(Icons.Filled.Search) to rememberVectorPainter(Icons.Outlined.Search)
    ProfileRoute -> rememberVectorPainter(Icons.Filled.Person) to rememberVectorPainter(Icons.Outlined.Person)
    else -> painterResource(R.drawable.ic_ev_ball).let { it to it }
}

/**
 * Estructura principal: menú inferior de vidrio (estilo iOS) con el «+» del DT al centro (estilo
 * TikTok) y campana arriba a la derecha en las pestañas. El contenido ocupa toda la pantalla y
 * pasa por debajo de la barra: por eso se ve desenfocado a través de ella.
 */
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

    // Nombres dorados (plan Gold) para toda la app, y planes de las academias que administro.
    // Si una academia mía todavía no tiene plan (las nuevas), se abre «Planes» para elegirlo.
    var plansAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ctx?.userId) {
        if (ctx == null) return@LaunchedEffect
        runCatching { container.subscriptions.goldNames() }.onSuccess { GoldNames.set(it.map { g -> g.id }.toSet()) }
        if (!plansAsked && ctx?.adminAcademies.orEmpty().isNotEmpty()) {
            plansAsked = true
            runCatching { container.subscriptions.mine() }.getOrNull()
                ?.firstOrNull { it.plan == null }
                ?.let { a -> nav.navigate(SubscriptionRoute(a.academyId, a.academyName, required = true)) }
        }
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

    // Vidrio de la barra inferior: el contenido de la pantalla es la fuente que se desenfoca
    val shellHaze = rememberHazeState()
    // Alto reservado al final de las listas de las pestañas: barra + borde de gestos del sistema
    val barInset = if (showBar) TabBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    val createLabel = stringResource(R.string.tab_actions_crear_partido)

    CompositionLocalProvider(
        LocalUnreadCount provides familyUi.unread,
        LocalShellHaze provides shellHaze,
        LocalBottomBarInset provides barInset,
    ) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().hazeSource(shellHaze)) {
            NavHost(nav, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(ctx, onOpenMatch = openMatch, onBell = onBell)
                }
                composable<MatchesRoute> {
                    MatchesScreen(
                        matchesVm, onOpenMatch = openMatch, onCreate = onCreate, onBell = onBell,
                        // Solo quien gestiona equipos puede desafiar o ser desafiado
                        onChallenges = if (canCreate) ({ nav.navigate(ChallengesRoute) }) else null,
                        onOpenSetup = { id -> nav.navigate(MatchSetupRoute(id)) },
                    )
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
                // El Calendario ya no es una pestaña: se abre desde Perfil y tiene flecha de volver
                composable<CalendarRoute> { CalendarScreen(matchesVm, onOpenMatch = openMatch, onBack = { nav.popBackStack() }) }
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
                        onCalendar = { nav.navigate(CalendarRoute) },
                        onPlans = { id, name -> nav.navigate(SubscriptionRoute(id, name)) },
                        onNotificationSettings = { nav.navigate(NotificationSettingsRoute) },
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
                        // Desafío enviado: todavía no hay partido, se muestra la lista de desafíos
                        onChallengeSent = {
                            nav.navigate(ChallengesRoute) { popUpTo<CreateMatchRoute> { inclusive = true } }
                        },
                    )
                }
                composable<ChallengesRoute> {
                    ChallengesScreen(
                        onBack = { nav.popBackStack() },
                        onOpenMatch = openMatch,
                        onAccepted = { id ->
                            matchesVm.refresh(true)
                            openMatch(id)
                        },
                        onCreate = { nav.navigate(CreateMatchRoute) },
                    )
                }
                composable<MatchSetupRoute> { entry ->
                    val r = entry.toRoute<MatchSetupRoute>()
                    MatchSetupScreen(
                        matchId = r.matchId,
                        onBack = { nav.popBackStack() },
                        onOpenEditor = { side -> nav.navigate(LineupEditorRoute(r.matchId, side)) },
                        onGoLive = {
                            nav.navigate(MatchDetailRoute(r.matchId)) { popUpTo<MatchSetupRoute> { inclusive = true } }
                        },
                    )
                }
                composable<LineupEditorRoute> { entry ->
                    val r = entry.toRoute<LineupEditorRoute>()
                    LineupEditorScreen(r.matchId, r.side, onBack = { nav.popBackStack() })
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
                                NotificationTarget.Challenges -> nav.navigate(ChallengesRoute)
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
                composable<SubscriptionRoute> { entry ->
                    val r = entry.toRoute<SubscriptionRoute>()
                    SubscriptionScreen(r.academyId, r.name, r.required, onBack = { nav.popBackStack() })
                }
                composable<AddChildRoute> {
                    AddChildScreen(onBack = { nav.popBackStack() }, onCreated = { session.refreshContext() })
                }
            }
        }
        // Barra inferior flotante, encima del contenido
        if (showBar) {
            GlassTabBar(
                items = tabs.map { t ->
                    val (on, off) = tabIcons(t.route)
                    TabBarItem(
                        label = t.label,
                        selected = destination?.hierarchy?.any { t.isCurrent(it) } == true,
                        iconSelected = on,
                        iconIdle = off,
                        onClick = { nav.navigateToTab(t.route) },
                    )
                },
                haze = shellHaze,
                // Solo quien dirige equipos (DT o admin de plataforma) ve el «+»
                onCreate = onCreate,
                createLabel = createLabel,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
