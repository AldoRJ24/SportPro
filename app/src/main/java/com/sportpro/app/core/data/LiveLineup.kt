package com.sportpro.app.core.data

/*
 * ALINEACIÓN EN VIVO (Bloque B). Todo se CALCULA a partir de los eventos vigentes (no anulados);
 * nada de esto se guarda, igual que el marcador y el minuto. Lógica pura para probarla con JUnit.
 * Clave de cada jugador = id del jugador del club o id del jugador externo del rival.
 */

/** Lo que hizo un jugador en el partido, para dibujar los íconos sobre su ficha. */
data class PlayerMatchStats(
    val goals: Int = 0,
    val ownGoals: Int = 0,
    val assists: Int = 0,
    val yellows: Int = 0,
    val reds: Int = 0,
    /** Entró desde la banca. */
    val subIn: Boolean = false,
    /** Salió reemplazado. */
    val subOut: Boolean = false,
) {
    /** Expulsado: roja directa o doble amarilla. */
    val sentOff: Boolean get() = reds > 0 || yellows >= 2
}

/** Un lado tal como se ve AHORA: formación, quién ocupa cada casilla y quiénes están fuera. */
data class LiveSide(
    val formation: PitchFormation,
    val slots: List<SlotAssignment>,
    /** Suplentes que no entraron y jugadores que ya salieron (reemplazados). */
    val bench: List<String>,
)

object LiveLineup {

    // Orden de fútbol: periodo, minuto, añadido y número de registro (los tardíos caen en su lugar)
    private fun ordered(events: List<TimelineEvent>): List<TimelineEvent> =
        events.filter { !it.isVoided && !it.isControl }
            .sortedWith(compareBy({ it.period }, { it.minute }, { it.minuteExtra }, { it.seq }))

    private fun isOwnGoal(e: TimelineEvent): Boolean =
        (e.payload["own_goal"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"

    /** ¿Este evento es un gol a favor del jugador que lo registra? (gol que no es en contra o penal convertido). */
    private fun isGoalFor(e: TimelineEvent): Boolean = when (e.code) {
        "goal" -> !isOwnGoal(e)
        "penalty" -> e.payloadString("result") == "converted"
        else -> false
    }

    /**
     * Estadísticas de cada jugador en ESTE partido:
     * - gol (no en contra) o penal convertido → gol del que lo registra;
     * - gol en contra → se cuenta aparte (no suma para el MVP);
     * - assist_player_id del gol → asistencia;
     * - tarjetas amarillas y rojas;
     * - cambio → «entró» para player_in_id y «salió» para player_out_id.
     */
    fun stats(events: List<TimelineEvent>): Map<String, PlayerMatchStats> {
        val out = HashMap<String, PlayerMatchStats>()
        fun upd(key: String?, f: (PlayerMatchStats) -> PlayerMatchStats) {
            if (key.isNullOrEmpty()) return
            out[key] = f(out[key] ?: PlayerMatchStats())
        }
        for (e in ordered(events)) {
            when {
                isGoalFor(e) -> {
                    upd(e.actorKey) { it.copy(goals = it.goals + 1) }
                    if (e.code == "goal") upd(e.payloadString("assist_player_id")) { it.copy(assists = it.assists + 1) }
                }
                e.code == "goal" -> upd(e.actorKey) { it.copy(ownGoals = it.ownGoals + 1) }
                e.code == "yellow_card" -> upd(e.actorKey) { it.copy(yellows = it.yellows + 1) }
                e.code == "red_card" -> upd(e.actorKey) { it.copy(reds = it.reds + 1) }
                e.code == "substitution" -> {
                    upd(e.payloadString("player_in_id")) { it.copy(subIn = true) }
                    upd(e.payloadString("player_out_id")) { it.copy(subOut = true) }
                }
            }
        }
        return out
    }

    /**
     * MVP del partido = quien MÁS goles metió, de cualquiera de los dos equipos (puede ser del
     * que pierde). Si nadie anotó, o si dos o más empatan en el máximo, no hay MVP (decisión
     * del usuario: con empate, nadie lleva la estrella). Los goles en contra no cuentan.
     */
    fun mvp(events: List<TimelineEvent>): String? {
        val goals = stats(events).filterValues { it.goals > 0 }.mapValues { it.value.goals }
        val max = goals.values.maxOrNull() ?: return null
        val leaders = goals.filterValues { it == max }.keys
        return leaders.singleOrNull()
    }

    /**
     * Un lado como se ve ahora:
     * 1. Los titulares se ubican en su formación (lugares guardados por el DT o, si no hay,
     *    los de la formación; ver [PitchFormations.place]).
     * 2. Cada cambio, en orden de juego, pone al que entra en la casilla del que sale.
     * 3. Un expulsado se queda en su casilla (la ficha se atenúa con su tarjeta).
     * 4. Fuera de la cancha quedan los suplentes que no entraron y los que ya salieron.
     */
    fun side(format: Int, label: String?, lineup: List<LineupPlayer>, events: List<TimelineEvent>, side: String): LiveSide {
        val mine = lineup.filter { it.teamSide == side && it.key.isNotEmpty() }
        val starters = mine.filter { it.isStarter }
        val formation = PitchFormations.resolve(format, label, starters.map { Position.of(it.fieldPosition) })
        val (placed, left) = PitchFormations.place(
            formation,
            starters.map { Placeable(it.key, Position.of(it.fieldPosition), it.slotX, it.slotY, it.jerseyNumber) },
        )
        val slots = placed.toMutableList()
        for (e in ordered(events)) {
            if (e.teamSide != side || e.code != "substitution") continue
            val outKey = e.payloadString("player_out_id") ?: continue
            val inKey = e.payloadString("player_in_id") ?: continue
            val i = slots.indexOfFirst { it.playerId == outKey }
            if (i >= 0) slots[i] = slots[i].copy(playerId = inKey)
        }
        val onPitch = slots.mapNotNull { it.playerId }.toSet()
        val bench = (left + mine.filter { !it.isStarter }.map { it.key } + starters.map { it.key })
            .distinct()
            .filter { it !in onPitch }
            .sortedBy { key -> mine.firstOrNull { it.key == key }?.jerseyNumber ?: 99 }
        return LiveSide(formation, slots, bench)
    }
}
