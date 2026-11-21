package com.sportpro.app

import com.sportpro.app.core.data.CoachFormationRow
import com.sportpro.app.core.data.LineupDraft
import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.PitchFormations
import com.sportpro.app.core.data.Placeable
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.SlotJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Formaciones dibujables, ubicación de jugadores en la cancha y el borrador del editor (migración 018). */
class PitchFormationsTest {

    @Before
    fun setUp() = TestText.install()

    // ---------- Plantillas ----------

    @Test
    fun `cada formacion predeterminada tiene tantas casillas como jugadores, 1 arquero y todo dentro de la cancha`() {
        for (format in 5..11) {
            val options = PitchFormations.forFormat(format)
            assertTrue("formato $format sin opciones", options.size >= 3)
            options.forEach { f ->
                assertEquals("${f.label} en fútbol $format", format, f.slots.size)
                assertEquals("${f.label}: arqueros", 1, f.count(Position.POR))
                assertTrue("${f.label}: casillas fuera de la cancha", f.slots.all { it.x in 0f..1f && it.y in 0f..1f })
                assertTrue("${f.label}: nombre inválido", PitchFormations.isValidLabel(f.label, format))
            }
        }
    }

    @Test
    fun `el 4-2-3-1 tiene 4 defensas, 5 medios en dos lineas y 1 delantero`() {
        val f = PitchFormations.build("4-2-3-1", 11)!!
        assertEquals(4, f.count(Position.DEF))
        assertEquals(5, f.count(Position.MED))
        assertEquals(1, f.count(Position.DEL))
        // Los medios están en dos alturas distintas
        assertEquals(2, f.slots.filter { it.pos == Position.MED }.map { it.y }.distinct().size)
        // El arquero es el más cercano a su arco y el delantero el más adelantado
        assertEquals(Position.POR, f.slots.minBy { it.y }.pos)
        assertEquals(Position.DEL, f.slots.maxBy { it.y }.pos)
    }

    @Test
    fun `el nombre de la formacion debe sumar los jugadores de campo del formato`() {
        assertTrue(PitchFormations.isValidLabel("4-4-2", 11))
        assertFalse(PitchFormations.isValidLabel("4-4-2", 7))
        assertFalse(PitchFormations.isValidLabel("4-4-2-", 11))
        assertFalse(PitchFormations.isValidLabel("0-3-3", 7))
        assertFalse(PitchFormations.isValidLabel("10", 11))
        assertNull(PitchFormations.build("4-4-2", 7))
    }

    @Test
    fun `sin nombre guardado la formacion se deduce de las posiciones, y si no, la predeterminada`() {
        val positions = listOf(Position.POR, Position.DEF, Position.DEF, Position.DEF, Position.MED, Position.MED, Position.DEL)
        assertEquals("3-2-1", PitchFormations.labelFromPositions(positions))
        assertEquals("3-2-1", PitchFormations.resolve(7, null, positions).label)
        assertEquals("2-2-2", PitchFormations.resolve(7, "2-2-2", positions).label)
        // Un nombre que no corresponde al formato se ignora
        assertEquals("3-2-1", PitchFormations.resolve(7, "4-4-2", positions).label)
        assertEquals(PitchFormations.default(7).label, PitchFormations.resolve(7, null, listOf(Position.POR)).label)
    }

    @Test
    fun `una formacion propia valida se convierte y una con dos arqueros se descarta`() {
        val ok = CoachFormationRow("f1", "Mía", 5, "2-1-1", listOf(
            SlotJson("POR", 0.5f, 0.1f), SlotJson("DEF", 0.3f, 0.3f), SlotJson("DEF", 0.7f, 0.3f),
            SlotJson("MED", 0.5f, 0.6f), SlotJson("DEL", 0.5f, 0.9f),
        ))
        val f = ok.toFormation()
        assertNotNull(f)
        assertEquals("Mía", f!!.displayName)
        assertTrue(f.isCustom)
        assertNull(ok.copy(slots = ok.slots.dropLast(1) + SlotJson("POR", 0.5f, 0.5f)).toFormation())
    }

    // ---------- Ubicar jugadores ----------

