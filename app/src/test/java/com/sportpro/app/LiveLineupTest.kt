package com.sportpro.app

import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.LiveLineup
import com.sportpro.app.core.data.TimelineEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Íconos de cada jugador, MVP y cancha en vivo (Bloque B). Todo se calcula de los eventos. */
class LiveLineupTest {

    @Before
    fun setUp() = TestText.install()

    private var seq = 0
    private fun ev(
        code: String, minute: Int, side: String = "home", player: String? = null,
        payload: JsonObject = JsonObject(emptyMap()), status: String = "active", period: Int = 1,
    ) = TimelineEvent(
        eventId = "e${++seq}", seq = seq, code = code, typeName = code, groupKind = "main", period = period,
        minute = minute, teamSide = side, playerId = player, status = status, payload = payload,
    )

    private fun goal(minute: Int, player: String, side: String = "home", assist: String? = null, own: Boolean = false) =
        ev("goal", minute, side, player, buildJsonObject {
            put("own_goal", own)
            assist?.let { put("assist_player_id", it) }
        })

    private fun penalty(minute: Int, player: String, result: String, side: String = "home") =
        ev("penalty", minute, side, player, buildJsonObject { put("result", result) })

    private fun sub(minute: Int, out: String, inn: String, side: String = "home") =
        ev("substitution", minute, side, out, buildJsonObject {
            put("player_out_id", out)
            put("player_in_id", inn)
        })

    // ---------- Estadísticas del partido ----------

    @Test
    fun `goles, asistencias, tarjetas y cambios se cuentan por jugador`() {
        val events = listOf(
            goal(10, "a", assist = "b"),
            goal(20, "a"),
            penalty(30, "a", "converted"),
            penalty(31, "c", "missed"),
            goal(40, "c", own = true),
            ev("yellow_card", 41, player = "b"),
            ev("yellow_card", 60, player = "b"),
            ev("red_card", 70, player = "d"),
            sub(75, out = "c", inn = "e"),
        )
        val s = LiveLineup.stats(events)
        assertEquals(3, s.getValue("a").goals)
        assertEquals(1, s.getValue("b").assists)
        assertEquals(0, s["c"]?.goals ?: 0)
        assertEquals(1, s.getValue("c").ownGoals)
        assertTrue(s.getValue("b").sentOff)  // doble amarilla
        assertTrue(s.getValue("d").sentOff)  // roja directa
        assertTrue(s.getValue("c").subOut)
        assertTrue(s.getValue("e").subIn)
    }

    @Test
    fun `un evento anulado no cuenta`() {
        val s = LiveLineup.stats(listOf(goal(10, "a", assist = "b").copy(status = "voided")))
        assertNull(s["a"])
        assertNull(s["b"])
    }

    // ---------- MVP ----------

    @Test
    fun `el MVP es el que mas goles metio, aunque sea del equipo que pierde`() {
        val events = listOf(
            goal(5, "h1"), goal(6, "h2"), goal(7, "h3"),          // el local gana 3-2
            goal(10, "v1", side = "away"), goal(20, "v1", side = "away"),
        )
        assertEquals("v1", LiveLineup.mvp(events))
    }

    @Test
    fun `con empate en goles o sin goles no hay MVP, y los goles en contra no cuentan`() {
        assertNull(LiveLineup.mvp(emptyList()))
        assertNull(LiveLineup.mvp(listOf(goal(5, "a"), goal(6, "b"))))
        // «a» y «b» meten 1 gol cada uno; los 2 goles en contra de «b» no le suman: empate → nadie
        assertNull(LiveLineup.mvp(listOf(goal(5, "a"), goal(6, "b"), goal(7, "b", own = true), goal(8, "b", own = true))))
        // Con un penal convertido, «a» pasa adelante
        assertEquals("a", LiveLineup.mvp(listOf(goal(5, "a"), goal(6, "b"), penalty(9, "a", "converted"))))
    }

    @Test
    fun `el MVP cambia en vivo si se anula el gol que desempataba`() {
        val g = goal(30, "a")
        val events = listOf(goal(5, "a"), goal(6, "b"), g)
        assertEquals("a", LiveLineup.mvp(events))
        assertNull(LiveLineup.mvp(events.map { if (it.eventId == g.eventId) it.copy(status = "voided") else it }))
    }

    // ---------- Cancha en vivo ----------

    private val lineup = listOf(
        LineupPlayer("home", playerId = "arq", isStarter = true, fieldPosition = "POR", jerseyNumber = 1),
        LineupPlayer("home", playerId = "d1", isStarter = true, fieldPosition = "DEF", jerseyNumber = 2, slotX = 0.9f, slotY = 0.55f),
        LineupPlayer("home", playerId = "d2", isStarter = true, fieldPosition = "DEF", jerseyNumber = 3),
        LineupPlayer("home", playerId = "m1", isStarter = true, fieldPosition = "MED", jerseyNumber = 4),
        LineupPlayer("home", playerId = "del", isStarter = true, fieldPosition = "DEL", jerseyNumber = 9),
        LineupPlayer("home", playerId = "sup", isStarter = false, jerseyNumber = 12),
        LineupPlayer("home", playerId = "sup2", isStarter = false, jerseyNumber = 13),
        LineupPlayer("away", playerId = "rival", isStarter = true, fieldPosition = "POR"),
    )

    @Test
    fun `sin cambios estan los titulares en su formacion y en su lugar guardado`() {
        val side = LiveLineup.side(5, "2-1-1", lineup, emptyList(), "home")
        assertEquals("2-1-1", side.formation.label)
        assertEquals(setOf("arq", "d1", "d2", "m1", "del"), side.slots.mapNotNull { it.playerId }.toSet())
        val d1 = side.slots.first { it.playerId == "d1" }
        assertEquals(0.9f, d1.slot.x)
        assertEquals(0.55f, d1.slot.y)
        assertEquals(listOf("sup", "sup2"), side.bench)
    }

    @Test
    fun `el que entra ocupa la casilla del que sale y el que sale pasa afuera`() {
        val before = LiveLineup.side(5, "2-1-1", lineup, emptyList(), "home")
        val after = LiveLineup.side(5, "2-1-1", lineup, listOf(sub(50, out = "d1", inn = "sup")), "home")
        val i = before.slots.indexOfFirst { it.playerId == "d1" }
        assertEquals("sup", after.slots[i].playerId)
        // Conserva el lugar que el DT le había dado a la casilla
        assertEquals(0.9f, after.slots[i].slot.x)
        assertTrue("d1" in after.bench)
        assertFalse("sup" in after.bench)
    }

    @Test
    fun `un expulsado sigue en su casilla y los cambios del otro equipo no afectan`() {
        val events = listOf(ev("red_card", 30, player = "m1"), sub(40, out = "rival", inn = "x", side = "away"))
        val side = LiveLineup.side(5, "2-1-1", lineup, events, "home")
        assertTrue(side.slots.any { it.playerId == "m1" })
        assertTrue(LiveLineup.stats(events).getValue("m1").sentOff)
    }
}
