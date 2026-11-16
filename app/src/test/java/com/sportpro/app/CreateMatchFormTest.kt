package com.sportpro.app

import com.sportpro.app.core.data.ExternalDraft
import com.sportpro.app.core.data.Position
import com.sportpro.app.ui.creatematch.CreateMatchForm
import com.sportpro.app.ui.creatematch.withRosterSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Validaciones del formulario «Crear partido» (las mismas reglas se vuelven a validar en el servidor). */
class CreateMatchFormTest {

    @Before
    fun setUp() = TestText.install()

    /** Plantel completo y válido de fútbol 7: 1 arquero, 2 defensas, 3 medios y 1 delantero. */
    private fun fullRoster(): List<ExternalDraft> {
        val positions = listOf(Position.POR, Position.DEF, Position.DEF, Position.MED, Position.MED, Position.MED, Position.DEL)
        return positions.mapIndexed { i, p -> ExternalDraft(jersey = "${i + 1}", name = "Rival ${i + 1}", position = p, starter = true) }
    }

    /** Formulario válido con rival externo; cada test cambia solo lo que quiere probar. */
    private fun validForm() = CreateMatchForm(
        homeTeamId = "t1", externalRival = true, externalName = "Rival FC", externalRoster = fullRoster(),
        formatPlayers = 7, halfMinutes = 25, dateDigits = "01122026", timeDigits = "1530",
    )

    // ---------- Formulario ----------

    @Test
    fun `un formulario completo no tiene errores`() {
        assertNull(validForm().validationError())
        // Con rival registrado no hace falta nombre ni plantel
        assertNull(validForm().copy(externalRival = false, awayTeamId = "t2", externalName = "", externalRoster = emptyList()).validationError())
    }

    @Test
    fun `se pide primero el equipo propio`() {
        assertEquals(TestText.key(R.string.create_match_elige_tu_equipo), validForm().copy(homeTeamId = null).validationError())
    }

    @Test
    fun `el rival externo necesita nombre y el registrado necesita equipo`() {
        assertEquals(
            TestText.key(R.string.create_match_escribe_el_nombre_del_rival),
            validForm().copy(externalName = " x ").validationError(),
        )
        assertEquals(
            TestText.key(R.string.create_match_elige_el_equipo_rival),
            validForm().copy(externalRival = false, awayTeamId = null).validationError(),
        )
    }

    @Test
    fun `formato y duracion tienen limites`() {
        assertEquals(TestText.key(R.string.create_match_el_formato_debe_ser_de), validForm().copy(formatPlayers = 4).validationError())
        assertEquals(TestText.key(R.string.create_match_el_formato_debe_ser_de), validForm().copy(formatPlayers = 12).validationError())
        assertEquals(TestText.key(R.string.create_match_cada_tiempo_debe_durar_entre), validForm().copy(halfMinutes = 4).validationError())
        assertEquals(TestText.key(R.string.create_match_cada_tiempo_debe_durar_entre), validForm().copy(halfMinutes = 61).validationError())
    }

    @Test
    fun `fecha y hora deben existir de verdad`() {
        assertEquals(TestText.key(R.string.create_match_fecha_invalida_escribe_8_digitos), validForm().copy(dateDigits = "31022026").validationError())
        assertEquals(TestText.key(R.string.create_match_fecha_invalida_escribe_8_digitos), validForm().copy(dateDigits = "0112").validationError())
        assertEquals(TestText.key(R.string.create_match_hora_invalida_escribe_4_digitos), validForm().copy(timeDigits = "2560").validationError())
    }

    @Test
    fun `los errores salen en el orden del formulario`() {
        // Falta el equipo Y la fecha es inválida: se avisa primero del equipo
        val form = validForm().copy(homeTeamId = null, dateDigits = "99999999")
        assertEquals(TestText.key(R.string.create_match_elige_tu_equipo), form.validationError())
    }

    // ---------- Plantel del rival externo ----------

    @Test
    fun `un plantel completo no tiene problemas`() {
        assertTrue(validForm().rosterProblems().isEmpty())
    }