    @Test
    fun `place respeta el lugar guardado y ubica a los demas por posicion y dorsal`() {
        val f = PitchFormations.build("2-3-1", 7)!!
        val players = listOf(
            Placeable("arq", Position.POR, jersey = 1),
            // Lateral adelantado: su casilla se mueve a donde lo dejó el DT
            Placeable("lat", Position.DEF, x = 0.85f, y = 0.55f, jersey = 4),
            Placeable("def", Position.DEF, jersey = 2),
            Placeable("m1", Position.MED, jersey = 6),
            Placeable("del", Position.DEL, jersey = 9),
        )
        val (slots, left) = PitchFormations.place(f, players)
        assertTrue(left.isEmpty())
        val lat = slots.first { it.playerId == "lat" }
        assertEquals(0.85f, lat.slot.x)
        assertEquals(0.55f, lat.slot.y)
        assertEquals(Position.DEF, lat.slot.pos)
        assertEquals(Position.POR, slots.first { it.playerId == "arq" }.slot.pos)
        // Quedan 2 casillas de medio libres
        assertEquals(2, slots.count { it.playerId == null })
    }

    @Test
    fun `place manda a cualquier casilla libre a quien no tiene la suya y devuelve los que no caben`() {
        val f = PitchFormations.build("2-1-1", 5)!!
        val players = (1..6).map { Placeable("d$it", Position.DEF, jersey = it) }
        val (slots, left) = PitchFormations.place(f, players)
        assertEquals(5, slots.count { it.playerId != null })
        assertEquals(listOf("d6"), left)
        // El arco se llena al final, con el último que cupo
        assertEquals("d5", slots.first { it.slot.pos == Position.POR }.playerId)
    }

    // ---------- Borrador del editor ----------

    private val f7 = PitchFormations.build("2-3-1", 7)!!
    private val confirmed = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i")

    @Test
    fun `asignar desde la banca manda al que estaba a la banca, y entre casillas intercambia`() {
        var d = LineupDraft.empty(f7, confirmed)
        d = d.assign(0, "a").assign(1, "b")
        assertEquals(listOf("a", "b"), d.starters)
        assertFalse("a" in d.bench)
        // «c» entra a la casilla de «a»: «a» vuelve primero a la banca
        d = d.assign(0, "c")
        assertEquals("c", d.slots[0].playerId)
        assertEquals("a", d.bench.first())
        // «b» (casilla 1) va a la casilla 0: se intercambia con «c»
        d = d.assign(0, "b")
        assertEquals("b", d.slots[0].playerId)
        assertEquals("c", d.slots[1].playerId)
    }

    @Test
    fun `vaciar una casilla manda al jugador a la banca`() {
        val d = LineupDraft.empty(f7, confirmed).assign(3, "e").clear(3)
        assertNull(d.slots[3].playerId)
        assertEquals("e", d.bench.first())
    }

    @Test
    fun `mover una casilla la marca como personalizada y restablecer la devuelve`() {
        var d = LineupDraft.empty(f7, confirmed)
        assertFalse(d.isCustomized)
        d = d.move(1, 0.9f, 0.6f)
        assertTrue(d.isCustomized)
        // No se sale de la cancha
        d = d.move(2, 5f, -1f)
        assertEquals(PitchFormations.MAX_X, d.slots[2].slot.x)
        assertEquals(PitchFormations.MIN_Y, d.slots[2].slot.y)
        assertFalse(d.resetPositions().isCustomized)
    }

    @Test
    fun `cambiar de formacion conserva a los titulares y los que no caben van a la banca`() {
        var d = LineupDraft.empty(f7, confirmed)
        d.slots.indices.forEach { i -> d = d.assign(i, confirmed[i]) }
        assertTrue(d.isComplete)
        val changed = d.withFormation(PitchFormations.build("3-2-1", 7)!!)
        assertEquals("3-2-1", changed.formation.label)
        assertEquals(7, changed.starters.size)
        assertEquals(d.starters.toSet(), changed.starters.toSet())
        // El arquero sigue en el arco
        assertEquals(d.slots.first { it.slot.pos == Position.POR }.playerId, changed.slots.first { it.slot.pos == Position.POR }.playerId)
    }

