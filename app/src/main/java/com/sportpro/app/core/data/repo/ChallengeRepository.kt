package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.ChallengeRow
import com.sportpro.app.core.data.RivalTeam
import com.sportpro.app.core.data.TeamCoach
import com.sportpro.app.core.data.decodeRows
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Desafíos entre equipos (migración 015).
 * Todo pasa por funciones del servidor: la app no puede escribir en match_challenges ni crear
 * directo un partido contra el equipo de otro entrenador.
 */
class ChallengeRepository(private val client: SupabaseClient) {

    /** Equipos de una academia como rivales de mi equipo, con entrenador y si están en mi división. */
    suspend fun rivalTeams(homeTeamId: String, academyId: String): List<RivalTeam> =
        client.postgrest.rpc("challenge_rival_teams", buildJsonObject {
            put("p_home_team", homeTeamId)
            put("p_academy", academyId)
        }).decodeRows()

    /** Envía el desafío. El entrenador rival recibe una notificación con todos los ajustes. */
    suspend fun create(
        homeTeamId: String, awayTeamId: String, formatPlayers: Int, halfMinutes: Int,
        scheduledAtIso: String, venue: String?, visibility: String,
    ): String =
        client.postgrest.rpc("challenge_create", buildJsonObject {
            put("p_home_team", homeTeamId)
            put("p_away_team", awayTeamId)
            put("p_format_players", formatPlayers)
            put("p_half_minutes", halfMinutes)
            put("p_scheduled_at", scheduledAtIso)
            if (!venue.isNullOrBlank()) put("p_venue", venue)
            put("p_visibility", visibility)
        }).decodeScalar()

    /** Acepta o rechaza. Al aceptar devuelve el id del partido creado; al rechazar, null. */
    suspend fun respond(challengeId: String, accept: Boolean, reason: String? = null): String? =
        client.postgrest.rpc("challenge_respond", buildJsonObject {
            put("p_challenge", challengeId)
            put("p_accept", accept)
            if (!reason.isNullOrBlank()) put("p_reason", reason)
        }).decodeScalar()

    /** Cancela un desafío que envié y que todavía no responden. */
    suspend fun cancel(challengeId: String) {
        client.postgrest.rpc("challenge_cancel", buildJsonObject { put("p_challenge", challengeId) })
    }

    /** Mis desafíos recibidos y enviados. */
    suspend fun mine(): List<ChallengeRow> =
        client.postgrest.rpc("my_challenges").decodeRows()

    /**
     * Entrenador de cada equipo que gestiono (migración 021). El administrador elige el equipo
     * local viendo quién lo dirige; [TeamCoach.iCoach] = lo dirijo yo (mi lado se acepta solo).
     */
    suspend fun managedTeamCoaches(): List<TeamCoach> =
        client.postgrest.rpc("managed_team_coaches").decodeRows()
}