    @Test
    fun `los titulares deben ser tantos como el formato`() {
        val problems = validForm().copy(externalRoster = fullRoster().dropLast(1)).rosterProblems()
        assertTrue(problems.contains(TestText.key(R.string.create_match_titulares_x_x, null, 6, 7)))
    }

    @Test
    fun `debe haber exactamente un arquero titular`() {
        val noKeeper = fullRoster().map { if (it.position == Position.POR) it.copy(position = Position.DEF) else it }
        assertTrue(validForm().copy(externalRoster = noKeeper).rosterProblems()
            .contains(TestText.key(R.string.create_match_debe_haber_exactamente_1_arquero, null, 0)))
        val twoKeepers = fullRoster().mapIndexed { i, r -> if (i == 1) r.copy(position = Position.POR) else r }
        assertTrue(validForm().copy(externalRoster = twoKeepers).rosterProblems()
            .contains(TestText.key(R.string.create_match_debe_haber_exactamente_1_arquero, null, 2)))
    }

    @Test
    fun `cada fila necesita nombre, dorsal y posicion`() {
        val roster = fullRoster().toMutableList()
        roster[2] = roster[2].copy(name = "")
        roster[3] = roster[3].copy(jersey = "x")
        roster[4] = roster[4].copy(position = null)
        val problems = validForm().copy(externalRoster = roster).rosterProblems()
        assertTrue(problems.contains(TestText.key(R.string.create_match_hay_filas_sin_nombre)))
        assertTrue(problems.contains(TestText.key(R.string.create_match_todos_necesitan_dorsal_0_99)))
        assertTrue(problems.contains(TestText.key(R.string.create_match_asigna_la_posicion_de_cada)))
    }

    @Test
    fun `los dorsales repetidos se informan con su numero`() {
        val roster = fullRoster().mapIndexed { i, r -> if (i == 6) r.copy(jersey = "1") else r }
        val problems = validForm().copy(externalRoster = roster).rosterProblems()
        assertTrue(problems.contains(TestText.key(R.string.create_match_dorsales_repetidos_x, null, "1")))
    }

    @Test
    fun `las filas totalmente vacias no cuentan como jugadores`() {
        // Una fila de suplente sin nombre ni dorsal se ignora
        val roster = fullRoster() + ExternalDraft(starter = false)
        assertTrue(validForm().copy(externalRoster = roster).rosterProblems().isEmpty())
        assertEquals(7, validForm().copy(externalRoster = roster).filledRoster.size)
    }

    @Test
    fun `un plantel con problemas bloquea la creacion del partido`() {
        val error = validForm().copy(externalRoster = fullRoster().dropLast(1)).validationError()
        assertTrue(error != null && error.startsWith(TestText.key(R.string.create_match_plantel_del_rival)))
    }

    // ---------- Filas de titulares según el formato ----------

    @Test
    fun `un formulario nuevo trae una fila por titular y la primera es el arquero`() {
        val form = CreateMatchForm(formatPlayers = 7).withRosterSize()
        assertEquals(7, form.externalRoster.size)
        assertTrue(form.externalRoster.all { it.starter })
        assertEquals(Position.POR, form.externalRoster.first().position)
        assertTrue(form.externalRoster.drop(1).all { it.position == null })
    }

    @Test
    fun `al subir el formato se agregan filas sin tocar las escritas`() {
        val form = validForm().copy(formatPlayers = 9).withRosterSize()
        assertEquals(9, form.externalRoster.size)
        assertEquals(fullRoster(), form.externalRoster.take(7))
        assertTrue(form.externalRoster.drop(7).all { it.name.isBlank() && it.position == null })
    }

    @Test
    fun `al bajar el formato solo se quitan filas vacias, nunca lo que escribio el DT`() {
        // 7 filas vacías → bajar a 5 deja 5
        val empty = CreateMatchForm(formatPlayers = 7).withRosterSize()
        assertEquals(5, empty.copy(formatPlayers = 5).withRosterSize().externalRoster.size)
        // 7 filas escritas → bajar a 5 las conserva todas (el DT decide cuál quitar)
        assertEquals(7, validForm().copy(formatPlayers = 5).withRosterSize().externalRoster.size)
    }
}
