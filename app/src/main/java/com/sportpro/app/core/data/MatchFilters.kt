package com.sportpro.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer

/**
 * Motivo por el que un partido aparece en la pestaña «Partidos» de un usuario.
 * Lo calcula el servidor (función my_matches, migración 014): la app no decide qué partidos
 * le corresponden a cada persona, solo los muestra y los filtra.
 */
@Serializable
data class MatchScope(
    @SerialName("match_id") val matchId: String,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("by_follow") val byFollow: Boolean = false,
    @SerialName("by_child") val byChild: Boolean = false,
    @SerialName("by_staff") val byStaff: Boolean = false,
    @SerialName("by_player") val byPlayer: Boolean = false,
)

/** Filtro «de quién es el partido». */
enum class MatchOrigin { ALL, FAVORITES, FOLLOWING, CHILDREN, MY_TEAMS, PLAYING }

/** Filtro por estado del partido. */
enum class MatchStatusFilter { ALL, LIVE, UPCOMING, FINISHED }

/** Lo que el usuario eligió en la barra de filtros de «Partidos». */
data class MatchFilter(
    val origin: MatchOrigin = MatchOrigin.ALL,
    val status: MatchStatusFilter = MatchStatusFilter.ALL,
    val query: String = "",
) {
    /** true si hay algún filtro puesto (sirve para ofrecer «Quitar filtros»). */
    val isActive: Boolean
        get() = origin != MatchOrigin.ALL || status != MatchStatusFilter.ALL || query.isNotBlank()
}

/** Lógica pura de los filtros de partidos: sin Android, para poder probarla con JUnit. */
object MatchFilters {

    /** Minúsculas y sin tildes, para que «leon» encuentre «León». */
    internal fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    /** ¿El partido cumple el filtro de origen? Sin datos del servidor solo pasa «Todos». */
    private fun matchesOrigin(scope: MatchScope?, origin: MatchOrigin): Boolean = when (origin) {
        MatchOrigin.ALL -> true
        MatchOrigin.FAVORITES -> scope?.isFavorite == true
        MatchOrigin.FOLLOWING -> scope?.byFollow == true
        MatchOrigin.CHILDREN -> scope?.byChild == true
        MatchOrigin.MY_TEAMS -> scope?.byStaff == true
        MatchOrigin.PLAYING -> scope?.byPlayer == true
    }

    private fun matchesStatus(m: MatchRow, status: MatchStatusFilter): Boolean = when (status) {
        MatchStatusFilter.ALL -> true
        MatchStatusFilter.LIVE -> m.isLive
        MatchStatusFilter.UPCOMING -> m.isScheduled
        MatchStatusFilter.FINISHED -> m.isFinished
    }

    /** La búsqueda mira los dos equipos, la categoría y la sede. Varias palabras: deben estar todas. */
    private fun matchesQuery(m: MatchRow, query: String): Boolean {
        val words = normalize(query).split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = normalize(
            listOfNotNull(m.home?.name, m.away?.name, m.awayExternalName, m.home?.category, m.venue).joinToString(" "),
        )
        return words.all { haystack.contains(it) }
    }

    /** Aplica los tres filtros a la vez, respetando el orden original de la lista. */
    fun apply(matches: List<MatchRow>, scopes: Map<String, MatchScope>, filter: MatchFilter): List<MatchRow> =
        matches.filter { m ->
            matchesOrigin(scopes[m.id], filter.origin) &&
                matchesStatus(m, filter.status) &&
                matchesQuery(m, filter.query)
        }

    /**
     * Filtros de origen que tiene sentido ofrecer a este usuario: «Todos» y «Favoritos» siempre;
     * los demás solo si al menos un partido suyo llega por ese motivo (un seguidor no ve «Mis hijos»).
     */
    fun availableOrigins(scopes: Collection<MatchScope>): List<MatchOrigin> = buildList {
        add(MatchOrigin.ALL)
        add(MatchOrigin.FAVORITES)
        if (scopes.any { it.byStaff }) add(MatchOrigin.MY_TEAMS)
        if (scopes.any { it.byChild }) add(MatchOrigin.CHILDREN)
        if (scopes.any { it.byPlayer }) add(MatchOrigin.PLAYING)
        if (scopes.any { it.byFollow }) add(MatchOrigin.FOLLOWING)
    }
}
