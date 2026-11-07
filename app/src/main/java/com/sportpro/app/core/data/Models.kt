package com.sportpro.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class IdRow(val id: String)

@Serializable
data class AcademyRef(
    val id: String,
    val name: String,
    val city: String? = null,
    val status: String = "pending",
    @SerialName("crest_path") val crestPath: String? = null,
)

@Serializable
data class TeamRef(
    val id: String,
    val name: String,
    val category: String? = null,
    @SerialName("academy_id") val academyId: String? = null,
    @SerialName("crest_path") val crestPath: String? = null,
    @SerialName("default_format_players") val defaultFormatPlayers: Int = 7,
    @SerialName("half_minutes") val halfMinutes: Int = 25,
)

@Serializable
data class MatchRow(
    val id: String,
    @SerialName("academy_id") val academyId: String,
    @SerialName("home_team_id") val homeTeamId: String,
    @SerialName("away_team_id") val awayTeamId: String? = null,
    @SerialName("away_external_name") val awayExternalName: String? = null,
    @SerialName("format_players") val formatPlayers: Int = 7,
    @SerialName("half_minutes") val halfMinutes: Int = 25,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    val venue: String? = null,
    val visibility: String = "followers",
    val status: String = "scheduled",
    val period: Int = 0,
    @SerialName("period_started_at") val periodStartedAt: String? = null,
    @SerialName("elapsed_before_seconds") val elapsedBeforeSeconds: Int = 0,
    @SerialName("clock_running") val clockRunning: Boolean = false,
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("home_score") val homeScore: Int = 0,
    @SerialName("away_score") val awayScore: Int = 0,
    val home: TeamRef? = null,
    val away: TeamRef? = null,
) {
    val homeName: String get() = home?.name ?: "Local"
    val awayName: String get() = away?.name ?: awayExternalName ?: "Rival"
    val category: String? get() = home?.category
    val isLive: Boolean get() = status == "live" || status == "halftime"
    val isFinished: Boolean get() = status == "finished"
    val isScheduled: Boolean get() = status == "scheduled"
    val hasExternalRival: Boolean get() = awayTeamId == null
}

@Serializable
data class TimelineEvent(
    @SerialName("event_id") val eventId: String,
    val seq: Int,
    val code: String,
    @SerialName("type_name") val typeName: String,
    val icon: String? = null,
    @SerialName("group_kind") val groupKind: String,
    val period: Int,
    val minute: Int,
    @SerialName("minute_extra") val minuteExtra: Int = 0,
    @SerialName("team_side") val teamSide: String,
    @SerialName("player_id") val playerId: String? = null,
    @SerialName("player_name") val playerName: String? = null,
    @SerialName("jersey_number") val jerseyNumber: Int? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
    val notes: String? = null,
    val status: String = "active",
    @SerialName("recorded_late") val recordedLate: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("external_player_id") val externalPlayerId: String? = null,
) {
    fun payloadString(key: String): String? =
        (payload[key] as? kotlinx.serialization.json.JsonPrimitive)?.let { p -> if (p.isString) p.content else null }?.ifEmpty { null }
    val actorKey: String? get() = playerId ?: externalPlayerId
    val isVoided: Boolean get() = status == "voided"
    val isControl: Boolean get() = groupKind == "control"
    val minuteLabel: String get() = if (minuteExtra > 0) "$minute+$minuteExtra'" else "$minute'"
}

@Serializable
data class LineupPlayer(
    @SerialName("team_side") val teamSide: String,
    @SerialName("player_id") val playerId: String? = null,
    @SerialName("external_player_id") val externalPlayerId: String? = null,
    @SerialName("player_name") val playerName: String? = null,
    @SerialName("jersey_number") val jerseyNumber: Int? = null,
    @SerialName("is_starter") val isStarter: Boolean = true,
    @SerialName("field_position") val fieldPosition: String? = null,
) {
    val key: String get() = playerId ?: externalPlayerId ?: ""
    val label: String get() = listOfNotNull(jerseyNumber?.toString(), playerName).joinToString(" · ")
}

@Serializable
data class EventType(
    val id: String,
    @SerialName("academy_id") val academyId: String? = null,
    val code: String,
    val name: String,
    val icon: String? = null,
    @SerialName("group_kind") val groupKind: String,
    @SerialName("scoring_rule") val scoringRule: String = "none",
    @SerialName("required_payload") val requiredPayload: List<String> = emptyList(),
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 100,
)

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
)

@Serializable
data class Membership(
    @SerialName("academy_id") val academyId: String,
    val role: String,
    val status: String = "active",
    val academy: AcademyRef? = null,
)

@Serializable
data class TeamStaffRow(
    @SerialName("team_id") val teamId: String,
    @SerialName("staff_role") val staffRole: String = "dt",
)

