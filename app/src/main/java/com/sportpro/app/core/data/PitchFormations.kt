package com.sportpro.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

/*
 * ALINEACIÓN EN LA CANCHA (migración 018). Lógica pura, sin Compose ni red, para poder probarla.
 *
 * Sistema de coordenadas (el mismo que guarda el servidor en slot_x / slot_y):
 * cada equipo se describe en SU MITAD de la cancha, como si atacara hacia arriba.
 *   x: 0 = banda izquierda, 1 = banda derecha.
 *   y: 0 = línea de su arco, 1 = línea de medio campo.
 * Al dibujar, el local va en la mitad de arriba (girado) y el visitante en la de abajo.
 */

/** Casilla de una formación: la posición que cubre y su lugar en la mitad propia. */
data class PitchSlot(val pos: Position, val x: Float, val y: Float)

/**
 * Formación dibujable. [label] son las líneas de jugadores de campo sin el arquero
 * («4-4-2», «4-2-3-1», «2-3-1»). Si es una formación propia del entrenador, trae su id y nombre.
 */
data class PitchFormation(
    val label: String,
    val format: Int,
    val slots: List<PitchSlot>,
    val customId: String? = null,
    val customName: String? = null,
) {
    val isCustom: Boolean get() = customId != null
    val displayName: String get() = customName ?: label
    fun count(p: Position): Int = slots.count { it.pos == p }
}

/** Una casilla con el jugador que la ocupa (o vacía). */
data class SlotAssignment(val slot: PitchSlot, val playerId: String? = null)

/** Jugador que hay que ubicar en la cancha: posición en el partido, lugar guardado (si tiene) y dorsal. */
data class Placeable(val id: String, val pos: Position?, val x: Float? = null, val y: Float? = null, val jersey: Int? = null)

/** Fila de la alineación que se envía al servidor (lineup_save). Los suplentes van sin lugar. */
data class LineupEntry(
    val playerId: String,
    val jersey: Int?,
    val starter: Boolean,
    val position: String?,
    val x: Float? = null,
    val y: Float? = null,
)

/** Casilla tal como se guarda en una formación propia (coach_formations.slots). */
@Serializable
data class SlotJson(val pos: String, val x: Float, val y: Float)

/** Formación propia del entrenador, tal como llega del servidor. */
@Serializable
data class CoachFormationRow(
    val id: String,
    val name: String,
    @SerialName("format_players") val formatPlayers: Int,
    @SerialName("base_formation") val baseFormation: String,
    val slots: List<SlotJson> = emptyList(),
) {
    /** Se convierte en formación dibujable; null si sus casillas no son válidas (por ejemplo, otro formato). */
    fun toFormation(): PitchFormation? {
        val parsed = slots.map { s -> PitchSlot(Position.of(s.pos) ?: return null, s.x, s.y) }
        if (parsed.size != formatPlayers || parsed.count { it.pos == Position.POR } != 1) return null
        return PitchFormation(baseFormation, formatPlayers, parsed, customId = id, customName = name)
    }
}

/** Foto de un jugador del plantel (get_roster_photos). */
@Serializable
data class RosterPhotoRow(
    @SerialName("player_id") val playerId: String,
    @SerialName("photo_path") val photoPath: String,
)

/** Nombre de la formación que eligió cada lado (match_lineup_formations). */
@Serializable
data class SideFormationRow(
    @SerialName("team_side") val teamSide: String,
    val formation: String,
)

object PitchFormations {
    // Límites para arrastrar: que la ficha no se salga del dibujo de la cancha
    const val MIN_X = 0.06f
    const val MAX_X = 0.94f
    const val MIN_Y = 0.04f
    const val MAX_Y = 0.96f

    // Altura del arquero y de la primera y última línea dentro de la mitad propia
    private const val GK_Y = 0.07f
    private const val FIRST_LINE_Y = 0.30f
    private const val LAST_LINE_Y = 0.86f

    // Formaciones predeterminadas por formato (la primera es la sugerida). Solo jugadores de campo.
    private val labels: Map<Int, List<String>> = mapOf(
        5 to listOf("2-1-1", "1-2-1", "2-2", "1-1-2"),
        6 to listOf("2-2-1", "2-1-2", "3-1-1", "1-3-1"),
        7 to listOf("2-3-1", "3-2-1", "2-2-2", "3-1-2"),
        8 to listOf("3-3-1", "3-2-2", "2-3-2", "3-1-3"),
        9 to listOf("3-3-2", "3-4-1", "4-3-1", "3-2-3"),
        10 to listOf("4-3-2", "3-4-2", "4-4-1", "3-3-3"),
        11 to listOf("4-4-2", "4-3-3", "4-2-3-1", "3-5-2", "4-1-4-1", "5-3-2"),
    )

    private val labelRegex = Regex("^[1-9](-[1-9]){1,4}$")

