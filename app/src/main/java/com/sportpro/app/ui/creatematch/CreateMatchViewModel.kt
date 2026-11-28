package com.sportpro.app.ui.creatematch
import com.sportpro.app.R
import com.sportpro.app.core.AppText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.AcademyRef
import com.sportpro.app.core.data.Challenges
import com.sportpro.app.core.data.ExternalDraft
import com.sportpro.app.core.data.Formations
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.RivalTeam
import com.sportpro.app.core.data.TeamRef
import com.sportpro.app.core.data.TeamCoach
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
import java.time.Instant
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
    /**
     * true cuando la persona eligió el formato / la duración a mano. Desde ese momento, cambiar de
     * equipo local ya NO los reemplaza por los del equipo (antes sí: elegías 5 vs 5, tocabas tu
     * equipo y volvía en silencio a su formato por defecto, por ejemplo 11).
     */
    val formatChosen: Boolean = false,
    val minutesChosen: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    // ---- Rival registrado en SportPro: academia → equipo (migración 015) ----
    /** Academias que se pueden elegir como rival (verificadas y la propia). */
    val academies: List<AcademyRef> = emptyList(),
    /** Texto del buscador de academias. */
    val academyQuery: String = "",
    val rivalAcademyId: String? = null,
    /** Equipos de la academia elegida, con su entrenador y si están en mi división. */
    val rivalTeams: List<RivalTeam> = emptyList(),
    val rivalsLoading: Boolean = false,
    /** Entrenador de cada equipo que gestiono (para que el administrador sepa a quién le propone). */
    val coaches: Map<String, TeamCoach> = emptyMap(),
) {
    /** Academias que pasan el buscador. */
    val shownAcademies: List<AcademyRef> get() = Challenges.filterAcademies(academies, academyQuery)
    val rivalAcademy: AcademyRef? get() = academies.firstOrNull { it.id == rivalAcademyId }
    val rivalTeam: RivalTeam? get() = rivalTeams.firstOrNull { it.teamId == awayTeamId }

    /**
     * true = el rival es el equipo de OTRO entrenador: no se crea el partido, se le envía un
     * desafío para que lo acepte o rechace. Si también gestiono al rival, se crea directo.
     */
    fun isChallenge(managedTeamIds: Set<String>): Boolean =
        !externalRival && awayTeamId != null && awayTeamId !in managedTeamIds

    /**
     * true = el partido necesita que lo acepte algún entrenador antes de crearse:
     *  - el rival registrado no es un equipo que yo dirija (desafío normal), o
     *  - yo soy administrador y NO dirijo el equipo local: su DT debe aceptar (propuesta).
     * [staffTeamIds] son los equipos donde soy DT (staff), no todos los que administro.
     */
    fun needsApproval(staffTeamIds: Set<String>): Boolean =
        isChallenge(staffTeamIds) || (homeTeamId != null && homeTeamId !in staffTeamIds)

    /** Propuesta de administrador: el equipo local no lo dirijo yo. */
    fun isAdminProposal(staffTeamIds: Set<String>): Boolean = homeTeamId != null && homeTeamId !in staffTeamIds

    val filledRoster: List<ExternalDraft> get() = externalRoster.filter { it.name.isNotBlank() || it.jersey.isNotBlank() }
    val externalStarters: List<ExternalDraft> get() = filledRoster.filter { it.starter }

    /**
     * Validación del plantel del RIVAL EXTERNO (equipo sin cuenta en la app): titulares = formato,
     * exactamente 1 arquero titular, nombre y dorsal en cada fila, dorsales sin repetir y posición
     * asignada. Lista vacía = válido.
     */
    /**
     * Primer problema del formulario en el orden en que se llena, o null si se puede crear el partido.
     * Está aquí (y no en el ViewModel) para poder probarlo con tests unitarios, sin red ni Android.
     */
    fun validationError(): String? {
        val rosterProblems = if (externalRival) rosterProblems() else emptyList()
        return when {
            homeTeamId == null -> AppText.get(R.string.create_match_elige_tu_equipo)
            externalRival && externalName.trim().length < 2 -> AppText.get(R.string.create_match_escribe_el_nombre_del_rival)
            !externalRival && awayTeamId == null -> AppText.get(R.string.create_match_elige_el_equipo_rival)
            formatPlayers !in 5..11 -> AppText.get(R.string.create_match_el_formato_debe_ser_de)
            halfMinutes !in 5..60 -> AppText.get(R.string.create_match_cada_tiempo_debe_durar_entre)
            parseDateDigits(dateDigits) == null -> AppText.get(R.string.create_match_fecha_invalida_escribe_8_digitos)
            parseTimeDigits(timeDigits) == null -> AppText.get(R.string.create_match_hora_invalida_escribe_4_digitos)
            rosterProblems.isNotEmpty() -> AppText.get(R.string.create_match_plantel_del_rival) + rosterProblems.joinToString("; ") + "."
            else -> null
        }
    }

    fun rosterProblems(): List<String> {
        val out = mutableListOf<String>()
        val rows = filledRoster
        val starters = externalStarters
        if (starters.size != formatPlayers) out += AppText.get(R.string.create_match_titulares_x_x, starters.size, formatPlayers)
        val gk = starters.count { it.position == Position.POR }
        if (gk != 1) out += AppText.get(R.string.create_match_debe_haber_exactamente_1_arquero, gk)
        if (rows.any { it.name.isBlank() }) out += AppText.get(R.string.create_match_hay_filas_sin_nombre)
        if (rows.any { it.jersey.toIntOrNull() == null }) out += AppText.get(R.string.create_match_todos_necesitan_dorsal_0_99)
        val dup = rows.mapNotNull { it.jersey.toIntOrNull() }.groupBy { it }.filter { it.value.size > 1 }.keys
        if (dup.isNotEmpty()) out += AppText.get(R.string.create_match_dorsales_repetidos_x, dup.joinToString())
        if (rows.any { it.position == null }) out += AppText.get(R.string.create_match_asigna_la_posicion_de_cada)
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

    init {
        loadAcademies()
        loadCoaches()
    }

    // Quién dirige cada equipo que gestiono: el admin elige el equipo viendo a su entrenador
    private fun loadCoaches() {
        viewModelScope.launch {
            runCatching { container.challenges.managedTeamCoaches() }
                .onSuccess { list -> _form.update { it.copy(coaches = list.associateBy { c -> c.teamId }) } }
        }
    }

    // Academias que el usuario puede ver (verificadas y la suya), para elegir rival registrado.
    private fun loadAcademies() {
        viewModelScope.launch {
            try {
                val list = container.club.visibleAcademies()
                _form.update { it.copy(academies = list) }
            } catch (_: Exception) {
                // Sin lista de academias todavía se puede crear un partido contra un rival externo
            }
        }
    }

    // Al cambiar de equipo local cambia la división permitida: se vuelve a pedir la lista de rivales.
    fun selectHome(team: TeamRef) {
        update {
            it.copy(
                homeTeamId = team.id,
                // Los valores del equipo solo se sugieren si la persona aún no eligió los suyos
                formatPlayers = if (it.formatChosen) it.formatPlayers else team.defaultFormatPlayers,
                halfMinutes = if (it.minutesChosen) it.halfMinutes else team.halfMinutes,
                awayTeamId = null, rivalTeams = emptyList(),
            )
        }
        _form.value.rivalAcademyId?.let { loadRivals(team.id, it) }
    }

    /** Formato elegido a mano (5 a 11). [custom] = viene del deslizador «Otro». */
    fun setFormat(players: Int, custom: Boolean) =
        update { it.copy(formatPlayers = players.coerceIn(5, 11), customFormat = custom, formatChosen = true) }

    /** Minutos de cada tiempo elegidos a mano. */
    fun setHalfMinutes(minutes: Int) = update { it.copy(halfMinutes = minutes.coerceIn(5, 45), minutesChosen = true) }

    fun setAcademyQuery(query: String) = update { it.copy(academyQuery = query.take(40)) }

    /** Elige la academia rival y trae sus equipos. Con null vuelve al buscador de academias. */
    fun selectAcademy(academyId: String?) {
        update { it.copy(rivalAcademyId = academyId, awayTeamId = null, rivalTeams = emptyList()) }
        val home = _form.value.homeTeamId
        if (academyId != null && home != null) loadRivals(home, academyId)
    }

    // El servidor devuelve cada equipo con su entrenador y si está dentro de mi división (-1 a +1).
    private fun loadRivals(homeTeamId: String, academyId: String) {
        viewModelScope.launch {
            _form.update { it.copy(rivalsLoading = true) }
            try {
                val teams = container.challenges.rivalTeams(homeTeamId, academyId)
                // Si mientras tanto el usuario cambió de equipo o de academia, esta respuesta ya no sirve
                _form.update {
                    if (it.homeTeamId == homeTeamId && it.rivalAcademyId == academyId) it.copy(rivalTeams = teams, rivalsLoading = false)
                    else it
                }
            } catch (e: Exception) {
                _form.update { it.copy(rivalsLoading = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Solo se puede elegir un equipo que el servidor marcó como disponible. */
    fun selectRival(team: RivalTeam) {
        if (team.eligible) update { it.copy(awayTeamId = team.teamId) }
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
    /**
     * [managedTeamIds]: equipos que gestiona el usuario. Si el rival registrado NO es suyo, en vez
     * de crear el partido se envía un desafío y se llama a [onChallengeSent].
     */
    fun create(staffTeamIds: Set<String>, onCreated: (String) -> Unit, onChallengeSent: () -> Unit) {
        val f = _form.value
        val date = parseDateDigits(f.dateDigits)
        val time = parseTimeDigits(f.timeDigits)
        val error = f.validationError()
        if (error != null) {
            _form.update { it.copy(error = error) }
            return
        }
        // Un rival externo no tiene entrenador que acepte: ese partido lo crea el DT del equipo local
        if (f.externalRival && f.isAdminProposal(staffTeamIds)) {
            _form.update { it.copy(error = AppText.get(R.string.create_match_externo_solo_dt)) }
            return
        }
        // Fecha y hora locales (DD/MM/AAAA + HH:MM) → instante con zona horaria (ISO-8601) para el servidor.
        val start = LocalDateTime.of(date!!, time!!).atZone(ZoneId.systemDefault())
        val iso = start.toOffsetDateTime().toString()
        if (f.needsApproval(staffTeamIds)) {
            // Las alineaciones se cierran 1 hora antes: el servidor exige esa anticipación
            if (start.toInstant().isBefore(Instant.now().plusSeconds(3600))) {
                _form.update { it.copy(error = AppText.get(R.string.create_match_desafio_con_una_hora)) }
                return
            }
            viewModelScope.launch {
                _form.update { it.copy(busy = true) }
                try {
                    container.challenges.create(
                        homeTeamId = f.homeTeamId!!, awayTeamId = f.awayTeamId!!,
                        formatPlayers = f.formatPlayers, halfMinutes = f.halfMinutes,
                        scheduledAtIso = iso, venue = f.venue.trim().ifBlank { null }, visibility = f.visibility,
                    )
                    _form.update { it.copy(busy = false) }
                    onChallengeSent()
                } catch (e: Exception) {
                    _form.update { it.copy(busy = false, error = e.friendlyMessage()) }
                }
            }
            return
        }
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
internal fun CreateMatchForm.withRosterSize(): CreateMatchForm {
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
