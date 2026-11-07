package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.AcademyRef
import com.sportpro.app.core.data.Callup
import com.sportpro.app.core.data.FollowRow
import com.sportpro.app.core.data.GuardianLink
import com.sportpro.app.core.data.MemberRow
import com.sportpro.app.core.data.Membership
import com.sportpro.app.core.data.MyTeamJoinRequest
import com.sportpro.app.core.data.PlayerRef
import com.sportpro.app.core.data.PlayerStats
import com.sportpro.app.core.data.Profile
import com.sportpro.app.core.data.TeamCodeCheck
import com.sportpro.app.core.data.TeamJoinRequest
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.core.data.TeamRef
import com.sportpro.app.core.data.TeamStaffRow
import com.sportpro.app.core.data.TeamStats
import com.sportpro.app.core.data.UserContext
import com.sportpro.app.core.data.decodeRows
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Academies, teams, players, family links, follows and stats. */
class ClubRepository(private val client: SupabaseClient) {

    private fun uid(): String = client.auth.currentUserOrNull()?.id ?: error("No autenticado")

    /**
     * Carga el CONTEXTO del usuario al iniciar sesión (todo filtrado por RLS en el servidor): perfil,
     * si es superadmin, membresías por academia, equipos donde es staff, equipos que gestiona, hijos
     * vinculados (apoderado), su perfil de jugador y el estado de la cuenta.
     * Con esto la interfaz decide qué secciones mostrar; el servidor igual valida cada acción.
     */
    suspend fun loadContext(): UserContext {
        val uid = uid()
        val profile = client.from("profiles")
            .select(Columns.raw("id,full_name,birth_date,avatar_path")) { filter { eq("id", uid) } }
            .decodeRows<Profile>().firstOrNull()
        val isAdmin = runCatching { client.postgrest.rpc("is_platform_admin").decodeScalar<Boolean>() }.getOrDefault(false)
        val memberships = client.from("academy_memberships")
            .select(Columns.raw("academy_id,role,status,academy:academies(id,name,city,status,crest_path)")) { filter { eq("user_id", uid) } }
            .decodeRows<Membership>()
        val staff = client.from("team_staff")
            .select(Columns.raw("team_id,staff_role")) { filter { eq("user_id", uid) } }
            .decodeRows<TeamStaffRow>()
        val staffIds = staff.map { it.teamId }.toSet()
        val admAcademies = memberships.filter { it.role == "adm" && it.status == "active" }.map { it.academyId }.toSet()
        val managed = if (isAdmin || admAcademies.isNotEmpty() || staffIds.isNotEmpty()) {
            visibleTeams().filter { isAdmin || it.academyId in admAcademies || it.id in staffIds }
        } else emptyList()
        val children = client.from("guardian_player_links")
            .select(Columns.raw("player_id,status,is_primary,player:players(id,first_name,last_name,birth_date,position,photo_authorized)")) {
                filter {
                    eq("guardian_user_id", uid)
                    eq("status", "approved")
                }
            }
            .decodeRows<GuardianLink>().mapNotNull { it.player }
        val own = client.from("players")
            .select(Columns.raw(PLAYER_COLS)) { filter { eq("user_id", uid) } }
            .decodeRows<PlayerRef>().firstOrNull()
        val account = runCatching {
            client.postgrest.rpc("my_account").decodeScalar<com.sportpro.app.core.data.AccountStatus>()
        }.getOrNull()
        return UserContext(uid, profile, isAdmin, memberships, staffIds, managed, children, own, account)
    }

    suspend fun visibleTeams(): List<TeamRef> =
        client.from("teams").select(Columns.raw(TEAM_COLS)) { order("name", Order.ASCENDING) }.decodeRows()

    suspend fun visibleAcademies(): List<AcademyRef> =
        client.from("academies").select(Columns.raw("id,name,city,status,crest_path")) { order("name", Order.ASCENDING) }.decodeRows()

    suspend fun teamPlayers(teamId: String): List<TeamPlayerRow> =
        client.from("team_players")
            .select(Columns.raw("id,team_id,player_id,jersey_number,status,position,player:players($PLAYER_COLS)")) {
                filter {
                    eq("team_id", teamId)
                    eq("status", "active")
                }
                order("jersey_number", Order.ASCENDING)
            }.decodeRows()

    /** The coach assigns/edits the usual position of a player in the team (POR/DEF/MED/DEL). */
    suspend fun setTeamPlayerPosition(teamPlayerId: String, position: String?) {
        client.from("team_players").update(buildJsonObject { put("position", position) }) {
            filter { eq("id", teamPlayerId) }
        }
    }

    suspend fun teamsOfPlayers(playerIds: List<String>): List<TeamPlayerRow> {
        if (playerIds.isEmpty()) return emptyList()
        return client.from("team_players")
            .select(Columns.raw("id,team_id,player_id,jersey_number,status,team:teams($TEAM_COLS)")) {
                filter {
                    isIn("player_id", playerIds)
                    eq("status", "active")
                }
            }.decodeRows()
    }

