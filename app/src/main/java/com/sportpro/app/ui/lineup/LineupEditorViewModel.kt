package com.sportpro.app.ui.lineup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.R
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.AppText
import com.sportpro.app.core.data.LineupDraft
import com.sportpro.app.core.data.LineupFlow
import com.sportpro.app.core.data.LineupStatus
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.PitchFormation
import com.sportpro.app.core.data.PitchFormations
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.TokenInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LineupEditorUi(
    val loading: Boolean = true,
    val error: String? = null,
    val match: MatchRow? = null,
    val teamName: String = "",
    /** Estado de las alineaciones si el partido es acordado por desafío (migración 017). */
    val status: LineupStatus? = null,
    /** Plantel activo del equipo y, de ellos, quiénes confirmaron asistencia. */
    val roster: List<TeamPlayerRow> = emptyList(),
    val confirmed: List<String> = emptyList(),
    /** Fotos que el servidor permite ver (id del jugador → ruta). */
    val photos: Map<String, String> = emptyMap(),
    /** Formaciones predeterminadas del formato y las propias del entrenador. */
    val presets: List<PitchFormation> = emptyList(),
    val customs: List<PitchFormation> = emptyList(),
    val draft: LineupDraft? = null,
    /** Casilla tocada: se abre la lista de jugadores para elegir quién va ahí. */
    val picking: Int? = null,
    val busy: Boolean = false,
    /** Hay cambios sin guardar (se pregunta antes de salir). */
    val dirty: Boolean = false,
) {
    fun editable(nowMs: Long): Boolean = match?.let { LineupFlow.editable(it, status, nowMs) } ?: false
    val flow: Boolean get() = status?.flow == true

    /** Posición habitual y dorsal de cada jugador del plantel. */
    val usual: Map<String, Position?> get() = roster.associate { it.playerId to Position.of(it.position) }
    val jersey: Map<String, Int?> get() = roster.associate { it.playerId to it.jerseyNumber }

    fun row(playerId: String): TeamPlayerRow? = roster.firstOrNull { it.playerId == playerId }

    /** Datos de la ficha: nombre, foto (si se puede ver) y dorsal. */
    fun token(playerId: String): TokenInfo? = row(playerId)?.let {
        TokenInfo(it.player?.fullName ?: "?", photos[playerId], it.jerseyNumber)
    }
}

/**
 * EDITOR DE ALINEACIÓN EN LA CANCHA de un lado (migración 018):
 * 1. El DT elige una formación (predeterminada o propia) y las casillas aparecen acomodadas.
 * 2. Toca una casilla y elige qué jugador va ahí (solo convocados que confirmaron).
 * 3. Puede arrastrar una casilla para ajustarla (por ejemplo, adelantar a un lateral) y, si la
 *    disposición ya no es la predeterminada, guardarla como formación propia (solo para él).
 * 4. Los confirmados que no están en la cancha son suplentes (bolitas fuera de la cancha) o
 *    «no juegan».
 * 5. Guardar envía todo en UNA operación al servidor; en un partido acordado además la ENVÍA.
 * El servidor vuelve a validar todo (confirmados, plazo, arquero); aquí solo se anticipa.
 */
