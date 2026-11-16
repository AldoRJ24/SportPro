package com.sportpro.app

import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.TimelineEvent
import com.sportpro.app.core.data.clock
import com.sportpro.app.core.data.parseInstant
import com.sportpro.app.core.data.statusLabel
import com.sportpro.app.core.data.timeAgo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** Reloj del partido (derivado de datos del servidor), minuto con añadido y «hace cuánto». */
class TimeTest {

    @Before
    fun setUp() = TestText.install()

    /** Un instante fijo para que el test dé siempre lo mismo. */
    private val now = Instant.parse("2026-11-16T15:00:00Z").toEpochMilli()

    private fun isoAgo(seconds: Long): String =
        Instant.ofEpochMilli(now - seconds * 1000).atOffset(ZoneOffset.UTC).toString()

    private fun match(
        period: Int = 1, elapsedBefore: Int = 0, running: Boolean = false, startedAgo: Long? = null,
        half: Int = 25, status: String = "live",
    ) = MatchRow(
        id = "m1", academyId = "a1", homeTeamId = "t1", halfMinutes = half, status = status, period = period,
        elapsedBeforeSeconds = elapsedBefore, clockRunning = running,
        periodStartedAt = startedAgo?.let { isoAgo(it) },
    )

    // ---------- Reloj ----------

    @Test
    fun `el primer minuto es el 1`() {
        val c = match(running = true, startedAgo = 0).clock(now)
        assertEquals(1, c.minute)
        assertEquals(0, c.extra)
        assertEquals("1'", c.label)
    }

    @Test
    fun `con el reloj corriendo el minuto sale de la hora de inicio del periodo`() {
        // 5 min 30 s jugados → minuto 6
        assertEquals(6, match(running = true, startedAgo = 330).clock(now).minute)
    }

    @Test
    fun `con el reloj detenido solo cuenta lo acumulado, pase el tiempo que pase`() {
        val paused = match(elapsedBefore = 600, running = false, startedAgo = 9999)
        assertEquals(11, paused.clock(now).minute)
        assertEquals(11, paused.clock(now + 3_600_000).minute)
    }

    @Test
    fun `lo acumulado antes de una pausa se suma a lo que corre ahora`() {
        // 10 min antes de la pausa + 2 min desde que se reanudó → minuto 13
        assertEquals(13, match(elapsedBefore = 600, running = true, startedAgo = 120).clock(now).minute)
    }

    @Test
    fun `el segundo tiempo empieza en el minuto siguiente al primero`() {
        assertEquals(26, match(period = 2, running = true, startedAgo = 0, half = 25).clock(now).minute)
        assertEquals(31, match(period = 2, running = true, startedAgo = 0, half = 30).clock(now).minute)
    }

    @Test
    fun `pasado el tiempo reglamentario se muestra como anadido`() {
        // 27 min jugados de un tiempo de 25 → 25+3'
        val c = match(running = true, startedAgo = 27 * 60).clock(now)
        assertEquals(25, c.minute)
        assertEquals(3, c.extra)
        assertEquals("25+3'", c.label)
        // En el segundo tiempo el añadido se cuenta sobre el minuto 50
        assertEquals("50+1'", match(period = 2, running = true, startedAgo = 25 * 60).clock(now).label)
    }

    @Test
    fun `el anadido nunca pasa de 30`() {
        assertEquals(30, match(running = true, startedAgo = 5 * 3600).clock(now).extra)
    }

    @Test
    fun `sin hora de inicio el reloj no avanza ni falla`() {
        assertEquals(1, match(running = true, startedAgo = null).clock(now).minute)
    }

    @Test
    fun `el minuto de un evento lleva el anadido`() {
        fun event(minute: Int, extra: Int) = TimelineEvent(
            eventId = "e", seq = 1, code = "goal", typeName = "Gol", groupKind = "play", period = 1,
            minute = minute, minuteExtra = extra, teamSide = "home",
        )
        assertEquals("12'", event(12, 0).minuteLabel)
        assertEquals("25+2'", event(25, 2).minuteLabel)
    }

    // ---------- Estado del partido ----------

    @Test
    fun `la etiqueta de estado depende del estado y en vivo lleva el minuto`() {
        assertEquals(TestText.key(R.string.time_entretiempo), match(status = "halftime").statusLabel(now))
        assertEquals(TestText.key(R.string.time_finalizado), match(status = "finished").statusLabel(now))
        assertEquals(TestText.key(R.string.time_cancelado), match(status = "cancelled").statusLabel(now))
        assertEquals(
            TestText.key(R.string.time_en_vivo_x, null, "6'"),
            match(status = "live", running = true, startedAgo = 330).statusLabel(now),
        )
    }

    // ---------- «hace cuánto» ----------

    @Test
    fun `timeAgo elige la unidad segun el tiempo transcurrido`() {
        assertEquals(TestText.key(R.string.time_now), timeAgo(isoAgo(30), now))
        assertEquals(TestText.key(R.string.time_hace_x_min, null, 5L), timeAgo(isoAgo(5 * 60 + 10), now))
        assertEquals(TestText.key(R.string.time_hace_x_h, null, 2L), timeAgo(isoAgo(2 * 3600 + 5), now))
        assertEquals(TestText.key(R.string.time_hace_x_d, null, 3L), timeAgo(isoAgo(3 * 86_400L + 5), now))
    }

    @Test
    fun `timeAgo cambia de unidad justo en el limite`() {
        assertEquals(TestText.key(R.string.time_now), timeAgo(isoAgo(59), now))
        assertEquals(TestText.key(R.string.time_hace_x_min, null, 1L), timeAgo(isoAgo(60), now))
        assertEquals(TestText.key(R.string.time_hace_x_min, null, 59L), timeAgo(isoAgo(3599), now))
        assertEquals(TestText.key(R.string.time_hace_x_h, null, 1L), timeAgo(isoAgo(3600), now))
        assertEquals(TestText.key(R.string.time_hace_x_h, null, 23L), timeAgo(isoAgo(86_399), now))
        assertEquals(TestText.key(R.string.time_hace_x_d, null, 1L), timeAgo(isoAgo(86_400), now))
    }

    @Test
    fun `pasada una semana timeAgo muestra la fecha`() {
        val old = timeAgo(isoAgo(8 * 86_400L), now)
        assertTrue(old.isNotBlank())
        assertNotEquals(TestText.key(R.string.time_hace_x_d, null, 8L), old)
    }

    @Test
    fun `timeAgo no falla con fechas vacias, invalidas o del futuro`() {
        assertEquals("", timeAgo(null, now))
        assertEquals("", timeAgo("no es fecha", now))
        // Un reloj del celular atrasado no debe dar tiempos negativos
        assertEquals(TestText.key(R.string.time_now), timeAgo(isoAgo(-120), now))
    }

    @Test
    fun `parseInstant acepta ISO con zona horaria y rechaza lo demas`() {
        assertEquals(Instant.parse("2026-11-16T15:00:00Z"), parseInstant("2026-11-16T10:00:00-05:00"))
        assertNull(parseInstant(null))
        assertNull(parseInstant("16/11/2026"))
    }
}
