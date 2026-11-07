package com.sportpro.app.core.data

/** Posiciones fijas: POR, DEF, MED, DEL. El servidor solo acepta estos códigos (check constraint). */
enum class Position(val code: String, val label: String, val short: String) {
    POR("POR", "Arquero", "ARQ"),
    DEF("DEF", "Defensa", "DEF"),
    MED("MED", "Mediocampista", "MED"),
    DEL("DEL", "Delantero", "DEL");

    companion object {
        fun of(code: String?): Position? = entries.firstOrNull { it.code == code }
    }
}

/** Formación: siempre 1 arquero; defensas + medios + delanteros = jugadores − 1 (ej. 1-2-3-1 en fútbol 7). */
data class Formation(val def: Int, val med: Int, val del: Int) {
    val name: String get() = "1-$def-$med-$del"
    val total: Int get() = 1 + def + med + del
    fun required(p: Position): Int = when (p) {
        Position.POR -> 1
        Position.DEF -> def
        Position.MED -> med
        Position.DEL -> del
    }
}

// Formaciones disponibles por formato (5 a 11 jugadores). La primera de cada lista es la sugerida.
object Formations {
    private val table: Map<Int, List<Formation>> = mapOf(
        5 to listOf(Formation(2, 1, 1), Formation(1, 2, 1), Formation(1, 1, 2)),
        6 to listOf(Formation(2, 2, 1), Formation(2, 1, 2), Formation(3, 1, 1)),
        7 to listOf(Formation(2, 3, 1), Formation(3, 2, 1), Formation(2, 2, 2)),
        8 to listOf(Formation(3, 3, 1), Formation(3, 2, 2), Formation(2, 3, 2)),
        9 to listOf(Formation(3, 3, 2), Formation(3, 4, 1), Formation(4, 3, 1)),
        10 to listOf(Formation(4, 3, 2), Formation(3, 4, 2), Formation(4, 4, 1)),
        11 to listOf(Formation(4, 4, 2), Formation(4, 3, 3), Formation(3, 5, 2)),
    )

    fun forFormat(players: Int): List<Formation> = table[players] ?: table.getValue(7)

    fun default(players: Int): Formation = forFormat(players).first()

    /** Deduce la formación a partir de las posiciones de los titulares (null si no hay exactamente 1 arquero). */
    fun detect(players: Int, positions: List<Position?>): Formation? {
        if (positions.size != players || positions.any { it == null }) return null
        val def = positions.count { it == Position.DEF }
        val med = positions.count { it == Position.MED }
        val del = positions.count { it == Position.DEL }
        val por = positions.count { it == Position.POR }
        if (por != 1) return null
        return Formation(def, med, del)
    }

    /** Problemas para mostrar al DT (titulares incompletos, sin posición, cupos por línea). Vacía = válida. */
    fun problems(formation: Formation, positions: List<Position?>): List<String> {
        val out = mutableListOf<String>()
        if (positions.size != formation.total) out += "Titulares ${positions.size}/${formation.total}"
        val missing = positions.count { it == null }
        if (missing > 0) out += "$missing titular(es) sin posición"
        Position.entries.forEach { p ->
            val have = positions.count { it == p }
            val need = formation.required(p)
            if (have != need) out += "${p.label}: $have/$need"
        }
        return out
    }
}

 /**
  * JUGADORES EN CANCHA de un lado; se calculan, no se guardan:
  * 1. Se parte de los titulares de la alineación, con su posición.
  * 2. Se recorren los eventos vigentes (no anulados) en orden de fútbol:
  *    - cambio: sale player_out_id y entra player_in_id, que hereda la posición del que sale;
  *    - tarjeta roja: el expulsado sale de la cancha.
  * Se usa para el arquero automático en atajadas y penales y para mostrar «En cancha» y «Banca».
  * Clave del mapa = id del jugador del club o id del jugador externo del rival.
  */
fun onFieldPlayers(lineup: List<LineupPlayer>, events: List<TimelineEvent>, side: String): Map<String, Position?> {
    val field = LinkedHashMap<String, Position?>()
    lineup.filter { it.teamSide == side && it.isStarter && it.key.isNotEmpty() }
        .forEach { field[it.key] = Position.of(it.fieldPosition) }
    val ordered = events.filter { !it.isVoided && it.teamSide == side }
        .sortedWith(compareBy({ it.period }, { it.minute }, { it.minuteExtra }, { it.seq }))
    for (e in ordered) {
        when (e.code) {
            "substitution" -> {
                val out = e.payloadString("player_out_id")
                val inn = e.payloadString("player_in_id")
                val pos = out?.let { field.remove(it) } ?: Position.of(lineup.firstOrNull { it.key == inn }?.fieldPosition)
                if (!inn.isNullOrEmpty()) field[inn] = pos
            }
            "red_card" -> (e.playerId ?: e.externalPlayerId)?.let { field.remove(it) }
        }
    }
    return field
}
