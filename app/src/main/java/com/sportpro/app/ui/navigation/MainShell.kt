package com.sportpro.app.ui.navigation

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
import com.sportpro.app.ui.admin.AcademyTeamsScreen
import com.sportpro.app.ui.admin.InvitesScreen
import com.sportpro.app.ui.admin.RedeemInviteDialog
import com.sportpro.app.ui.components.LocalUnreadCount
import com.sportpro.app.ui.profile.FamilyViewModel
import com.sportpro.app.ui.calendar.CalendarScreen
import com.sportpro.app.ui.creatematch.CreateMatchScreen
import com.sportpro.app.ui.creatematch.MatchSetupScreen
import com.sportpro.app.ui.explore.ExploreScreen
import com.sportpro.app.ui.home.HomeScreen
import com.sportpro.app.ui.match.MatchDetailScreen
import com.sportpro.app.ui.matches.MatchesScreen
import com.sportpro.app.ui.matches.MatchesViewModel
import com.sportpro.app.ui.notifications.NotificationsScreen
import com.sportpro.app.ui.profile.AddChildScreen
import com.sportpro.app.ui.profile.EditProfileScreen
import com.sportpro.app.ui.profile.PlayerStatsScreen
import com.sportpro.app.ui.profile.ProfileScreen
import com.sportpro.app.ui.profile.TeamRosterScreen
import com.sportpro.app.ui.profile.TeamStatsScreen
import com.sportpro.app.ui.teams.JoinTeamScreen
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
    Tab(HomeRoute, { it.hasRoute<HomeRoute>() }, "Inicio", Icons.Filled.Home, null),
    Tab(MatchesRoute, { it.hasRoute<MatchesRoute>() }, "Partidos", null, R.drawable.ic_ev_ball),
    Tab(ExploreRoute, { it.hasRoute<ExploreRoute>() }, "Explorar", Icons.Filled.Search, null),
    Tab(CalendarRoute, { it.hasRoute<CalendarRoute>() }, "Calendario", Icons.Filled.DateRange, null),
    Tab(ProfileRoute, { it.hasRoute<ProfileRoute>() }, "Perfil", Icons.Filled.Person, null), // D-29: profile bottom-right
)

/** Instagram-inspired shell (D-21): bottom bar with icons, "+" top-left and bell top-right on tab screens. */
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
    // Unread badge for the bell (real-time) + invitation opened while already signed in
    val shellFamily: FamilyViewModel = viewModel(key = "shell-family") { FamilyViewModel(container) }
    val familyUi by shellFamily.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ctx) { shellFamily.load(ctx) }
    val pendingInvite by InviteLinks.pending.collectAsStateWithLifecycle()

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
                    HomeScreen(matchesVm, ctx, onOpenMatch = openMatch, onCreate = onCreate, onBell = onBell)
                }
                composable<MatchesRoute> {
                    MatchesScreen(matchesVm, onOpenMatch = openMatch, onCreate = onCreate, onBell = onBell)
                }
                composable<ExploreRoute> { ExploreScreen(onBell = onBell, onFollowChanged = { matchesVm.refresh(true) }) }
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
                    )
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
                    NotificationsScreen(session, onBack = { nav.popBackStack() }, onOpenMatch = openMatch)
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