class LineupEditorViewModel(
    private val container: AppContainer,
    private val matchId: String,
    val side: String,
) : ViewModel() {
    private val _ui = MutableStateFlow(LineupEditorUi())
    val ui: StateFlow<LineupEditorUi> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val match = container.matches.getMatch(matchId)
                    ?: return@launch _ui.update { it.copy(loading = false, error = AppText.get(R.string.match_setup_partido_no_encontrado_o_sin)) }
                val teamId = if (side == "home") match.homeTeamId else match.awayTeamId
                    ?: return@launch _ui.update { it.copy(loading = false, error = AppText.get(R.string.lineup_editor_external)) }
                val status = if (match.lineupFlow) runCatching { container.matches.lineupStatus(matchId) }.getOrNull() else null
                // En un partido acordado, solo el entrenador de ese lado arma su alineación
                if (status?.flow == true && side !in status.editableSides) {
                    _ui.update { it.copy(loading = false, error = AppText.get(R.string.lineup_editor_not_yours)) }
                    return@launch
                }
                val roster = container.club.teamPlayers(teamId).filter { it.status == "active" }
                val confirmed = container.matches.callups(matchId)
                    .filter { it.teamId == teamId && it.status == "confirmed" }
                    .map { it.playerId }
                    .filter { id -> roster.any { it.playerId == id } }
                    .sortedBy { id -> roster.first { it.playerId == id }.jerseyNumber ?: 99 }
                val photos = runCatching { container.matches.rosterPhotos(teamId) }.getOrDefault(emptyMap())
                val saved = container.matches.lineup(matchId).filter { it.teamSide == side && it.playerId != null }
                val label = runCatching { container.matches.lineupFormations(matchId) }.getOrDefault(emptyList())
                    .firstOrNull { it.teamSide == side }?.formation
                val customs = runCatching { container.matches.coachFormations(match.formatPlayers) }.getOrDefault(emptyList())
                    .mapNotNull { it.toFormation() }
                val formation = PitchFormations.resolve(
                    match.formatPlayers, label,
                    saved.filter { it.isStarter }.map { Position.of(it.fieldPosition) },
                )
                _ui.update {
                    LineupEditorUi(
                        loading = false, match = match,
                        teamName = if (side == "home") match.homeName else match.awayName,
                        status = status, roster = roster, confirmed = confirmed, photos = photos,
                        presets = PitchFormations.forFormat(match.formatPlayers), customs = customs,
                        draft = LineupDraft.fromSaved(formation, saved, confirmed),
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    // Aplica un cambio al borrador y lo marca como «sin guardar».
    private fun edit(f: (LineupDraft) -> LineupDraft) =
        _ui.update { s -> s.draft?.let { s.copy(draft = f(it), dirty = true) } ?: s }

    fun selectFormation(f: PitchFormation) = edit { it.withFormation(f) }
    fun openPicker(index: Int) = _ui.update { it.copy(picking = index) }
    fun closePicker() = _ui.update { it.copy(picking = null) }
    fun assign(index: Int, playerId: String) {
        edit { it.assign(index, playerId) }
        closePicker()
    }
    fun clear(index: Int) {
        edit { it.clear(index) }
        closePicker()
    }
    fun move(index: Int, x: Float, y: Float) = edit { it.move(index, x, y) }
    fun resetPositions() = edit { it.resetPositions() }
    fun toggleOut(playerId: String) = edit { it.toggleOut(playerId) }
    fun autoFill() {
        val s = _ui.value
        edit { it.autoFill(s.usual, s.jersey) }
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

    /**
     * Guardar: primero se revisa en el celular que no falten titulares (mensaje claro) y
     * después el servidor valida todo otra vez. En un partido acordado, además se envía.
     */
    fun save() {
        val s = _ui.value
        val draft = s.draft ?: return
        if (!draft.isComplete) {
            _messages.tryEmit(AppText.plural(R.plurals.lineup_editor_missing, draft.emptySlots))
            return
        }
        act {
            container.matches.saveLineup(matchId, side, draft.formation.label, draft.toEntries(s.usual, s.jersey))
            if (s.flow) {
                container.matches.submitLineup(matchId, side)
                _ui.update { it.copy(status = container.matches.lineupStatus(matchId), dirty = false) }
                _messages.tryEmit(AppText.get(R.string.lineup_sent, s.teamName))
            } else {
                _ui.update { it.copy(dirty = false) }
                _messages.tryEmit(AppText.get(R.string.lineup_editor_saved, s.teamName, draft.formation.label, draft.bench.size))
            }
        }
    }

    /** Guarda la disposición actual como formación propia del entrenador y la deja elegida. */
    fun saveCustom(name: String) {
        val s = _ui.value
        val draft = s.draft ?: return
        val match = s.match ?: return
        if (name.isBlank()) {
            _messages.tryEmit(AppText.get(R.string.lineup_editor_custom_name_required))
            return
        }
        act {
            val id = container.matches.saveCoachFormation(match.formatPlayers, name.trim(), draft.formation.label, draft.currentSlots())
            val customs = container.matches.coachFormations(match.formatPlayers).mapNotNull { it.toFormation() }
            // Se elige la nueva formación propia sin mover a nadie: sus casillas son las actuales
            val mine = customs.firstOrNull { it.customId == id }
            _ui.update { u -> u.copy(customs = customs, draft = mine?.let { f -> u.draft?.copy(formation = f) } ?: u.draft) }
            _messages.tryEmit(AppText.get(R.string.lineup_editor_custom_saved, name.trim()))
        }
    }

    /** Archiva (no borra) una formación propia. Si estaba elegida, la alineación no cambia de lugar. */
    fun archiveCustom(f: PitchFormation) {
        val id = f.customId ?: return
        val match = _ui.value.match ?: return
        act {
            container.matches.archiveCoachFormation(id)
            val customs = container.matches.coachFormations(match.formatPlayers).mapNotNull { it.toFormation() }
            _ui.update { u ->
                // Si era la elegida, se vuelve a la predeterminada con el mismo nombre, conservando los lugares
                val draft = u.draft?.let { d ->
                    if (d.formation.customId == id) d.copy(formation = PitchFormations.build(d.formation.label, match.formatPlayers) ?: d.formation)
                    else d
                }
                u.copy(customs = customs, draft = draft)
            }
            _messages.tryEmit(AppText.get(R.string.lineup_editor_custom_archived, f.displayName))
        }
    }
}
