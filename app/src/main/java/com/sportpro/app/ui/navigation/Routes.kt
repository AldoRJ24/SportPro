package com.sportpro.app.ui.navigation

import kotlinx.serialization.Serializable

// Rutas con tipos (Navigation Compose 2.10): cada pantalla es una clase y sus parámetros son sus campos.
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

// Mensualidades (migración 008)
@Serializable data object FeesOverviewRoute
@Serializable data class TeamFeesRoute(val teamId: String, val name: String)
// teamId y playerId nulos = «Mis mensualidades» (lo que paga el usuario)
@Serializable data class FeeStatementRoute(val teamId: String? = null, val playerId: String? = null, val title: String? = null)

// Documentos del jugador (migración 009)
// name vacío = se llegó desde una notificación (no se conoce el nombre)
@Serializable data class PlayerDocsRoute(val playerId: String, val name: String = "")
@Serializable data class TeamDocsRoute(val teamId: String, val name: String)

// Ajustes de notificaciones (migración 010)
@Serializable data object NotificationSettingsRoute

// Avisos de pruebas (migración 011)
@Serializable data object TryoutsRoute
@Serializable data class TryoutDetailRoute(val tryoutId: String)
@Serializable data object CreateTryoutRoute