    // ---- Follows (D-19: followers see live matches) ----
    suspend fun myFollows(): List<FollowRow> =
        client.from("follows").select(Columns.raw("id,academy_id,team_id")).decodeRows()

    suspend fun followTeam(teamId: String) {
        client.from("follows").insert(buildJsonArray { add(buildJsonObject { put("team_id", teamId) }) })
    }

    suspend fun followAcademy(academyId: String) {
        client.from("follows").insert(buildJsonArray { add(buildJsonObject { put("academy_id", academyId) }) })
    }

    suspend fun unfollow(followId: String) {
        client.from("follows").delete { filter { eq("id", followId) } }
    }

    // ---- Callups (US-04) for my children / myself ----
    suspend fun callupsForPlayers(playerIds: List<String>): List<Callup> {
        if (playerIds.isEmpty()) return emptyList()
        return client.from("match_callups")
            .select(Columns.raw(
                "id,match_id,team_id,player_id,status,player:players(id,first_name,last_name)," +
                    "match:matches(id,scheduled_at,venue,status,away_external_name," +
                    "home:teams!home_team_id(id,name,category),away:teams!away_team_id(id,name,category))"
            )) { filter { isIn("player_id", playerIds) } }
            .decodeRows()
    }

    suspend fun respondCallup(callupId: String, accept: Boolean) {
        client.from("match_callups").update(buildJsonObject { put("status", if (accept) "confirmed" else "declined") }) {
            filter { eq("id", callupId) }
        }
    }

    // ---- Stats (US-11) ----
    suspend fun playerStats(playerId: String): PlayerStats =
        client.postgrest.rpc("get_player_stats", buildJsonObject { put("p_player", playerId) })
            .decodeRows<PlayerStats>().firstOrNull() ?: PlayerStats()

    suspend fun teamStats(teamId: String): TeamStats =
        client.postgrest.rpc("get_team_stats", buildJsonObject { put("p_team", teamId) })
            .decodeRows<TeamStats>().firstOrNull() ?: TeamStats()

    // ---- Family (D-08: guardian creates the profile of an under-13 child) ----
    suspend fun createChild(first: String, last: String, birthIso: String, position: String?): String =
        client.postgrest.rpc("create_child_player", buildJsonObject {
            put("p_first", first)
            put("p_last", last)
            put("p_birth", birthIso)
            put("p_position", position ?: "")
        }).decodeScalar()

    suspend fun academyMembers(academyId: String): List<MemberRow> =
        client.from("academy_memberships")
            .select(Columns.raw("user_id,role,profile:profiles!user_id(id,full_name,avatar_path)")) {
                filter {
                    eq("academy_id", academyId)
                    eq("status", "active")
                }
            }.decodeRows()

    // ---- Unirse a un equipo con código del DT (migración 007) ----
    // Flujo: el DT comparte el código/QR de su equipo → el jugador o su apoderado envía una
    // solicitud → al DT le llega la notificación → acepta o rechaza. Siempre decide el DT.

    // Código del equipo (solo DT/admin del equipo). regenerate = true invalida el anterior.
    suspend fun teamJoinCode(teamId: String, regenerate: Boolean = false): String =
        client.postgrest.rpc("team_join_code", buildJsonObject {
            put("p_team", teamId)
            put("p_regenerate", regenerate)
        }).decodeScalar()

    // Qué equipo es ese código (academia, categoría, edad máxima) antes de enviar la solicitud.
    suspend fun checkTeamCode(code: String): TeamCodeCheck =
        client.postgrest.rpc("check_team_code", buildJsonObject { put("p_code", code) }).decodeScalar()

    // Enviar solicitud para mi perfil de jugador o para mi hijo vinculado (el servidor valida edad).
    suspend fun requestTeamJoin(code: String, playerId: String) {
        client.postgrest.rpc("request_team_join", buildJsonObject {
            put("p_code", code)
            put("p_player", playerId)
        })
    }

    // Solicitudes pendientes de los equipos que gestiono (DT/admin).
    suspend fun teamJoinRequests(): List<TeamJoinRequest> =
        client.postgrest.rpc("team_join_requests_for_me").decodeRows()

    // Mis solicitudes enviadas y su estado.
    suspend fun myTeamJoinRequests(): List<MyTeamJoinRequest> =
        client.postgrest.rpc("my_team_join_requests").decodeRows()

    // Aceptar (entra al plantel y a la academia) o rechazar. Se avisa a quien lo pidió.
    suspend fun respondTeamJoin(requestId: String, accept: Boolean) {
        client.postgrest.rpc("respond_team_join", buildJsonObject {
            put("p_request", requestId)
            put("p_accept", accept)
        })
    }

    companion object {
        const val TEAM_COLS = "id,name,category,academy_id,crest_path,default_format_players,half_minutes"
        const val PLAYER_COLS = "id,first_name,last_name,birth_date,position,photo_authorized"
    }
}
