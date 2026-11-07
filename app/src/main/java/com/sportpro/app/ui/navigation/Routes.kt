package com.sportpro.app.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (Navigation Compose 2.10).
@Serializable data object LoginRoute
@Serializable data object RegisterRoute

@Serializable data object HomeRoute
@Serializable data object MatchesRoute
@Serializable data object ExploreRoute
@Serializable data object CalendarRoute
@Serializable data object ProfileRoute

@Serializable data class MatchDetailRoute(val matchId: String)
@Serializable data object CreateMatchRoute
@Serializable data class MatchSetupRoute(val matchId: String)
@Serializable data object NotificationsRoute
@Serializable data class SummaryRoute(val matchId: String)
@Serializable data class PlayerStatsRoute(val playerId: String, val name: String)
@Serializable data class TeamStatsRoute(val teamId: String, val name: String)
@Serializable data object AddChildRoute
@Serializable data class TeamRosterRoute(val teamId: String, val name: String)
@Serializable data class InvitesRoute(val kind: String, val academyId: String? = null, val academyName: String? = null)
@Serializable data class AcademyTeamsRoute(val academyId: String, val name: String)
@Serializable data object EditProfileRoute
@Serializable data object JoinTeamRoute
