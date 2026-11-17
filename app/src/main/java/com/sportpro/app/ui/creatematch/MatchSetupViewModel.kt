package com.sportpro.app.ui.creatematch
import com.sportpro.app.R
import com.sportpro.app.core.AppText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.Callup
import com.sportpro.app.core.data.ExternalDraft
import com.sportpro.app.core.data.ExternalPlayer
import com.sportpro.app.core.data.Formation
import com.sportpro.app.core.data.Formations
import com.sportpro.app.core.data.LineupFlow
import com.sportpro.app.core.data.LineupStatus
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.MemberRow
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.core.data.repo.MatchRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LineupRole { STARTER, SUB, OUT }

// Preparación de un lado (local o visitante registrado): plantel, rol de cada jugador,
// posiciones para este partido y formación elegida.
data class SideSetup(
    val side: String,
    val teamId: String,
    val teamName: String,
    val roster: List<TeamPlayerRow> = emptyList(),
    val roles: Map<String, LineupRole> = emptyMap(),
    /** Posición en ESTE partido (por defecto, la posición habitual del jugador en el equipo). */
    val positions: Map<String, Position?> = emptyMap(),
    val formation: Formation = Formations.default(7),
) {
    val starterIds: List<String> get() = roles.filterValues { it == LineupRole.STARTER }.keys.toList()
    fun positionOf(playerId: String): Position? =
        positions[playerId] ?: Position.of(roster.firstOrNull { it.playerId == playerId }?.position)
    val starterPositions: List<Position?> get() = starterIds.map { positionOf(it) }
}

data class MatchSetupUi(
    val loading: Boolean = true,
    val error: String? = null,
    val match: MatchRow? = null,
    val sides: List<SideSetup> = emptyList(),
    val callups: List<Callup> = emptyList(),
    val external: List<ExternalPlayer> = emptyList(),
    val members: List<MemberRow> = emptyList(),
    val operatorId: String? = null,
    val busy: Boolean = false,
    /** Estado de las alineaciones (partidos acordados por desafío, migración 017). */
    val status: LineupStatus? = null,
) {
    /** El operador lo designa el DT local; en un partido acordado el visitante no ve esa sección. */
    val canManageHome: Boolean get() = status?.side("home")?.canEdit ?: true
    /** ¿Se puede editar ahora? (plazo de 1 hora antes y confirmación; lo valida también el servidor). */
    fun editable(nowMs: Long): Boolean = match?.let { LineupFlow.editable(it, status, nowMs) } ?: false
    fun callupOf(playerId: String): Callup? = callups.firstOrNull { it.playerId == playerId }
    val externalStarters: List<ExternalPlayer> get() = external.filter { it.isStarter }
    // Validación del rival externo antes de iniciar (mismas reglas que al crear el partido).
    fun externalProblems(format: Int): List<String> {
        val out = mutableListOf<String>()
        if (externalStarters.size != format) out += AppText.get(R.string.match_setup_titulares_x_x, externalStarters.size, format)
        val gk = externalStarters.count { it.fieldPosition == "POR" }
        if (gk != 1) out += AppText.get(R.string.match_setup_arqueros_titulares_x_debe_ser, gk)
        if (external.any { it.jerseyNumber == null }) out += AppText.get(R.string.match_setup_hay_jugadores_sin_dorsal)
        if (external.any { it.fieldPosition == null }) out += AppText.get(R.string.match_setup_hay_jugadores_sin_posicion)
        return out
    }
}

/**
 * PREPARACIÓN DEL PARTIDO (pantalla del DT antes de iniciar), en este orden:
 * 1. Convocatoria (US-04): el DT llama jugadores; apoderados o jugadores confirman, o el DT
 *    confirma en persona.
 * 2. Posición habitual de cada jugador del plantel (editable).
 * 3. Formación + alineación (US-09): solo jugadores CONFIRMADOS y 1 arquero obligatorio.
 * 4. Rival externo: completar o editar su plantel.
 * 5. Operador (D-16): quién registrará los eventos en vivo.
 * La convocatoria se actualiza en tiempo real cuando alguien responde.
 */
