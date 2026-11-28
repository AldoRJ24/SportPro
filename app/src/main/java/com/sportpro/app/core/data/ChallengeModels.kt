package com.sportpro.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer

// ---------------- Desafíos entre equipos registrados (migración 015) ----------------
// Un DT desafía al equipo de otro entrenador; el partido solo se crea si el otro acepta.

/** Equipo de la academia rival, como lo devuelve challenge_rival_teams. */
@Serializable
data class RivalTeam(
    @SerialName("team_id") val teamId: String,
    val name: String,
    val category: String? = null,
    @SerialName("max_age") val maxAge: Int? = null,
    /** Entrenador del equipo: se muestra antes de elegir, para saber a quién se desafía. */
    @SerialName("coach_name") val coachName: String? = null,
    /** false = fuera de la división permitida (lo decide el servidor). */
    val eligible: Boolean = false,
    /** Por qué no se puede elegir, si [eligible] es false. */
    val reason: String? = null,
)

/** Entrenador de un equipo que gestiono, como lo devuelve managed_team_coaches. */
@Serializable
data class TeamCoach(
    @SerialName("team_id") val teamId: String,
    @SerialName("coach_name") val coachName: String? = null,
    @SerialName("i_coach") val iCoach: Boolean = false,
)

/** Un desafío recibido o enviado, como lo devuelve my_challenges. */
@Serializable
data class ChallengeRow(
    val id: String,
    /** true = me desafiaron a mí (puedo aceptar o rechazar). */
    val incoming: Boolean = false,
    /** true = lo envié yo (puedo cancelarlo mientras esté pendiente). */
    val outgoing: Boolean = false,
    /** pending, accepted, rejected, cancelled o expired. */
    val status: String = "pending",
    @SerialName("home_team_id") val homeTeamId: String,
    @SerialName("home_team_name") val homeTeamName: String,
    @SerialName("home_category") val homeCategory: String? = null,
    @SerialName("home_academy_name") val homeAcademyName: String? = null,
    @SerialName("home_coach_name") val homeCoachName: String? = null,
    @SerialName("away_team_id") val awayTeamId: String,
    @SerialName("away_team_name") val awayTeamName: String,
    @SerialName("away_category") val awayCategory: String? = null,
    @SerialName("away_academy_name") val awayAcademyName: String? = null,
    @SerialName("away_coach_name") val awayCoachName: String? = null,
    @SerialName("format_players") val formatPlayers: Int = 7,
    @SerialName("half_minutes") val halfMinutes: Int = 25,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    val venue: String? = null,
    val visibility: String = "followers",
    @SerialName("decision_reason") val decisionReason: String? = null,
    @SerialName("match_id") val matchId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // ---- Aceptación por lado (migración 021) ----
    /** El entrenador local ya aceptó (en un desafío normal, al enviarlo). */
    @SerialName("home_accepted") val homeAccepted: Boolean = true,
    /** El entrenador visitante ya aceptó. */
    @SerialName("away_accepted") val awayAccepted: Boolean = false,
    /** true = lo propuso un administrador de academia: deben aceptar los dos DT. */
    @SerialName("proposed_by_admin") val proposedByAdmin: Boolean = false,
    @SerialName("proposed_by_name") val proposedByName: String? = null,
) {
    val isPending: Boolean get() = status == "pending"
    /** Puedo responderlo: es para mí y sigue pendiente. */
    val canRespond: Boolean get() = incoming && isPending
    /** Puedo cancelarlo: lo envié yo, sigue pendiente y no es además mío para responder. */
    val canCancel: Boolean get() = outgoing && isPending && !incoming
}

/** Lógica pura de los desafíos: sin Android, para poder probarla con JUnit. */
object Challenges {

    /**
     * Regla de división, igual que en el servidor: las edades máximas deben estar a 1 de
     * distancia (un Sub-14 juega contra Sub-13, Sub-14 o Sub-15). Un equipo sin edad máxima
     * solo juega contra otro sin edad máxima. El servidor vuelve a validarlo siempre.
     */
    fun divisionOk(mine: Int?, rival: Int?): Boolean = when {
        mine == null && rival == null -> true
        mine == null || rival == null -> false
        else -> kotlin.math.abs(mine - rival) <= 1
    }

    /** Minúsculas y sin tildes, para que «leon» encuentre «León». */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    /** Filtro del buscador de academias: por nombre o ciudad. Texto vacío = todas. */
    fun filterAcademies(academies: List<AcademyRef>, query: String): List<AcademyRef> {
        val q = normalize(query)
        if (q.isEmpty()) return academies
        return academies.filter { normalize(it.name + " " + (it.city ?: "")).contains(q) }
    }

    /** Primero los desafíos que esperan mi respuesta, luego los que envié y siguen pendientes, y al final el historial. */
    fun sorted(rows: List<ChallengeRow>): List<ChallengeRow> =
        rows.sortedWith(compareBy({ !it.canRespond }, { !it.isPending }))
}