    /** Formaciones predeterminadas para un formato (5 a 11). Un formato desconocido usa fútbol 7. */
    fun forFormat(format: Int): List<PitchFormation> {
        val f = if (format in labels) format else 7
        return labels.getValue(f).mapNotNull { build(it, f) }
    }

    fun default(format: Int): PitchFormation = forFormat(format).first()

    /** ¿Es un nombre válido para ese formato? Las líneas deben sumar los jugadores de campo. */
    fun isValidLabel(label: String, format: Int): Boolean =
        labelRegex.matches(label) && label.split('-').sumOf { it.toInt() } == format - 1

    /**
     * Arma las casillas de una formación a partir de su nombre:
     * - el arquero abajo al centro;
     * - la primera línea son defensas, la última delanteros y las del medio, mediocampistas;
     * - las líneas se reparten de forma pareja a lo alto, y los jugadores de cada línea a lo ancho;
     * - en líneas de 4 o más, los de los costados van un poco más adelante (laterales y volantes abiertos).
     * Devuelve null si el nombre no corresponde al formato.
     */
    fun build(label: String, format: Int): PitchFormation? {
        if (!isValidLabel(label, format)) return null
        val lines = label.split('-').map { it.toInt() }
        val slots = mutableListOf(PitchSlot(Position.POR, 0.5f, GK_Y))
        lines.forEachIndexed { i, count ->
            val pos = when (i) {
                0 -> Position.DEF
                lines.lastIndex -> Position.DEL
                else -> Position.MED
            }
            val y = FIRST_LINE_Y + (LAST_LINE_Y - FIRST_LINE_Y) * i / (lines.size - 1)
            repeat(count) { j ->
                val x = 0.08f + 0.84f * (j + 0.5f) / count
                val wing = count >= 4 && (j == 0 || j == count - 1)
                slots += PitchSlot(pos, x, if (wing) y + 0.04f else y)
            }
        }
        return PitchFormation(label, format, slots)
    }

    /** Nombre deducido de cuántos defensas, medios y delanteros hay (sin líneas vacías); null si no alcanza. */
    fun labelFromPositions(positions: List<Position?>): String? {
        val lines = listOf(Position.DEF, Position.MED, Position.DEL).map { p -> positions.count { it == p } }.filter { it > 0 }
        return if (lines.size >= 2 && lines.all { it <= 9 }) lines.joinToString("-") else null
    }

    /**
     * Formación para mostrar una alineación ya guardada: la que eligió el DT si es válida;
     * si no (partidos viejos, autocompletado del servidor), la que se deduce de las posiciones;
     * y si tampoco, la predeterminada del formato.
     */
    fun resolve(format: Int, label: String?, positions: List<Position?>): PitchFormation =
        label?.let { build(it, format) }
            ?: labelFromPositions(positions)?.let { build(it, format) }
            ?: default(format)

    /**
     * Ubica jugadores en las casillas de una formación (se usa al cargar una alineación guardada
     * y al cambiar de formación):
     * 1. Quien tiene lugar guardado ocupa la casilla libre de su misma posición más cercana a ese
     *    lugar, y la casilla se mueve a donde él estaba.
     * 2. Los demás ocupan, por dorsal, las casillas libres de su posición (de izquierda a derecha).
     * 3. Si aún quedan, ocupan cualquier casilla libre (y pasan a cubrir esa posición).
     * Devuelve las casillas (en el mismo orden que la formación) y los ids de quienes no cupieron.
     */
    fun place(formation: PitchFormation, players: List<Placeable>): Pair<List<SlotAssignment>, List<String>> {
        val result = formation.slots.map { SlotAssignment(it) }.toMutableList()
        val pending = players.sortedWith(compareBy({ it.jersey ?: 99 }, { it.id })).toMutableList()

        // Paso 1: los que tienen lugar guardado, a la casilla más cercana de su posición
        pending.filter { it.x != null && it.y != null }.forEach { p ->
            val best = result.indices
                .filter { result[it].playerId == null && result[it].slot.pos == p.pos }
                .minByOrNull { i -> abs(result[i].slot.x - p.x!!) + abs(result[i].slot.y - p.y!!) }
            if (best != null) {
                result[best] = SlotAssignment(result[best].slot.copy(x = p.x!!, y = p.y!!), p.id)
                pending.remove(p)
            }
        }
        // Paso 2: por posición, en orden de dorsal
        pending.toList().forEach { p ->
            val free = result.indexOfFirst { it.playerId == null && it.slot.pos == p.pos }
            if (free >= 0) {
                result[free] = result[free].copy(playerId = p.id)
                pending.remove(p)
            }
        }
        // Paso 3: cualquier casilla libre (nunca el arco a alguien que no es arquero si hay otra libre)
        pending.toList().forEach { p ->
            val free = result.indices.filter { result[it].playerId == null }
                .sortedBy { if (result[it].slot.pos == Position.POR) 1 else 0 }
                .firstOrNull()
            if (free != null) {
                result[free] = result[free].copy(playerId = p.id)
                pending.remove(p)
            }
        }
        return result to pending.map { it.id }
    }

