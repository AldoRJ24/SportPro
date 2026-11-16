package com.sportpro.app

import com.sportpro.app.core.data.Formation
import com.sportpro.app.core.data.Formations
import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TimelineEvent
import com.sportpro.app.core.data.onFieldPlayers
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Formaciones y jugadores en cancha (titulares ± cambios − rojas). Lógica pura, sin red. */
class PositionsTest {

    @Before
    fun setUp() = TestText.install()

    // ---------- Formaciones ----------

    @Test
    fun `cada formacion suma exactamente los jugadores del formato`() {
        for (players in 5..11) {
            val options = Formations.forFormat(players)
            assertTrue("formato $players sin opciones", options.isNotEmpty())
            options.forEach { f -> assertEquals("formato $players, formación ${f.name}", players, f.total) }
        }
    }

    @Test
    fun `la formacion sugerida es la primera y un formato desconocido usa futbol 7`() {
        assertEquals(Formation(2, 3, 1), Formations.default(7))
        assertEquals("1-2-3-1", Formations.default(7).name)
        assertEquals(Formations.forFormat(7), Formations.forFormat(99))
    }

    @Test
    fun `detect deduce la formacion solo si hay exactamente un arquero`() {
        val ok = listOf(Position.POR, Position.DEF, Position.DEF, Position.MED, Position.MED, Position.MED, Position.DEL)
        assertEquals(Formation(2, 3, 1), Formations.detect(7, ok))
        // dos arqueros
        assertNull(Formations.detect(7, ok.drop(1) + Position.POR + Position.POR))
        // ningún arquero
        assertNull(Formations.detect(7, List(7) { Position.DEF }))
        // falta un titular
        assertNull(Formations.detect(7, ok.dropLast(1)))
        // un titular sin posición
        assertNull(Formations.detect(7, ok.dropLast(1) + null))
    }

    @Test
    fun `problems esta vacia cuando la alineacion cumple la formacion`() {
        val formation = Formation(2, 3, 1)
        val ok = listOf(Position.POR, Position.DEF, Position.DEF, Position.MED, Position.MED, Position.MED, Position.DEL)
        assertTrue(Formations.problems(formation, ok).isEmpty())
    }

    @Test
    fun `problems avisa de titulares incompletos, sin posicion y cupos por linea`() {
        val formation = Formation(2, 3, 1)
        // 6 titulares: falta uno, uno sin posición, sobra un defensa y faltan dos medios
        val bad = listOf(Position.POR, Position.DEF, Position.DEF, Position.DEF, Position.MED, null)
        val problems = Formations.problems(formation, bad)
        assertTrue(problems.contains(TestText.key(R.string.positions_titulares_x_x, null, 6, 7)))
        assertTrue(problems.contains(TestText.key(R.plurals.positions_missing_position, 1)))
        // una línea por cada posición que no cuadra: DEF 3/2, MED 1/3, DEL 0/1 (el arquero sí cuadra)
        assertEquals(1, problems.count { it.endsWith(": 3/2") })
        assertEquals(1, problems.count { it.endsWith(": 1/3") })
        assertEquals(1, problems.count { it.endsWith(": 0/1") })
        assertFalse(problems.any { it.endsWith(": 1/1") })
    }

    // ---------- Jugadores en cancha ----------

    private fun starter(id: String, pos: String, side: String = "home") =
        LineupPlayer(teamSide = side, playerId = id, isStarter = true, fieldPosition = pos)

    private fun bench(id: String, pos: String? = null, side: String = "home") =
        LineupPlayer(teamSide = side, playerId = id, isStarter = false, fieldPosition = pos)

    private fun event(
        seq: Int, code: String, minute: Int, side: String = "home", playerId: String? = null,
        out: String? = null, inn: String? = null, status: String = "active", period: Int = 1,
    ) = TimelineEvent(
        eventId = "e$seq", seq = seq, code = code, typeName = code, groupKind = "play", period = period,
        minute = minute, teamSide = side, playerId = playerId, status = status,
        payload = buildJsonObject {
            out?.let { put("player_out_id", it) }
            inn?.let { put("player_in_id", it) }
        },
    )

    private val lineup = listOf(
        starter("arq", "POR"), starter("def", "DEF"), starter("del", "DEL"),
        bench("sup1"), bench("sup2", "MED"),
        starter("rival", "POR", side = "away"),
    )

    @Test
    fun `sin eventos estan en cancha solo los titulares de ese lado`() {
        val field = onFieldPlayers(lineup, emptyList(), "home")
        assertEquals(setOf("arq", "def", "del"), field.keys)
        assertEquals(Position.POR, field["arq"])
        assertEquals(setOf("rival"), onFieldPlayers(lineup, emptyList(), "away").keys)
    }

    @Test
    fun `en un cambio el que entra hereda la posicion del que sale`() {
        val field = onFieldPlayers(lineup, listOf(event(1, "substitution", 10, out = "def", inn = "sup1")), "home")
        assertEquals(setOf("arq", "del", "sup1"), field.keys)
        assertEquals(Position.DEF, field["sup1"])
    }

    @Test
    fun `una tarjeta roja saca al jugador de la cancha`() {
        val field = onFieldPlayers(lineup, listOf(event(1, "red_card", 20, playerId = "del")), "home")
        assertEquals(setOf("arq", "def"), field.keys)
    }

    @Test
    fun `los eventos anulados y los del otro equipo no cuentan`() {
        val events = listOf(
            event(1, "red_card", 5, playerId = "arq", status = "voided"),
            event(2, "red_card", 6, side = "away", playerId = "arq"),
            event(3, "substitution", 7, side = "away", out = "def", inn = "sup1"),
        )
        assertEquals(setOf("arq", "def", "del"), onFieldPlayers(lineup, events, "home").keys)
    }

    @Test
    fun `los eventos se aplican en orden de juego aunque lleguen desordenados`() {
        // Llega primero (seq 1) la roja del minuto 30 al suplente y después (seq 2) el cambio del
        // minuto 10 que lo hizo entrar: un evento registrado tarde. En orden de fútbol entra y luego
        // es expulsado, así que al final no está en cancha.
        val events = listOf(
            event(1, "red_card", 30, playerId = "sup1"),
            event(2, "substitution", 10, out = "def", inn = "sup1"),
        )
        assertEquals(setOf("arq", "del"), onFieldPlayers(lineup, events, "home").keys)
    }

    @Test
    fun `dos cambios seguidos conservan la posicion original`() {
        val events = listOf(
            event(1, "substitution", 10, out = "arq", inn = "sup1"),
            event(2, "substitution", 5, out = "sup1", inn = "sup2", period = 2),
        )
        val field = onFieldPlayers(lineup, events, "home")
        assertEquals(setOf("def", "del", "sup2"), field.keys)
        // sup2 venía como MED en la banca, pero entra por el arquero: queda de arquero
        assertEquals(Position.POR, field["sup2"])
    }
}