@Serializable
data class PlayerRef(
    val id: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("birth_date") val birthDate: String? = null,
    val position: String? = null,
    @SerialName("photo_authorized") val photoAuthorized: Boolean = false,
) {
    val fullName: String get() = "$firstName $lastName"
}

@Serializable
data class TeamPlayerRow(
    val id: String,
    @SerialName("team_id") val teamId: String,
    @SerialName("player_id") val playerId: String,
    @SerialName("jersey_number") val jerseyNumber: Int? = null,
    val status: String = "active",
    /** Usual position assigned by the coach (POR/DEF/MED/DEL), editable. */
    val position: String? = null,
    val player: PlayerRef? = null,
    val team: TeamRef? = null,
)

@Serializable
data class GuardianLink(
    @SerialName("player_id") val playerId: String,
    val status: String,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    val player: PlayerRef? = null,
)

@Serializable
data class CallupMatchRef(
    val id: String,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    val venue: String? = null,
    val status: String = "scheduled",
    @SerialName("away_external_name") val awayExternalName: String? = null,
    val home: TeamRef? = null,
    val away: TeamRef? = null,
) {
    val title: String get() = "${home?.name ?: "Local"} vs ${away?.name ?: awayExternalName ?: "Rival"}"
}

@Serializable
data class Callup(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("team_id") val teamId: String,
    @SerialName("player_id") val playerId: String,
    val status: String = "pending",
    val player: PlayerRef? = null,
    val match: CallupMatchRef? = null,
)

@Serializable
data class PlayerStats(
    @SerialName("matches_played") val matchesPlayed: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    @SerialName("yellow_cards") val yellowCards: Int = 0,
    @SerialName("red_cards") val redCards: Int = 0,
    val fouls: Int = 0,
    val saves: Int = 0,
    @SerialName("own_goals") val ownGoals: Int = 0,
)

@Serializable
data class TeamStats(
    @SerialName("matches_played") val matchesPlayed: Int = 0,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    @SerialName("goals_for") val goalsFor: Int = 0,
    @SerialName("goals_against") val goalsAgainst: Int = 0,
)

@Serializable
data class FollowRow(
    val id: String,
    @SerialName("academy_id") val academyId: String? = null,
    @SerialName("team_id") val teamId: String? = null,
)

@Serializable
data class MemberRow(
    @SerialName("user_id") val userId: String,
    val role: String,
    val profile: Profile? = null,
)

@Serializable
data class OperatorRow(
    @SerialName("user_id") val userId: String,
    val active: Boolean = true,
)

@Serializable
data class SummaryRow(
    @SerialName("match_id") val matchId: String,
    val status: String = "draft",
    @SerialName("ai_text") val aiText: String? = null,
    @SerialName("final_text") val finalText: String? = null,
    @SerialName("generation_count") val generationCount: Int = 0,
    val visibility: String? = null,
)

@Serializable
data class ExternalPlayer(
    val id: String,
    val name: String,
    @SerialName("jersey_number") val jerseyNumber: Int? = null,
    @SerialName("is_starter") val isStarter: Boolean = true,
    @SerialName("field_position") val fieldPosition: String? = null,
)

/** Row typed by the DT for the external rival's roster (D-15). */
data class ExternalDraft(
    val jersey: String = "",
    val name: String = "",
    val position: Position? = null,
    val starter: Boolean = true,
)

/** Everything the app needs to know about the signed-in user (roles are always read from the server). */
data class UserContext(
    val userId: String,
    val profile: Profile?,
    val isPlatformAdmin: Boolean,
    val memberships: List<Membership>,
    val staffTeamIds: Set<String>,
    val managedTeams: List<TeamRef>,
    val children: List<PlayerRef>,
    val ownPlayer: PlayerRef?,
    val account: AccountStatus? = null,
) {
    val adminAcademies: List<Membership>
        get() = memberships.filter { it.role == "adm" && it.status == "active" }
    val isGuardianAccount: Boolean
        get() = account?.accountType == "pad" || account?.guardianCode != null || roles.contains("pad")
    val displayName: String get() = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Usuario"
    val roles: Set<String> get() = memberships.filter { it.status == "active" }.map { it.role }.toSet()
    val isStaff: Boolean get() = isPlatformAdmin || managedTeams.isNotEmpty()
    val isGuardian: Boolean get() = children.isNotEmpty() || isGuardianAccount
}

// ---------------- Cuentas, vínculo familiar, notificaciones e invitaciones (migración 003) ----------------

@Serializable
data class AccountStatus(
    @SerialName("account_type") val accountType: String? = null,
    val age: Int? = null,
    @SerialName("guardian_code") val guardianCode: String? = null,
    @SerialName("player_id") val playerId: String? = null,
    @SerialName("guardian_status") val guardianStatus: String = "none",
    @SerialName("guardian_name") val guardianName: String? = null,
    @SerialName("needs_guardian") val needsGuardian: Boolean = false,
)