@OptIn(FlowPreview::class)
class MatchSetupViewModel(private val container: AppContainer, private val matchId: String) : ViewModel() {
    private val _ui = MutableStateFlow(MatchSetupUi())
    val ui: StateFlow<MatchSetupUi> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
        viewModelScope.launch {
            container.matches.observeCallups(matchId).debounce(300).catch { }.collect { reloadCallups() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val match = container.matches.getMatch(matchId)
                if (match == null) {
                    _ui.update { it.copy(loading = false, error = AppText.get(R.string.match_setup_partido_no_encontrado_o_sin)) }
                    return@launch
                }
                val lineup = container.matches.lineup(matchId)
                // En un partido acordado, cada entrenador ve y arma SOLO su lado
                val status = if (match.lineupFlow) runCatching { container.matches.lineupStatus(matchId) }.getOrNull() else null
                val candidates = buildList {
                    add(Triple("home", match.homeTeamId, match.homeName))
                    match.awayTeamId?.let { add(Triple("away", it, match.awayName)) }
                }
                val shown = LineupFlow.visibleSides(candidates.map { it.first }, status)
                val sides = candidates.filter { it.first in shown }.map { (side, teamId, name) ->
                    SideSetup(side, teamId, name, container.club.teamPlayers(teamId))
                }.map { s ->
                    // Si ya había una alineación guardada, se reconstruyen roles y posiciones y se detecta la formación.
                    val existing = lineup.filter { it.teamSide == s.side && it.playerId != null }
                    val roles = existing.associate { it.playerId!! to if (it.isStarter) LineupRole.STARTER else LineupRole.SUB }
                    val positions = existing.associate { it.playerId!! to Position.of(it.fieldPosition) }
                    val base = s.copy(roles = roles, positions = positions)
                    val detected = Formations.detect(match.formatPlayers, base.starterPositions)
                    val formation = Formations.forFormat(match.formatPlayers).firstOrNull { it == detected }
                        ?: Formations.default(match.formatPlayers)
                    base.copy(formation = formation)
                }
                val callups = container.matches.callups(matchId)
                val external = if (match.awayTeamId == null) container.matches.externalPlayers(matchId) else emptyList()
                val members = runCatching { container.club.academyMembers(match.academyId) }.getOrDefault(emptyList())
                val op = runCatching { container.matches.currentOperator(matchId) }.getOrNull()
                _ui.update {
                    MatchSetupUi(
                        loading = false, match = match, sides = sides, callups = callups,
                        external = external, members = members.distinctBy { m -> m.userId }, operatorId = op?.userId,
                        status = status,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    private fun reloadCallups() {
        viewModelScope.launch {
            runCatching { container.matches.callups(matchId) }.onSuccess { c -> _ui.update { it.copy(callups = c) } }
        }
    }

    // Ejecuta una acción contra el servidor mostrando «ocupado» y convirtiendo errores en mensajes.
    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: Exception) {
                _messages.tryEmit(e.friendlyMessage())
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun updateSide(side: String, f: (SideSetup) -> SideSetup) =
        _ui.update { s -> s.copy(sides = s.sides.map { if (it.side == side) f(it) else it }) }

    // ---------- Convocatoria ----------
    // Convocar o quitar de la convocatoria a un jugador.
    fun toggleCallup(side: SideSetup, playerId: String) = act {
        val existing = _ui.value.callupOf(playerId)
        if (existing != null) container.matches.removeCallup(existing.id)
        else container.matches.addCallups(matchId, side.teamId, listOf(playerId))
        _ui.update { it.copy(callups = container.matches.callups(matchId)) }
    }

    // Convoca a todo el plantel que falte; a sus apoderados o a ellos les aparece para confirmar.
    fun callAll(side: SideSetup) = act {
        val missing = side.roster.map { it.playerId }.filter { _ui.value.callupOf(it) == null }
        container.matches.addCallups(matchId, side.teamId, missing)
        _ui.update { it.copy(callups = container.matches.callups(matchId)) }
        _messages.tryEmit(AppText.plural(R.plurals.match_setup_called_up, missing.size))
    }

    /** El DT confirma en bloque la asistencia comprobada en persona. Cada cambio queda trazado (responded_by). */
    fun confirmAll(side: SideSetup) = act {
        val pending = _ui.value.callups.filter { it.teamId == side.teamId && it.status == "pending" }
        pending.forEach { container.matches.setCallupStatus(it.id, "confirmed") }
        _ui.update { it.copy(callups = container.matches.callups(matchId)) }
        _messages.tryEmit(AppText.plural(R.plurals.match_setup_confirmed, pending.size))
    }

    // ---------- Posición habitual del plantel (editable por el DT) ----------
    fun setTeamPosition(side: SideSetup, tp: TeamPlayerRow, position: Position) = act {
        container.club.setTeamPlayerPosition(tp.id, position.code)
        updateSide(side.side) { s ->
            s.copy(roster = s.roster.map { if (it.id == tp.id) it.copy(position = position.code) else it })
        }
    }

    // ---------- Alineación y formación ----------
    fun setRole(side: SideSetup, playerId: String, role: LineupRole) = updateSide(side.side) { s ->
        s.copy(roles = s.roles + (playerId to role))
    }

    fun setMatchPosition(side: SideSetup, playerId: String, position: Position) = updateSide(side.side) { s ->
        s.copy(positions = s.positions + (playerId to position))
    }

    fun setFormation(side: SideSetup, formation: Formation) = updateSide(side.side) { it.copy(formation = formation) }

     /**
      * «Alineación automática» para la formación elegida, solo con jugadores CONFIRMADOS:
      * 1. Por cada línea (arquero, defensas, medios, delanteros) toma, por dorsal, a los jugadores
      *    cuya posición habitual coincide.
      * 2. Los cupos que quedan se llenan con los restantes, asignándoles la posición que falta.
      * 3. El resto de confirmados queda como suplente. El DT puede ajustar todo a mano después.
      */
    fun autoLineup(side: SideSetup) = _ui.update { s ->
        val current = s.sides.first { it.side == side.side }
        val confirmed = current.roster.filter { s.callupOf(it.playerId)?.status == "confirmed" }
            .sortedBy { it.jerseyNumber ?: 99 }
        val pool = confirmed.toMutableList()
        val positions = mutableMapOf<String, Position?>()
        val starters = mutableListOf<String>()
        Position.entries.forEach { p ->
            repeat(current.formation.required(p)) {
                val pick = pool.firstOrNull { Position.of(it.position) == p }
                if (pick != null) {
                    pool.remove(pick); starters += pick.playerId; positions[pick.playerId] = p
                }
            }
        }
        // Paso 2: completar los cupos vacíos con los que quedan (se les asigna la posición que falta).
        Position.entries.forEach { p ->
            val missing = current.formation.required(p) - positions.values.count { it == p }
            repeat(missing.coerceAtLeast(0)) {
                val pick = pool.firstOrNull()
                if (pick != null) {
                    pool.remove(pick); starters += pick.playerId; positions[pick.playerId] = p
                }
            }
        }
        pool.forEach { positions[it.playerId] = Position.of(it.position) }
        val roles = confirmed.associate { it.playerId to if (it.playerId in starters) LineupRole.STARTER else LineupRole.SUB }
        s.copy(sides = s.sides.map {
            if (it.side == side.side) it.copy(roles = roles, positions = it.positions + positions) else it
        })
    }

    // Guardar: primero se valida la formación en el celular (mensaje claro) y luego el servidor valida de nuevo.
    fun saveLineup(side: SideSetup) {
        val current = _ui.value.sides.first { it.side == side.side }
        val problems = Formations.problems(current.formation, current.starterPositions)
        if (problems.isNotEmpty()) {
            _messages.tryEmit(AppText.get(R.string.match_setup_formacion_x_incompleta_x, current.formation.name, problems.joinToString(", ")))
            return
        }
        act {
            val jersey = current.roster.associate { it.playerId to it.jerseyNumber }
            val entries = current.roles.filterValues { it != LineupRole.OUT }.map { (pid, role) ->
                MatchRepository.LineupEntry(pid, jersey[pid], role == LineupRole.STARTER, current.positionOf(pid)?.code)
            }
            container.matches.saveLineup(matchId, current.side, entries)
            // Partido acordado: además de guardar, se ENVÍA (el servidor valida titulares y arquero)
            if (_ui.value.status?.flow == true) {
                container.matches.submitLineup(matchId, current.side)
                _ui.update { it.copy(status = container.matches.lineupStatus(matchId)) }
                _messages.tryEmit(AppText.get(R.string.lineup_sent, current.teamName))
                return@act
            }
            _messages.tryEmit(
                AppText.get(R.string.match_setup_alineacion_de_x_guardada_x, current.teamName, current.formation.name) +
                    AppText.get(R.string.match_setup_x_titulares_x_suplentes, entries.count { it.starter }, entries.count { !it.starter }),
            )
        }
    }

    // ---------- Rival externo ----------
    fun updateExternal(x: ExternalPlayer, jersey: Int? = x.jerseyNumber, position: String? = x.fieldPosition, starter: Boolean = x.isStarter) = act {
        container.matches.updateExternal(x.id, jersey, x.name, position, starter)
        _ui.update { it.copy(external = container.matches.externalPlayers(matchId)) }
    }

    fun deleteExternal(x: ExternalPlayer) = act {
        container.matches.deleteExternal(x.id)
        _ui.update { it.copy(external = container.matches.externalPlayers(matchId)) }
    }

    // Agregar un jugador al rival externo: nombre, dorsal único y posición obligatorios.
    fun addExternal(row: ExternalDraft, onDone: () -> Unit) {
        val jersey = row.jersey.toIntOrNull()
        val error = when {
            row.name.isBlank() -> AppText.get(R.string.match_setup_escribe_el_nombre_del_jugador)
            jersey == null -> AppText.get(R.string.match_setup_escribe_el_dorsal)
            _ui.value.external.any { it.jerseyNumber == jersey } -> AppText.get(R.string.match_setup_el_dorsal_x_ya_existe, jersey)
            row.position == null -> AppText.get(R.string.match_setup_elige_la_posicion)
            else -> null
        }
        if (error != null) {
            _messages.tryEmit(error)
            return
        }
        act {
            container.matches.addExternalPlayers(matchId, listOf(row))
            _ui.update { it.copy(external = container.matches.externalPlayers(matchId)) }
            onDone()
        }
    }

    // ---------- Operador ----------
    // Designar operador: solo esa persona (y el DT) podrá registrar eventos durante el partido.
    fun setOperator(userId: String) = act {
        container.matches.setOperator(matchId, userId)
        _ui.update { it.copy(operatorId = userId) }
        _messages.tryEmit(AppText.get(R.string.match_setup_operador_designado_solo_esa_persona))
    }
}