    @Test
    fun `automatico prefiere la posicion habitual y el dorsal, y deja el arco al arquero`() {
        val usual = mapOf("a" to Position.DEL, "b" to Position.POR, "c" to Position.DEF, "d" to Position.DEF,
            "e" to Position.MED, "f" to Position.MED, "g" to Position.MED, "h" to Position.MED, "i" to null)
        val jersey = confirmed.mapIndexed { i, id -> id to i + 1 }.toMap()
        val d = LineupDraft.empty(f7, confirmed).autoFill(usual, jersey)
        assertTrue(d.isComplete)
        assertEquals("b", d.slots.first { it.slot.pos == Position.POR }.playerId)
        assertEquals("a", d.slots.first { it.slot.pos == Position.DEL }.playerId)
        // De los 4 medios habituales entran los 3 de menor dorsal
        assertEquals(setOf("e", "f", "g"), d.slots.filter { it.slot.pos == Position.MED }.map { it.playerId }.toSet())
        assertEquals(listOf("h", "i"), d.bench.sorted())
    }

    @Test
    fun `no juega y suplentes se intercambian, y solo los titulares llevan lugar al guardar`() {
        var d = LineupDraft.empty(f7, confirmed).assign(0, "a")
        d = d.toggleOut("i")
        assertTrue("i" in d.out && "i" !in d.bench)
        val entries = d.toEntries(emptyMap(), emptyMap())
        assertEquals(1, entries.count { it.starter })
        assertTrue(entries.filter { it.starter }.all { it.x != null && it.y != null })
        assertTrue(entries.filter { !it.starter }.all { it.x == null && it.y == null })
        // Quien no juega no se envía
        assertTrue(entries.none { it.playerId == "i" })
        assertEquals("i", d.toggleOut("i").bench.last())
    }

    @Test
    fun `al cargar lo guardado se descarta a quien ya no esta confirmado`() {
        val saved = listOf(
            LineupPlayer("home", playerId = "a", isStarter = true, fieldPosition = "POR", slotX = 0.5f, slotY = 0.1f),
            LineupPlayer("home", playerId = "zz", isStarter = true, fieldPosition = "DEF"),
            LineupPlayer("home", playerId = "b", isStarter = false),
        )
        val d = LineupDraft.fromSaved(f7, saved, listOf("a", "b", "c"))
        assertEquals(listOf("a"), d.starters)
        assertEquals(listOf("b"), d.bench)
        assertEquals(listOf("c"), d.out)
    }

    @Test
    fun `el nombre corto en la cancha respeta la privacidad del menor`() {
        assertEquals("J. Pérez", PitchFormations.pitchName("Juan Pérez García"))
        assertEquals("Juan P.", PitchFormations.pitchName("Juan P."))
        assertEquals("Mateo", PitchFormations.pitchName(" Mateo "))
        assertEquals("", PitchFormations.pitchName(null))
    }

    // ---------- Orientación en pantalla ----------

    @Test
    fun `el local se dibuja arriba y girado, el visitante abajo`() {
        // Arquero local: arriba al centro; arquero visitante: abajo al centro
        val home = PitchFormations.toScreen(0.5f, 0.1f, top = true)
        assertEquals(0.5f, home.first, 0.0001f)
        assertEquals(0.05f, home.second, 0.0001f)
        val away = PitchFormations.toScreen(0.5f, 0.1f, top = false)
        assertEquals(0.5f, away.first, 0.0001f)
        assertEquals(0.95f, away.second, 0.0001f)
        // La izquierda del local queda a la derecha de la pantalla
        assertEquals(0.8f, PitchFormations.toScreen(0.2f, 0.5f, top = true).first, 0.0001f)
        // Ida y vuelta en una mitad dibujada sola
        val (sx, sy) = PitchFormations.toHalfScreen(0.3f, 0.7f, top = true)
        val (x, y) = PitchFormations.fromHalfScreen(sx, sy, top = true)
        assertEquals(0.3f, x, 0.0001f)
        assertEquals(0.7f, y, 0.0001f)
    }
}