    /**
     * Pasa de coordenadas de la mitad propia a coordenadas de la CANCHA COMPLETA en pantalla
     * (0..1 de ancho y 0..1 de alto). El local va arriba con su arco arriba, por eso se gira:
     * su «izquierda» queda a la derecha de la pantalla. El visitante va abajo, sin girar.
     */
    fun toScreen(x: Float, y: Float, top: Boolean): Pair<Float, Float> =
        if (top) (1f - x) to (y / 2f) else x to (1f - y / 2f)

    /** Inversa de [toScreen] dentro de UNA mitad dibujada sola (0..1 de alto en esa mitad). */
    fun fromHalfScreen(sx: Float, sy: Float, top: Boolean): Pair<Float, Float> =
        if (top) (1f - sx) to sy else sx to (1f - sy)

    /** De coordenadas propias a coordenadas de una mitad dibujada sola (0..1 de alto en esa mitad). */
    fun toHalfScreen(x: Float, y: Float, top: Boolean): Pair<Float, Float> =
        if (top) (1f - x) to y else x to (1f - y)

    /**
     * Nombre corto para mostrar debajo de la ficha en la cancha:
     * - «Juan Pérez García» → «J. Pérez»;
     * - un menor que llega como «Juan P.» (privacidad) se deja igual: no se puede acortar más
     *   sin que quede irreconocible, y nunca se muestra más de lo que mandó el servidor.
     */
    fun pitchName(full: String?): String {
        val parts = full.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> ""
            parts.size == 1 -> parts[0]
            parts.last().endsWith(".") -> "${parts[0]} ${parts.last()}"
            else -> "${parts[0].first()}. ${parts[1]}"
        }
    }
}

/**
 * BORRADOR DEL EDITOR DE ALINEACIÓN de un lado. Invariante: [slots] tiene el mismo orden y las
 * mismas posiciones que [formation].slots; solo cambian los lugares (si el DT arrastra) y quién
 * ocupa cada casilla. Los confirmados que no están en la cancha son suplentes ([bench]) o no
 * juegan ([out]).
 */
