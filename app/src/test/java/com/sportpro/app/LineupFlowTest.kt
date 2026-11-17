package com.sportpro.app

import com.sportpro.app.core.data.LineupFlow
import com.sportpro.app.core.data.LineupSideStatus
import com.sportpro.app.core.data.LineupStatus
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.NotificationTarget
import com.sportpro.app.core.data.lineupsPending
import com.sportpro.app.core.data.notificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Alineaciones de partidos acordados: plazo de 1 hora, lados visibles y avisos. */
class LineupFlowTest {

    private val now = Instant.parse("2026-11-20T15:00:00Z").toEpochMilli()
    private fun iso(minutesFromNow: Long) = Instant.ofEpochMilli(now + minutesFromNow * 60_000).toString()

    private fun match(status: String = "scheduled", flow: Boolean = true, confirmedAt: String? = null) = MatchRow(
        id = "m1", academyId = "a1", homeTeamId = "t1", awayTeamId = "t2", status = status,
        lineupFlow = flow, lineupsConfirmedAt = confirmedAt,
    )

    private fun status(deadlineInMinutes: Long, confirmedAt: String? = null, editable: Set<String> = setOf("home")) = LineupStatus(
        flow = true, deadline = iso(deadlineInMinutes), confirmedAt = confirmedAt,
        sides = listOf(LineupSideStatus("home", canEdit = "home" in editable), LineupSideStatus("away", canEdit = "away" in editable)),
    )

    // ---------- ¿Se puede editar? ----------

    @Test
    fun `antes de la hora limite se puede editar`() {
        assertTrue(LineupFlow.editable(match(), status(deadlineInMinutes = 30), now))
    }

    @Test
    fun `pasada la hora limite ya no`() {
        assertFalse(LineupFlow.editable(match(), status(deadlineInMinutes = -1), now))
    }

    @Test
    fun `confirmadas ya no se editan aunque falte para la hora`() {
        assertFalse(LineupFlow.editable(match(), status(deadlineInMinutes = 30, confirmedAt = iso(-5)), now))
    }

    @Test
    fun `un partido de siempre se edita mientras este programado`() {
        assertTrue(LineupFlow.editable(match(flow = false), null, now))
        assertFalse(LineupFlow.editable(match(flow = false, status = "live"), null, now))
    }

    @Test
    fun `sin hora limite conocida no se habilita la edicion`() {
        assertFalse(LineupFlow.editable(match(), LineupStatus(flow = true, deadline = null), now))
    }

    // ---------- Lados visibles en «Convocatoria y alineación» ----------

    @Test
    fun `en un partido acordado cada entrenador ve solo su lado`() {
        assertEquals(listOf("away"), LineupFlow.visibleSides(listOf("home", "away"), status(30, editable = setOf("away"))))
        assertEquals(listOf("home"), LineupFlow.visibleSides(listOf("home", "away"), status(30, editable = setOf("home"))))
    }

    @Test
    fun `en un partido de siempre se ven todos los lados`() {
        assertEquals(listOf("home", "away"), LineupFlow.visibleSides(listOf("home", "away"), null))
    }

    // ---------- Tarjeta «Alineación pendiente» ----------

    @Test
    fun `pendiente solo si es acordado programado y sin confirmar`() {
        assertTrue(match().lineupsPending)
        assertFalse(match(confirmedAt = iso(-5)).lineupsPending)
        assertFalse(match(flow = false).lineupsPending)
        assertFalse(match(status = "live").lineupsPending)
    }

    // ---------- Avisos ----------

    @Test
    fun `los avisos de alineacion abren el partido`() {
        val data: (String) -> String? = { key -> if (key == "match_id") "m1" else null }
        assertEquals(NotificationTarget.Match("m1"), notificationTarget("match_lineups", data))
        assertEquals(NotificationTarget.Match("m1"), notificationTarget("match_lineup_submitted", data))
        assertEquals(NotificationTarget.Match("m1"), notificationTarget("match_lineup_autofilled", data))
    }
}
