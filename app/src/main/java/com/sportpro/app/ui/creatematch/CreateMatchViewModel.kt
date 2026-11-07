package com.sportpro.app.ui.creatematch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.ExternalDraft
import com.sportpro.app.core.data.Formations
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TeamRef
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.parseDateDigits
import com.sportpro.app.ui.components.parseTimeDigits
import com.sportpro.app.ui.components.toDateDigits
import com.sportpro.app.ui.components.toTimeDigits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class CreateMatchForm(
    val homeTeamId: String? = null,
    val externalRival: Boolean = true,
    val awayTeamId: String? = null,
    val externalName: String = "",
    val externalRoster: List<ExternalDraft> = emptyList(),
    val formatPlayers: Int = 7,
    val customFormat: Boolean = false,
    val halfMinutes: Int = 25,
    val dateDigits: String = LocalDate.now().toDateDigits(),
    val timeDigits: String = LocalTime.now().toTimeDigits(),
    val venue: String = "",
    val visibility: String = "followers",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val filledRoster: List<ExternalDraft> get() = externalRoster.filter { it.name.isNotBlank() || it.jersey.isNotBlank() }
    val externalStarters: List<ExternalDraft> get() = filledRoster.filter { it.starter }

    /**
     * Validación del plantel del RIVAL EXTERNO (equipo sin cuenta en la app): titulares = formato,
     * exactamente 1 arquero titular, nombre y dorsal en cada fila, dorsales sin repetir y posición
     * asignada. Lista vacía = válido.
     */
    fun rosterProblems(): List<String> {
        val out = mutableListOf<String>()
        val rows = filledRoster
        val starters = externalStarters
        if (starters.size != formatPlayers) out += "Titulares ${starters.size}/$formatPlayers"
        val gk = starters.count { it.position == Position.POR }
        if (gk != 1) out += "Debe haber exactamente 1 arquero titular (hay $gk)"
        if (rows.any { it.name.isBlank() }) out += "Hay filas sin nombre"
        if (rows.any { it.jersey.toIntOrNull() == null }) out += "Todos necesitan dorsal (0–99)"
        val dup = rows.mapNotNull { it.jersey.toIntOrNull() }.groupBy { it }.filter { it.value.size > 1 }.keys
        if (dup.isNotEmpty()) out += "Dorsales repetidos: ${dup.joinToString()}"
        if (rows.any { it.position == null }) out += "Asigna la posición de cada jugador"
        return out
    }
}

/**
 * Crear partido. Formatos D-14 (5/7/9/11 o personalizado de 5 a 11). Rival D-15: un equipo
 * registrado en la app o un rival externo con su plantel completo y posiciones.
 */
class CreateMatchViewModel(private val container: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow(CreateMatchForm().withRosterSize())
    val form: StateFlow<CreateMatchForm> = _form.asStateFlow()

    fun update(transform: (CreateMatchForm) -> CreateMatchForm) =
        _form.update { transform(it).withRosterSize().copy(error = null) }

    fun selectHome(team: TeamRef) = update {
        it.copy(homeTeamId = team.id, formatPlayers = team.defaultFormatPlayers, halfMinutes = team.halfMinutes)
    }

    fun setRow(index: Int, row: ExternalDraft) = update { f ->
        f.copy(externalRoster = f.externalRoster.mapIndexed { i, r -> if (i == index) row else r })
    }

    fun addSubstitute() = update { f -> f.copy(externalRoster = f.externalRoster + ExternalDraft(starter = false)) }

    fun removeRow(index: Int) = update { f -> f.copy(externalRoster = f.externalRoster.filterIndexed { i, _ -> i != index }) }

    /** «Sugerir posiciones»: completa las vacías según la formación por defecto (primero el arquero) sin tocar lo ya elegido. */
    fun suggestPositions() = update { f ->
        val formation = Formations.default(f.formatPlayers)
        val queue = buildList {
            add(Position.POR)
            repeat(formation.def) { add(Position.DEF) }
            repeat(formation.med) { add(Position.MED) }
            repeat(formation.del) { add(Position.DEL) }
        }.toMutableList()
        f.externalRoster.filter { it.starter && it.position != null }.forEach { queue.remove(it.position) }
        f.copy(externalRoster = f.externalRoster.map { r ->
            if (r.starter && r.position == null && queue.isNotEmpty()) r.copy(position = queue.removeAt(0)) else r
        })
    }

    // Valida en el celular (mensajes claros) y luego crea en el servidor, que vuelve a validar.
    fun create(onCreated: (String) -> Unit) {
        val f = _form.value
        val date = parseDateDigits(f.dateDigits)
        val time = parseTimeDigits(f.timeDigits)
        val rosterProblems = if (f.externalRival) f.rosterProblems() else emptyList()
        val error = when {
            f.homeTeamId == null -> "Elige tu equipo."
            f.externalRival && f.externalName.trim().length < 2 -> "Escribe el nombre del rival externo."
            !f.externalRival && f.awayTeamId == null -> "Elige el equipo rival."
            f.formatPlayers !in 5..11 -> "El formato debe ser de 5 a 11 jugadores."
            f.halfMinutes !in 5..60 -> "Cada tiempo debe durar entre 5 y 60 minutos."
            date == null -> "Fecha inválida: escribe 8 dígitos (DD/MM/AAAA)."
            time == null -> "Hora inválida: escribe 4 dígitos (HH:MM, 24 h)."
            rosterProblems.isNotEmpty() -> "Plantel del rival: " + rosterProblems.joinToString("; ") + "."
            else -> null
        }
        if (error != null) {
            _form.update { it.copy(error = error) }
            return
        }
        // Fecha y hora locales (DD/MM/AAAA + HH:MM) → instante con zona horaria (ISO-8601) para el servidor.
        val iso = LocalDateTime.of(date!!, time!!).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
        viewModelScope.launch {
            _form.update { it.copy(busy = true) }
            try {
                val id = container.matches.createMatch(
                    homeTeamId = f.homeTeamId!!,
                    awayTeamId = if (f.externalRival) null else f.awayTeamId,
                    awayExternalName = if (f.externalRival) f.externalName.trim() else null,
                    formatPlayers = f.formatPlayers,
                    halfMinutes = f.halfMinutes,
                    scheduledAtIso = iso,
                    venue = f.venue.trim().ifBlank { null },
                    visibility = f.visibility,
                    externalRoster = if (f.externalRival) f.filledRoster else emptyList(),
                )
                _form.update { it.copy(busy = false) }
                onCreated(id)
            } catch (e: Exception) {
                _form.update { it.copy(busy = false, error = e.friendlyMessage()) }
            }
        }
    }
}

/** Ajusta las filas de titulares al formato: agrega las que falten (la primera, arquero) y quita solo filas vacías. */
private fun CreateMatchForm.withRosterSize(): CreateMatchForm {
    var starters = externalRoster.count { it.starter }
    if (starters > formatPlayers) {
        // Si se reduce el formato, solo se eliminan filas de titulares VACÍAS del final (nunca lo que escribió el DT).
        val rows = externalRoster.toMutableList()
        var i = rows.lastIndex
        while (starters > formatPlayers && i >= 0) {
            val r = rows[i]
            if (r.starter && r.name.isBlank() && r.jersey.isBlank()) {
                rows.removeAt(i); starters--
            }
            i--
        }
        return copy(externalRoster = rows)
    }
    if (starters == formatPlayers) return this
    val extra = List(formatPlayers - starters) { i ->
        ExternalDraft(starter = true, position = if (externalRoster.isEmpty() && i == 0) Position.POR else null)
    }
    return copy(externalRoster = externalRoster + extra)
}
