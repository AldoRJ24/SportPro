package com.sportpro.app

import com.sportpro.app.core.data.AcademyRef
import com.sportpro.app.core.data.RivalTeam
import com.sportpro.app.core.data.VoiceBrain
import com.sportpro.app.core.data.VoiceDraft
import com.sportpro.app.core.data.VoiceField
import com.sportpro.app.core.data.VoiceParse
import com.sportpro.app.core.data.VoicePath
import com.sportpro.app.core.data.VoiceStep
import com.sportpro.app.core.data.VoiceTeam
import com.sportpro.app.core.data.VoiceWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Conversación del modo manos libres, sin red ni IA (lo que la IA devolvería se simula). */
class VoiceBrainTest {

    @Before fun texts() = TestText.install()

    private val now = LocalDateTime.of(2026, 11, 28, 10, 0)
    private val tomorrow = LocalDate.of(2026, 11, 29)

    private val aguilas15 = VoiceTeam("h15", "Las Águilas", "Sub-15", null, "Fernando Pérez", 7, 25)
    private val aguilas12 = VoiceTeam("h12", "Las Águilas", "Sub-12", null, "Fernando Pérez", 7, 20)
    private val halcones = VoiceTeam("hx", "Halcones", "Sub-15", null, "Marta Ríos", 11, 30)

    private val academies = listOf(
        AcademyRef("a1", "Academia Virgen del Rosario", "Lima", "verified"),
        AcademyRef("a2", "Club Los Halcones", "Surco", "verified"),
    )
    private val lagartos15 = RivalTeam("t15", "Lagartos", "Sub-15", 15, "Rodolfo Gómez", eligible = true)
    private val lagartos13 = RivalTeam("t13", "Lagartos", "Sub-13", 13, "Rodolfo Gómez", eligible = false, reason = "Fuera de tu división")

    /** Un administrador que no es DT de ningún equipo (sus partidos son propuestas). */
    private fun world(rivals: Boolean = true) = VoiceWorld(
        myTeams = listOf(aguilas15, aguilas12, halcones),
        staffTeamIds = emptySet(),
        academies = academies,
        rivals = if (rivals) mapOf("a1" to listOf(lagartos15, lagartos13)) else emptyMap(),
    )

    // Lo que la IA devolvería para la frase del ejemplo
    private val example = VoiceParse(
        intent = "create_match", date = "2026-11-29", time = "11:00",
        homeTeam = "Las Águilas", homeCoach = "Fernandito", homeCategory = 15,
        awayTeam = "los Lagartos", awayCoach = "Rodolfo", awayAcademy = "Virgen de Rosario", awayCategory = 15,
    )

    @Test fun ejemploCompletoSoloPreguntaLoQueFalta() {
        val d = VoiceBrain.merge(VoiceDraft(), example, null, "")
        val (resolved, step) = VoiceBrain.next(d, world(), now)
        // Reconoció todo lo dicho…
        assertEquals("h15", resolved.homeTeamId)
        assertEquals("a1", resolved.academyId)
        assertEquals("t15", resolved.awayTeamId)
        assertEquals(tomorrow, resolved.date)
        assertEquals(LocalTime.of(11, 0), resolved.time)
        // …y pregunta lo primero que falta: de cuántos jugadores es
        assertTrue(step is VoiceStep.Ask && step.field == VoiceField.FORMAT)
    }

    @Test fun conTodoLosDatosPideConfirmarYEsPropuesta() {
        var d = VoiceBrain.merge(VoiceDraft(), example, null, "")
        d = VoiceBrain.merge(d, VoiceParse(intent = "answer", formatPlayers = 7, halfMinutes = 25, venue = "-"), null, "")
        val (resolved, step) = VoiceBrain.next(d, world(), now)
        assertTrue(step is VoiceStep.Confirm)
        assertEquals("", resolved.venue)
        // El admin no dirige Las Águilas: deben aceptar los dos entrenadores
        assertEquals(VoicePath.PROPOSAL, VoiceBrain.route(resolved, world()))
    }

    @Test fun sinRivalesCargadosPideTraerlos() {
        val d = VoiceBrain.merge(VoiceDraft(), example, null, "")
        val (_, step) = VoiceBrain.next(d, world(rivals = false), now)
        assertEquals(VoiceStep.LoadRivals(listOf("a1")), step)
    }

    @Test fun diminutivoEncuentraAlEntrenador() {
        assertEquals("fernand", VoiceBrain.stem("fernandito"))
        assertTrue(
            VoiceBrain.teamScore(com.sportpro.app.core.data.TeamQuery(coach = "Fernandito"), "Las Águilas", "Sub-15", null, "Fernando Pérez") > 0,
        )
    }

    @Test fun sinDivisionHayDosAguilasYPregunta() {
        val d = VoiceBrain.merge(VoiceDraft(), VoiceParse(intent = "create_match", homeTeam = "Águilas"), null, "")
        val (_, step) = VoiceBrain.next(d, world(), now)
        assertTrue(step is VoiceStep.Ask && step.field == VoiceField.HOME)
        assertEquals(2, (step as VoiceStep.Ask).options.size)
        // Responde «la de sub 15» (texto) → elige esa opción sin necesitar a la IA
        val picked = VoiceBrain.merge(d, VoiceParse(intent = "answer"), step, "la de sub 15")
        assertEquals("h15", picked.homeTeamId)
    }

    @Test fun rivalFueraDeDivisionSeExplica() {
        val d = VoiceBrain.merge(VoiceDraft(homeTeamId = "h15"),
            VoiceParse(intent = "answer", awayTeam = "Lagartos", awayAcademy = "Virgen del Rosario", awayCategory = 13), null, "")
        val (_, step) = VoiceBrain.next(d, world(), now)
        assertTrue(step is VoiceStep.Ask && step.field == VoiceField.AWAY)
        // Ofrece solo los que sí se pueden elegir
        assertEquals(listOf("a1|t15"), (step as VoiceStep.Ask).options.map { it.value })
    }

    @Test fun fechaPasadaSeVuelveAPreguntar() {
        var d = VoiceBrain.merge(VoiceDraft(), example, null, "")
        d = d.copy(date = LocalDate.of(2026, 11, 20))
        val (resolved, step) = VoiceBrain.next(d, world(), now)
        assertNull(resolved.date)
        assertTrue(step is VoiceStep.Ask && step.field == VoiceField.DATE)
    }

    @Test fun propuestaNecesitaUnaHoraDeAnticipacion() {
        var d = VoiceBrain.merge(VoiceDraft(), example, null, "")
        d = d.copy(date = now.toLocalDate(), time = LocalTime.of(10, 30))
        val (_, step) = VoiceBrain.next(d, world(), now)
        assertTrue(step is VoiceStep.Ask && step.field == VoiceField.TIME)
    }

    @Test fun siNoYCancelarRapidos() {
        assertEquals(true, VoiceBrain.quickYesNo("Sí, dale"))
        assertEquals(true, VoiceBrain.quickYesNo("confírmalo"))
        assertEquals(false, VoiceBrain.quickYesNo("no"))
        assertNull(VoiceBrain.quickYesNo("a las cuatro"))
        assertTrue(VoiceBrain.isCancel("Cancela"))
        assertFalse(VoiceBrain.isCancel("contra los Lagartos"))
    }
}