data class LineupDraft(
    val formation: PitchFormation,
    val slots: List<SlotAssignment>,
    val bench: List<String> = emptyList(),
    val out: List<String> = emptyList(),
) {
    val starters: List<String> get() = slots.mapNotNull { it.playerId }
    val emptySlots: Int get() = slots.count { it.playerId == null }
    val isComplete: Boolean get() = emptySlots == 0

    /** ¿El DT movió alguna casilla respecto de la formación elegida? (Para ofrecer guardarla como propia.) */
    val isCustomized: Boolean
        get() = slots.zip(formation.slots).any { (a, b) -> abs(a.slot.x - b.x) > 0.01f || abs(a.slot.y - b.y) > 0.01f }

    fun slotOf(playerId: String): Int = slots.indexOfFirst { it.playerId == playerId }

    /**
     * Cambiar de formación sin perder a los jugadores: cada titular busca una casilla de su
     * posición en la nueva formación (en su lugar predeterminado); los que no caben pasan
     * primeros a la banca.
     */
    fun withFormation(f: PitchFormation): LineupDraft {
        // Se numeran de izquierda a derecha (en lugar del dorsal) para conservar el orden visual al reubicar
        val ordered = slots.filter { it.playerId != null }.sortedBy { it.slot.x }
            .mapIndexed { i, s -> Placeable(s.playerId!!, s.slot.pos, jersey = i) }
        val (placed, left) = PitchFormations.place(f, ordered)
        return copy(formation = f, slots = placed, bench = left + bench)
    }

    /**
     * Poner a un jugador en una casilla:
     * - si ya estaba en otra casilla, los dos se intercambian;
     * - si venía de la banca (o de «no juega»), el que ocupaba la casilla pasa a la banca.
     */
    fun assign(index: Int, playerId: String): LineupDraft {
        if (index !in slots.indices) return this
        val previous = slots[index].playerId
        if (previous == playerId) return this
        val from = slotOf(playerId)
        val newSlots = slots.toMutableList()
        newSlots[index] = newSlots[index].copy(playerId = playerId)
        return if (from >= 0) {
            newSlots[from] = newSlots[from].copy(playerId = previous)
            copy(slots = newSlots)
        } else {
            val newBench = (listOfNotNull(previous) + bench).filter { it != playerId }
            copy(slots = newSlots, bench = newBench, out = out - playerId)
        }
    }

    /** Vaciar una casilla: su jugador pasa a la banca. */
    fun clear(index: Int): LineupDraft {
        val previous = slots.getOrNull(index)?.playerId ?: return this
        val newSlots = slots.toMutableList().also { it[index] = it[index].copy(playerId = null) }
        return copy(slots = newSlots, bench = listOf(previous) + bench)
    }

    /** Mover una casilla (arrastre). El lugar se limita para que no salga de la cancha. */
    fun move(index: Int, x: Float, y: Float): LineupDraft {
        if (index !in slots.indices) return this
        val nx = x.coerceIn(PitchFormations.MIN_X, PitchFormations.MAX_X)
        val ny = y.coerceIn(PitchFormations.MIN_Y, PitchFormations.MAX_Y)
        val newSlots = slots.toMutableList().also { it[index] = it[index].copy(slot = it[index].slot.copy(x = nx, y = ny)) }
        return copy(slots = newSlots)
    }

    /** Volver cada casilla a su lugar de la formación elegida (sin cambiar quién la ocupa). */
    fun resetPositions(): LineupDraft =
        copy(slots = slots.mapIndexed { i, s -> s.copy(slot = formation.slots[i]) })

    /** Un suplente pasa a «no juega» y viceversa. */
    fun toggleOut(playerId: String): LineupDraft = when (playerId) {
        in bench -> copy(bench = bench - playerId, out = out + playerId)
        in out -> copy(out = out - playerId, bench = bench + playerId)
        else -> this
    }

    /**
     * «Automático»: llena las casillas vacías con suplentes. Primero, por dorsal, quienes juegan
     * habitualmente en esa posición; después, cualquiera (nunca al arco si no hay arquero habitual:
     * esa casilla queda para el final y se llena solo si sobran jugadores).
     */
    fun autoFill(usual: Map<String, Position?>, jersey: Map<String, Int?>): LineupDraft {
        var draft = this
        val byJersey = compareBy<String>({ jersey[it] ?: 99 }, { it })
        // Pasada 1: misma posición habitual
        slots.indices.forEach { i ->
            if (draft.slots[i].playerId == null) {
                val pick = draft.bench.sortedWith(byJersey).firstOrNull { usual[it] == draft.slots[i].slot.pos }
                if (pick != null) draft = draft.assign(i, pick)
            }
        }
        // Pasada 2: el resto de casillas, dejando el arco al final
        slots.indices.sortedBy { if (slots[it].slot.pos == Position.POR) 1 else 0 }.forEach { i ->
            if (draft.slots[i].playerId == null) {
                val pick = draft.bench.sortedWith(byJersey).firstOrNull()
                if (pick != null) draft = draft.assign(i, pick)
            }
        }
        return draft
    }

    /** Filas para el servidor: titulares con su posición y lugar; suplentes con su posición habitual. */
    fun toEntries(usual: Map<String, Position?>, jersey: Map<String, Int?>): List<LineupEntry> =
        slots.filter { it.playerId != null }.map { s ->
            LineupEntry(s.playerId!!, jersey[s.playerId], true, s.slot.pos.code, s.slot.x, s.slot.y)
        } + bench.map { id -> LineupEntry(id, jersey[id], false, usual[id]?.code) }

    /** Las casillas actuales, para guardarlas como formación propia. */
    fun currentSlots(): List<PitchSlot> = slots.map { it.slot }

    companion object {
        /** Borrador vacío: casillas de la formación sin nadie y todos los confirmados en la banca. */
        fun empty(formation: PitchFormation, confirmed: List<String>): LineupDraft =
            LineupDraft(formation, formation.slots.map { SlotAssignment(it) }, bench = confirmed)

        /**
         * Borrador a partir de lo guardado en el servidor:
         * - solo cuentan los jugadores que siguen confirmados (el servidor no acepta a otros);
         * - los titulares se ubican con [PitchFormations.place]; los que no caben van a la banca;
         * - los confirmados que no estaban en la alineación quedan como «no juega».
         */
        fun fromSaved(formation: PitchFormation, saved: List<LineupPlayer>, confirmed: List<String>): LineupDraft {
            val ok = confirmed.toSet()
            val mine = saved.filter { it.playerId != null && it.playerId in ok }
            if (mine.isEmpty()) return empty(formation, confirmed)
            val starters = mine.filter { it.isStarter }.map {
                Placeable(it.playerId!!, Position.of(it.fieldPosition), it.slotX, it.slotY, it.jerseyNumber)
            }
            val (placed, left) = PitchFormations.place(formation, starters)
            val bench = left + mine.filter { !it.isStarter }.sortedBy { it.jerseyNumber ?: 99 }.map { it.playerId!! }
            val out = confirmed.filter { id -> placed.none { it.playerId == id } && id !in bench }
            return LineupDraft(formation, placed, bench, out)
        }
    }
}
