package com.sportpro.app

import com.sportpro.app.core.data.MatchFilter
import com.sportpro.app.core.data.MatchFilters
import com.sportpro.app.core.data.MatchOrigin
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.MatchScope
import com.sportpro.app.core.data.MatchStatusFilter
import com.sportpro.app.core.data.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filtros de la pestaña «Partidos»: origen (favoritos, sigo, mis hijos…), estado y búsqueda. */
class MatchFiltersTest {

    private fun match(id: String, home: String, away: String, status: String, category: String = "Sub-15", venue: String? = null) =
        MatchRow(
            id = id, academyId = "a1", homeTeamId = "t-$id", status = status, venue = venue,
            home = TeamRef(id = "t-$id", name = home, category = category),
            awayExternalName = away,
        )

    private val live = match("m1", "Halcones", "León FC", "live", venue = "Cancha Norte")
    private val upcoming = match("m2", "Leones", "Águilas", "scheduled", category = "Sub-13")
    private val finished = match("m3", "Halcones", "Tigres", "finished")
    private val halftime = match("m4", "Pumas", "Cóndores", "halftime")
    private val all = listOf(live, upcoming, finished, halftime)

    private val scopes = listOf(
        MatchScope("m1", isFavorite = true, byStaff = true),
        MatchScope("m2", byFollow = true),
        MatchScope("m3", byChild = true, byStaff = true),
        MatchScope("m4", byPlayer = true),
    ).associateBy { it.matchId }

    private fun ids(filter: MatchFilter) = MatchFilters.apply(all, scopes, filter).map { it.id }

    // ---------- Sin filtros ----------

    @Test
    fun `sin filtros se muestran todos en el mismo orden`() {
        assertEquals(listOf("m1", "m2", "m3", "m4"), ids(MatchFilter()))
        assertFalse(MatchFilter().isActive)
    }

    // ---------- Origen ----------

    @Test
    fun `favoritos solo deja los marcados con estrella`() {
        assertEquals(listOf("m1"), ids(MatchFilter(origin = MatchOrigin.FAVORITES)))
    }

    @Test
    fun `cada origen deja solo los partidos que llegan por ese motivo`() {
        assertEquals(listOf("m2"), ids(MatchFilter(origin = MatchOrigin.FOLLOWING)))
        assertEquals(listOf("m3"), ids(MatchFilter(origin = MatchOrigin.CHILDREN)))
        assertEquals(listOf("m1", "m3"), ids(MatchFilter(origin = MatchOrigin.MY_TEAMS)))
        assertEquals(listOf("m4"), ids(MatchFilter(origin = MatchOrigin.PLAYING)))
    }

    @Test
    fun `un partido sin motivo conocido solo pasa el filtro Todos`() {
        val orphan = match("m9", "Sin dato", "Rival", "live")
        assertEquals(1, MatchFilters.apply(listOf(orphan), emptyMap(), MatchFilter()).size)
        assertEquals(0, MatchFilters.apply(listOf(orphan), emptyMap(), MatchFilter(origin = MatchOrigin.FAVORITES)).size)
    }

    // ---------- Estado ----------

    @Test
    fun `en vivo incluye el entretiempo`() {
        assertEquals(listOf("m1", "m4"), ids(MatchFilter(status = MatchStatusFilter.LIVE)))
    }

    @Test
    fun `proximos y finalizados`() {
        assertEquals(listOf("m2"), ids(MatchFilter(status = MatchStatusFilter.UPCOMING)))
        assertEquals(listOf("m3"), ids(MatchFilter(status = MatchStatusFilter.FINISHED)))
    }

    // ---------- Búsqueda ----------

    @Test
    fun `la busqueda ignora mayusculas y tildes`() {
        assertEquals(listOf("m1", "m2"), ids(MatchFilter(query = "LEON")))
        assertEquals(listOf("m2"), ids(MatchFilter(query = "aguilas")))
    }

    @Test
    fun `la busqueda mira equipo local rival categoria y sede`() {
        assertEquals(listOf("m1", "m3"), ids(MatchFilter(query = "halcones")))
        assertEquals(listOf("m3"), ids(MatchFilter(query = "tigres")))
        assertEquals(listOf("m2"), ids(MatchFilter(query = "sub-13")))
        assertEquals(listOf("m1"), ids(MatchFilter(query = "cancha norte")))
    }

    @Test
    fun `con varias palabras deben estar todas`() {
        assertEquals(listOf("m3"), ids(MatchFilter(query = "halcones tigres")))
        assertEquals(emptyList<String>(), ids(MatchFilter(query = "halcones aguilas")))
    }

    @Test
    fun `una busqueda de solo espacios no filtra`() {
        assertEquals(4, ids(MatchFilter(query = "   ")).size)
        assertFalse(MatchFilter(query = "   ").isActive)
    }

    // ---------- Combinados ----------

    @Test
    fun `los tres filtros se aplican a la vez`() {
        val f = MatchFilter(origin = MatchOrigin.MY_TEAMS, status = MatchStatusFilter.FINISHED, query = "halc")
        assertEquals(listOf("m3"), ids(f))
        assertTrue(f.isActive)
    }

    // ---------- Qué filtros se ofrecen ----------

    @Test
    fun `solo se ofrecen los origenes que aplican al usuario`() {
        val follower = listOf(MatchScope("m2", byFollow = true))
        assertEquals(
            listOf(MatchOrigin.ALL, MatchOrigin.FAVORITES, MatchOrigin.FOLLOWING),
            MatchFilters.availableOrigins(follower),
        )
        assertEquals(listOf(MatchOrigin.ALL, MatchOrigin.FAVORITES), MatchFilters.availableOrigins(emptyList()))
        assertEquals(6, MatchFilters.availableOrigins(scopes.values).size)
    }
}
