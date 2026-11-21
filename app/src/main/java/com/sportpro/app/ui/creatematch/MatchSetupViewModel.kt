package com.sportpro.app.ui.creatematch
import com.sportpro.app.R
import com.sportpro.app.core.AppText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.Callup
import com.sportpro.app.core.data.ExternalDraft
import com.sportpro.app.core.data.ExternalPlayer
import com.sportpro.app.core.data.LineupFlow
import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.LineupStatus
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.MemberRow
import com.sportpro.app.core.data.PitchFormation
import com.sportpro.app.core.data.PitchFormations
import com.sportpro.app.core.data.Placeable
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.SlotAssignment
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.TokenInfo
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

// Preparación de un lado (local o visitante registrado): plantel y alineación guardada.
// La alineación se arma en el editor de la cancha (LineupEditorScreen, migración 018);
// aquí solo se muestra una vista previa de lo guardado.
data class SideSetup(
    val side: String,
    val teamId: String,
    val teamName: String,
    val roster: List<TeamPlayerRow> = emptyList(),
    /** Alineación guardada de este lado y nombre de su formación. */
    val saved: List<LineupPlayer> = emptyList(),
    val formationLabel: String? = null,
) {
    val starters: List<LineupPlayer> get() = saved.filter { it.isStarter }
    val subs: Int get() = saved.count { !it.isStarter }

    /** Formación y casillas para la vista previa (se acomodan igual que en el editor). */
    fun preview(format: Int): Pair<PitchFormation, List<SlotAssignment>> {
        val formation = PitchFormations.resolve(format, formationLabel, starters.map { Position.of(it.fieldPosition) })
        val placeable = starters.mapNotNull { p ->
            p.playerId?.let { Placeable(it, Position.of(p.fieldPosition), p.slotX, p.slotY, p.jerseyNumber) }
        }
        return formation to PitchFormations.place(formation, placeable).first
    }

    /** Datos de la ficha de un jugador de la vista previa. */
    fun token(playerId: String): TokenInfo? = saved.firstOrNull { it.playerId == playerId }?.let {
        TokenInfo(it.playerName ?: "?", it.photoPath, it.jerseyNumber)
    }
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
 * 3. Alineación (US-09): vista previa de la cancha y botón al editor (solo CONFIRMADOS, 1 arquero).
 * 4. Rival externo: completar o editar su plantel.
 * 5. Operador (D-16): quién registrará los eventos en vivo.
 * La convocatoria se actualiza en tiempo real cuando alguien responde. La pantalla vuelve a
 * cargar todo cada vez que aparece (por ejemplo, al volver del editor de la cancha).
 */
@OptIn(FlowPreview::class)
class MatchSetupViewModel(private val container: AppContainer, private val matchId: String) : ViewModel() {
    private val _ui = MutableStateFlow(MatchSetupUi())
    val ui: StateFlow<MatchSetupUi> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        // La primera carga la dispara la pantalla al aparecer (LifecycleResumeEffect)
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
                val labels = runCatching { container.matches.lineupFormations(matchId) }.getOrDefault(emptyList())
                val sides = candidates.filter { it.first in shown }.map { (side, teamId, name) ->
                    SideSetup(
                        side, teamId, name, container.club.teamPlayers(teamId),
                        saved = lineup.filter { it.teamSide == side && it.playerId != null },
                        formationLabel = labels.firstOrNull { it.teamSide == side }?.formation,
                    )
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
