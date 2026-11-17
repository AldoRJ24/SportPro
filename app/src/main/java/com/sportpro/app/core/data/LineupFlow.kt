package com.sportpro.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------- Alineaciones de los partidos acordados por desafío (migración 017) ----------------
// Cada entrenador arma y ENVÍA su alineación; puede editarla hasta 1 hora antes del partido.
// A esa hora el servidor completa lo que falte, confirma las dos y avisa a todos.

/** Estado de un lado, como lo devuelve lineup_status. */
@Serializable
data class LineupSideStatus(
    val side: String,
    /** true = el usuario gestiona este equipo y puede editar su alineación. */
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("submitted_at") val submittedAt: String? = null,
    /** El servidor completó jugadores porque no estaba lista a tiempo. */
    @SerialName("auto_filled") val autoFilled: Boolean = false,
) {
    val submitted: Boolean get() = submittedAt != null
}

/** Estado de las alineaciones de un partido. */
@Serializable
data class LineupStatus(
    /** true = partido acordado por desafío (este flujo). false = partido de siempre. */
    val flow: Boolean = false,
    /** Hora límite para editar (1 hora antes del partido); null fuera de este flujo. */
    val deadline: String? = null,
    @SerialName("confirmed_at") val confirmedAt: String? = null,
    val sides: List<LineupSideStatus> = emptyList(),
) {
    val confirmed: Boolean get() = confirmedAt != null
    fun side(side: String): LineupSideStatus? = sides.firstOrNull { it.side == side }
    /** Lados que el usuario puede editar. */
    val editableSides: Set<String> get() = sides.filter { it.canEdit }.map { it.side }.toSet()
}

/** Lógica pura del flujo de alineaciones: sin Android, para poder probarla con JUnit. */
object LineupFlow {

    /**
     * ¿Se puede editar todavía? En un partido de siempre, mientras esté programado.
     * En uno acordado, además, antes de la confirmación y antes de la hora límite.
     * El servidor aplica la misma regla; aquí solo sirve para desactivar botones a tiempo.
     */
    fun editable(match: MatchRow, status: LineupStatus?, nowMs: Long): Boolean {
        if (!match.isScheduled) return false
        if (status == null || !status.flow) return true
        if (status.confirmed) return false
        val deadline = parseInstant(status.deadline)?.toEpochMilli() ?: return false
        return nowMs < deadline
    }

    /**
     * Lados que se muestran en «Convocatoria y alineación». En un partido acordado, cada
     * entrenador ve solo el suyo (la alineación del rival es secreta hasta la confirmación).
     */
    fun visibleSides(allSides: List<String>, status: LineupStatus?): List<String> =
        if (status == null || !status.flow) allSides else allSides.filter { it in status.editableSides }
}

/** Un partido acordado cuyas alineaciones aún no se confirman. */
val MatchRow.lineupsPending: Boolean get() = lineupFlow && lineupsConfirmedAt == null && isScheduled
