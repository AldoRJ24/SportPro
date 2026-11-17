package com.sportpro.app

import com.sportpro.app.core.data.AcademyRef
import com.sportpro.app.core.data.ChallengeRow
import com.sportpro.app.core.data.Challenges
import com.sportpro.app.core.data.NotificationTarget
import com.sportpro.app.core.data.notificationTarget
import com.sportpro.app.ui.creatematch.CreateMatchForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desafíos entre equipos: regla de división, buscador de academias, orden y destino de los avisos. */
class ChallengesTest {

    // ---------- Regla de división (-1 a +1) ----------

    @Test
    fun `un sub 14 juega contra sub 13 sub 14 y sub 15`() {
        assertTrue(Challenges.divisionOk(14, 13))
        assertTrue(Challenges.divisionOk(14, 14))
        assertTrue(Challenges.divisionOk(14, 15))
    }

    @Test
    fun `dos divisiones de diferencia ya no se permite`() {
        assertFalse(Challenges.divisionOk(14, 12))
        assertFalse(Challenges.divisionOk(14, 16))
        assertFalse(Challenges.divisionOk(15, 17))
    }

    @Test
    fun `un equipo sin edad maxima solo juega contra otro sin edad maxima`() {
        assertTrue(Challenges.divisionOk(null, null))
        assertFalse(Challenges.divisionOk(14, null))
        assertFalse(Challenges.divisionOk(null, 14))
    }

    // ---------- Buscador de academias ----------

    private val academies = listOf(
        AcademyRef(id = "a1", name = "Academia León", city = "Lima"),
        AcademyRef(id = "a2", name = "Cantera Norte", city = "Trujillo"),
        AcademyRef(id = "a3", name = "Escuela Sur", city = null),
    )

    @Test
    fun `sin texto se muestran todas las academias`() {
        assertEquals(3, Challenges.filterAcademies(academies, "").size)
        assertEquals(3, Challenges.filterAcademies(academies, "   ").size)
    }

    @Test
    fun `el buscador encuentra por nombre sin importar tildes ni mayusculas`() {
        assertEquals(listOf("a1"), Challenges.filterAcademies(academies, "LEON").map { it.id })
    }

    @Test
    fun `el buscador tambien encuentra por ciudad`() {
        assertEquals(listOf("a2"), Challenges.filterAcademies(academies, "trujillo").map { it.id })
        assertEquals(emptyList<String>(), Challenges.filterAcademies(academies, "cusco").map { it.id })
    }

    // ---------- Qué puedo hacer con cada desafío y en qué orden salen ----------

    private fun row(id: String, incoming: Boolean, outgoing: Boolean, status: String) = ChallengeRow(
        id = id, incoming = incoming, outgoing = outgoing, status = status,
        homeTeamId = "h", homeTeamName = "Halcones", awayTeamId = "a", awayTeamName = "Tigres",
    )

    @Test
    fun `solo se responde un desafio recibido y pendiente`() {
        assertTrue(row("1", incoming = true, outgoing = false, status = "pending").canRespond)
        assertFalse(row("2", incoming = false, outgoing = true, status = "pending").canRespond)
        assertFalse(row("3", incoming = true, outgoing = false, status = "expired").canRespond)
    }

    @Test
    fun `solo se cancela un desafio enviado y pendiente`() {
        assertTrue(row("1", incoming = false, outgoing = true, status = "pending").canCancel)
        assertFalse(row("2", incoming = true, outgoing = false, status = "pending").canCancel)
        assertFalse(row("3", incoming = false, outgoing = true, status = "accepted").canCancel)
    }

    @Test
    fun `primero los que esperan mi respuesta luego los enviados y al final el historial`() {
        val sorted = Challenges.sorted(
            listOf(
                row("historial", incoming = true, outgoing = false, status = "rejected"),
                row("enviado", incoming = false, outgoing = true, status = "pending"),
                row("responder", incoming = true, outgoing = false, status = "pending"),
            ),
        )
        assertEquals(listOf("responder", "enviado", "historial"), sorted.map { it.id })
    }

    // ---------- Crear partido: ¿directo o desafío? ----------

    @Test
    fun `contra el equipo de otro entrenador se envia un desafio`() {
        val form = CreateMatchForm(homeTeamId = "mio", externalRival = false, awayTeamId = "ajeno")
        assertTrue(form.isChallenge(setOf("mio")))
    }

    @Test
    fun `contra un equipo que tambien gestiono o un rival externo el partido se crea directo`() {
        assertFalse(CreateMatchForm(homeTeamId = "mio", externalRival = false, awayTeamId = "otro-mio").isChallenge(setOf("mio", "otro-mio")))
        assertFalse(CreateMatchForm(homeTeamId = "mio", externalRival = true).isChallenge(setOf("mio")))
        assertFalse(CreateMatchForm(homeTeamId = "mio", externalRival = false, awayTeamId = null).isChallenge(setOf("mio")))
    }

    // ---------- A dónde lleva la notificación ----------

    @Test
    fun `un desafio nuevo rechazado o cancelado abre la lista de desafios`() {
        val noData: (String) -> String? = { null }
        assertEquals(NotificationTarget.Challenges, notificationTarget("match_challenge", noData))
        assertEquals(NotificationTarget.Challenges, notificationTarget("match_challenge_rejected", noData))
        assertEquals(NotificationTarget.Challenges, notificationTarget("match_challenge_cancelled", noData))
    }

    @Test
    fun `un desafio aceptado abre el partido creado`() {
        val target = notificationTarget("match_challenge_accepted") { key -> if (key == "match_id") "m1" else null }
        assertEquals(NotificationTarget.Match("m1"), target)
    }
}