@Serializable
data class InvitationCheck(
    val valid: Boolean = false,
    val kind: String? = null,
    @SerialName("academy_name") val academyName: String? = null,
    val reason: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class CreatedInvitation(
    val code: String,
    val kind: String,
    @SerialName("academy_name") val academyName: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class InvitationRow(
    val id: String,
    val code: String,
    val kind: String,
    @SerialName("academy_name") val academyName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val status: String = "active",
    @SerialName("used_by_name") val usedByName: String? = null,
)

@Serializable
data class GuardianRequest(
    @SerialName("link_id") val linkId: String,
    @SerialName("player_id") val playerId: String,
    @SerialName("player_name") val playerName: String,
    val age: Int? = null,
    @SerialName("requested_at") val requestedAt: String? = null,
)

@Serializable
data class NotificationRow(
    val id: String,
    val kind: String,
    val title: String,
    val body: String? = null,
    val data: JsonObject = JsonObject(emptyMap()),
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isUnread: Boolean get() = readAt == null
    fun dataString(key: String): String? =
        (data[key] as? kotlinx.serialization.json.JsonPrimitive)?.let { if (it.isString) it.content else null }
}

@Serializable
data class TeamStaffMember(
    @SerialName("team_id") val teamId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("staff_role") val staffRole: String = "dt",
    val profile: Profile? = null,
)

/** Data typed in the registration form (D-13 + migration 003). */
data class SignUpData(
    val accountType: String,
    val firstName: String,
    val lastName: String,
    val birthDateIso: String,
    val email: String,
    val password: String,
    val inviteCode: String? = null,
    val academyName: String? = null,
    val academyCity: String? = null,
)

// ---------------- Publicaciones de Inicio (migración 006) ----------------

/** Una publicación del feed = resumen IA aprobado de un partido, con autor, marcador y reacciones. */
@Serializable
data class FeedPost(
    @SerialName("match_id") val matchId: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val text: String = "",
    val visibility: String? = null,
    @SerialName("author_id") val authorId: String? = null,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("author_avatar") val authorAvatar: String? = null,
    @SerialName("academy_name") val academyName: String? = null,
    @SerialName("home_name") val homeName: String = "",
    @SerialName("away_name") val awayName: String = "",
    @SerialName("home_crest") val homeCrest: String? = null,
    @SerialName("away_crest") val awayCrest: String? = null,
    @SerialName("home_score") val homeScore: Int = 0,
    @SerialName("away_score") val awayScore: Int = 0,
    val likes: Int = 0,
    val liked: Boolean = false,
    val comments: Int = 0,
    @SerialName("comments_enabled") val commentsEnabled: Boolean = true,
    @SerialName("can_moderate") val canModerate: Boolean = false,
)

/** Comentario de una publicación. can_delete = es mío o soy quien modera la publicación. */
@Serializable
data class PostComment(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("author_avatar") val authorAvatar: String? = null,
    val body: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_mine") val isMine: Boolean = false,
    @SerialName("can_delete") val canDelete: Boolean = false,
)

@Serializable
data class LikeResult(val liked: Boolean = false, val likes: Int = 0)

// ---------------- Unirse a un equipo con código del DT (migración 007) ----------------

/** Resultado de revisar un código de equipo antes de enviar la solicitud. */
@Serializable
data class TeamCodeCheck(
    val valid: Boolean = false,
    val reason: String? = null,
    @SerialName("team_id") val teamId: String? = null,
    @SerialName("team_name") val teamName: String? = null,
    val category: String? = null,
    @SerialName("max_age") val maxAge: Int? = null,
    @SerialName("crest_path") val crestPath: String? = null,
    @SerialName("academy_name") val academyName: String? = null,
)

/** Solicitud pendiente que ve el DT: quién quiere entrar, a qué equipo y quién la envió. */
@Serializable
data class TeamJoinRequest(
    val id: String,
    @SerialName("team_id") val teamId: String,
    @SerialName("team_name") val teamName: String = "",
    val category: String? = null,
    @SerialName("academy_name") val academyName: String? = null,
    @SerialName("player_id") val playerId: String,
    @SerialName("player_name") val playerName: String = "",
    @SerialName("player_age") val playerAge: Int? = null,
    @SerialName("requested_by_name") val requestedByName: String = "",
    @SerialName("by_guardian") val byGuardian: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Solicitud que yo envié (para mí o para mi hijo) con su estado. */
@Serializable
data class MyTeamJoinRequest(
    val id: String,
    @SerialName("team_name") val teamName: String = "",
    val category: String? = null,
    @SerialName("academy_name") val academyName: String? = null,
    @SerialName("player_name") val playerName: String = "",
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
)
