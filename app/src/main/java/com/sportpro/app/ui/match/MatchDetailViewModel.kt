package com.sportpro.app.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.EventType
import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.onFieldPlayers
import com.sportpro.app.core.data.TimelineEvent
import com.sportpro.app.core.data.clock
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.core.data.offline.PendingEvent
import com.sportpro.app.core.data.offline.SubmitResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

// Pasos del registro rápido: equipo → (resultado del penal) → jugador; en cambios: sale → entra.
enum class DraftStep { SIDE, PENALTY_RESULT, PLAYER, SUB_OUT, SUB_IN }

/**
 * Evento que se está registrando. Flujo de 3 toques: ícono → equipo → jugador → se guarda al
 * instante. El minuto se toma del reloj al tocar el ícono y se puede ajustar (±1 y añadido).
 */
data class EventDraft(
    val type: EventType,
    val step: DraftStep = DraftStep.SIDE,
    val side: String? = null,
    val minute: Int,
    val extra: Int,
    val penaltyResult: String? = null,
    val ownGoal: Boolean = false,
    val playerOut: LineupPlayer? = null,
)

// Mensaje para el snackbar; si trae ids, permite «Deshacer» el registro recién hecho.
data class UiMessage(val text: String, val undoClientEventId: String? = null, val undoEventId: String? = null)

data class MatchDetailUi(
    val loading: Boolean = true,
    val error: String? = null,
    val match: MatchRow? = null,
    val timeline: List<TimelineEvent> = emptyList(),
    val lineup: List<LineupPlayer> = emptyList(),
    val eventTypes: List<EventType> = emptyList(),
    val isManager: Boolean = false,
    val isOperator: Boolean = false,
    val busy: Boolean = false,
    val draft: EventDraft? = null,
) {
    private val live: Boolean get() = match?.isLive == true
    /** Botones iniciar / entretiempo / finalizar: DT u operador, con el partido programado o en juego. */
    val canControl: Boolean get() = (isManager || isOperator) && (live || match?.isScheduled == true)
    /** Registrar: operador o DT con el partido en vivo; el DT también después del final (queda como tardío). */
    val canRecord: Boolean get() = (live && (isManager || isOperator)) || (match?.isFinished == true && isManager)
    /** Corregir/anular: DT siempre; operador solo en vivo. El servidor aplica la misma regla (la UI solo oculta botones). */
    val canCorrect: Boolean get() = isManager || (isOperator && live)
    // Tipos que se pueden registrar: sin los de control (inicio/fin) ni los de otras academias;
    // primero los principales (gol, tarjetas...) y luego el resto.
    val recordableTypes: List<EventType>
        get() = eventTypes.filter { it.groupKind != "control" && (it.academyId == null || it.academyId == match?.academyId) }
            .sortedWith(compareBy({ if (it.groupKind == "main") 0 else 1 }, { it.sortOrder }))

    fun sideName(side: String): String = when (side) {
        "home" -> match?.homeName ?: "Local"
        "away" -> match?.awayName ?: "Rival"
        else -> ""
    }

    fun playersOf(side: String): List<LineupPlayer> =
        lineup.filter { it.teamSide == side }.sortedWith(compareBy({ !it.isStarter }, { it.jerseyNumber ?: 99 }))

    fun nameOf(id: String?): String? = id?.let { k -> lineup.firstOrNull { it.key == k }?.playerName }

    /** Quién está en cancha ahora (titulares ± cambios − rojas), con su posición actual. */
    fun onField(side: String): Map<String, Position?> = onFieldPlayers(lineup, timeline, side)

    // Jugadores que ya salieron (sustituidos o expulsados): no vuelven a aparecer en la banca.
    private fun leftTheGame(side: String): Set<String> = timeline
        .filter { !it.isVoided && it.teamSide == side }
        .flatMap { e ->
            when (e.code) {
                "substitution" -> listOfNotNull(e.payloadString("player_out_id"))
                "red_card" -> listOfNotNull(e.actorKey)
                else -> emptyList()
            }
        }.toSet()

    /** Jugadores en cancha ordenados: arquero, defensas, medios, delanteros (y luego por dorsal). */
    fun fieldPlayers(side: String): List<LineupPlayer> {
        val field = onField(side)
        return lineup.filter { it.teamSide == side && it.key in field.keys }
            .map { it.copy(fieldPosition = field[it.key]?.code ?: it.fieldPosition) }
            .sortedWith(compareBy({ Position.of(it.fieldPosition)?.ordinal ?: 9 }, { it.jerseyNumber ?: 99 }))
    }

    /** Banca: está en la lista del partido, no está en cancha y no fue sustituido ni expulsado. */
    fun benchPlayers(side: String): List<LineupPlayer> {
        val field = onField(side).keys
        val gone = leftTheGame(side)
        return lineup.filter { it.teamSide == side && it.key !in field && it.key !in gone }
            .sortedBy { it.jerseyNumber ?: 99 }
    }

    // Arquero en cancha AHORA (considera los cambios): se usa para registrar atajadas automáticamente.
    fun goalkeeperOnField(side: String): LineupPlayer? =
        fieldPlayers(side).firstOrNull { it.fieldPosition == Position.POR.code }

    fun otherSide(side: String): String = if (side == "home") "away" else "home"

    /** Cronología en orden de fútbol (periodo, minuto, añadido, seq): los eventos tardíos o sin conexión caen en su lugar. */
    val ordered: List<TimelineEvent>
        get() = timeline.sortedWith(compareBy({ it.period }, { it.minute }, { it.minuteExtra }, { it.seq }))
}

/**
 * ViewModel de la pantalla del partido en vivo (detalle + registro de eventos):
 * - carga el partido, la cronología, la alineación, el catálogo y los permisos;
 * - escucha Realtime y ante cualquier cambio vuelve a leer (debounce de 250 ms para agrupar ráfagas);
 * - registra eventos a través de la cola sin conexión (PendingEventQueue);
 * - corrige y anula con motivo (auditoría en el servidor).
 */
@OptIn(FlowPreview::class)
class MatchDetailViewModel(private val container: AppContainer, private val matchId: String) : ViewModel() {
    private val _ui = MutableStateFlow(MatchDetailUi())
    val ui: StateFlow<MatchDetailUi> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val pending: StateFlow<List<PendingEvent>> = container.queue.items
    val online: StateFlow<Boolean> = container.queue.online

    init {
        load()
        viewModelScope.launch {
            container.matches.observeMatch(matchId)
                .debounce(250)
                .catch { _messages.tryEmit(UiMessage("Tiempo real no disponible; usa actualizar.")) }
                .collect { refresh() }
        }
        viewModelScope.launch {
            // Cuando un evento que estaba en cola llega al servidor, se refresca la cronología.
            container.queue.sent.filter { it == matchId }.collect { refresh() }
        }
        viewModelScope.launch {
            // Rechazos del servidor de eventos que estaban en cola → se muestran al usuario.
            container.queue.errors.collect { _messages.tryEmit(UiMessage(it)) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val match = container.matches.getMatch(matchId)
                if (match == null) {
                    _ui.update { it.copy(loading = false, error = "No tienes acceso a este partido o no existe.") }
                    return@launch
                }
                val timeline = container.matches.timeline(matchId)
                val lineup = container.matches.lineup(matchId)
                val types = container.matches.eventTypes()
                val manager = container.matches.isManager(matchId)
                val operator = container.matches.isOperator(matchId)
                _ui.update {
                    it.copy(
                        loading = false, match = match, timeline = timeline, lineup = lineup,
                        eventTypes = types, isManager = manager, isOperator = operator,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Relectura tras un aviso de Realtime (los nombres vienen de la RPC, que aplica privacidad). Si falla, se conserva lo último. */
    fun refresh() {
        viewModelScope.launch {
            try {
                val match = container.matches.getMatch(matchId) ?: return@launch
                val timeline = container.matches.timeline(matchId)
                val types = if (_ui.value.eventTypes.isEmpty()) container.matches.eventTypes() else _ui.value.eventTypes
                _ui.update { it.copy(match = match, timeline = timeline, eventTypes = types) }
            } catch (_: Exception) {
                // keep last known state; the offline indicator tells the user
            }
        }
    }

    fun reloadCatalog() {
        viewModelScope.launch {
            runCatching { container.matches.eventTypes() }.onSuccess { t -> _ui.update { it.copy(eventTypes = t) } }
        }
    }

    // ---------------- Match state ----------------
    // Iniciar / entretiempo / 2.º tiempo / finalizar: todo lo valida el servidor (change_match_state).
    fun changeState(action: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                container.matches.changeState(matchId, action)
                refresh()
                _messages.tryEmit(UiMessage(when (action) {
                    "start" -> "¡Partido iniciado!"
                    "halftime" -> "Fin del primer tiempo"
                    "second_half" -> "Inicia el segundo tiempo"
                    else -> "Partido finalizado. Las estadísticas se consolidaron."
                }))
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage(e.friendlyMessage()))
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    // ---------------- Recording ----------------
    // FLUJO DE REGISTRO:
    // 1) startDraft: el operador toca el ícono del evento; se congela el minuto actual del reloj.
    // 2) chooseSide: elige el equipo. 3) choosePlayer: elige el jugador (o «Sin identificar») y se guarda.
    fun startDraft(type: EventType) {
        val m = _ui.value.match ?: return
        val c = if (m.isFinished) m.clock().copy() else m.clock()
        _ui.update { it.copy(draft = EventDraft(type = type, minute = c.minute, extra = c.extra)) }
    }

    fun cancelDraft() = _ui.update { it.copy(draft = null) }

    fun adjustMinute(delta: Int) = _ui.update { s ->
        val d = s.draft ?: return@update s
        s.copy(draft = d.copy(minute = (d.minute + delta).coerceIn(0, 200)))
    }

    fun adjustExtra(delta: Int) = _ui.update { s ->
        val d = s.draft ?: return@update s
        s.copy(draft = d.copy(extra = (d.extra + delta).coerceIn(0, 30)))
    }

    fun toggleOwnGoal() = _ui.update { s ->
        val d = s.draft ?: return@update s
        s.copy(draft = d.copy(ownGoal = !d.ownGoal))
    }

    // Tras elegir equipo, el siguiente paso depende del tipo: penal → resultado; cambio → quién sale;
    // el resto → jugador. La atajada se resuelve sola con el arquero en cancha.
    fun chooseSide(side: String) {
        val s = _ui.value
        val d = s.draft ?: return
        if (d.type.code == "save") {
            // ATAJADA: solo hay un arquero en cancha → se registra directo a su nombre (sin lista ni error de jugador).
            val gk = s.goalkeeperOnField(side)
            if (gk != null) {
                submit(d.copy(side = side), gk)
                return
            }
        }
        val next = when (d.type.code) {
            "penalty" -> DraftStep.PENALTY_RESULT
            "substitution" -> DraftStep.SUB_OUT
            else -> DraftStep.PLAYER
        }
        _ui.update { it.copy(draft = d.copy(side = side, step = next)) }
    }

    fun choosePenaltyResult(result: String) = _ui.update { s ->
        val d = s.draft ?: return@update s
        s.copy(draft = d.copy(penaltyResult = result, step = DraftStep.PLAYER))
    }

    fun chooseSubOut(p: LineupPlayer) = _ui.update { s ->
        val d = s.draft ?: return@update s
        s.copy(draft = d.copy(playerOut = p, step = DraftStep.SUB_IN))
    }

    /** Tocar un jugador (o null = «Sin identificar», D-17) guarda el evento al instante. */
    fun choosePlayer(p: LineupPlayer?) {
        val d = _ui.value.draft ?: return
        submit(d, p)
    }

    private fun submit(d: EventDraft, player: LineupPlayer?) {
        submitOne(d, player, announce = true)
        // PENAL ATAJADO: además del penal se registra automáticamente una atajada para el arquero del OTRO equipo.
        if (d.type.code == "penalty" && d.penaltyResult == "saved") {
            val s = _ui.value
            val side = d.side ?: return
            val saveType = s.eventTypes.firstOrNull { it.code == "save" && it.isActive } ?: return
            val gkSide = s.otherSide(side)
            val gk = s.goalkeeperOnField(gkSide)
            submitOne(EventDraft(type = saveType, side = gkSide, minute = d.minute, extra = d.extra), gk, announce = true)
        }
    }

    /**
     * Arma la fila del evento y la envía por la cola sin conexión:
     * - payload según el tipo: gol → own_goal; penal → result; cambio → player_out_id / player_in_id;
     * - el «actor» de un cambio es quien sale; en el resto, el jugador elegido;
     * - jugador del club → player_id; jugador del rival externo → external_player_id;
     * - el borrador se cierra ANTES de enviar: la pantalla no espera a la red.
     */
    private fun submitOne(d: EventDraft, player: LineupPlayer?, announce: Boolean) {
        val s = _ui.value
        val m = s.match ?: return
        val side = d.side ?: return
        // UUID del celular: garantiza que un reintento no duplique el evento (idempotencia).
        val clientId = UUID.randomUUID().toString()
        val payload: JsonObject = buildJsonObject {
            when (d.type.code) {
                "goal" -> put("own_goal", d.ownGoal)
                "penalty" -> put("result", d.penaltyResult ?: "converted")
                "substitution" -> {
                    put("player_out_id", d.playerOut?.key ?: "")
                    put("player_in_id", player?.key ?: "")
                }
            }
        }
        val actor = if (d.type.code == "substitution") d.playerOut else player
        val body = buildJsonObject {
            put("match_id", matchId)
            put("client_event_id", clientId)
            put("event_type_id", d.type.id)
            put("period", m.period.coerceIn(1, 4))
            put("minute", d.minute)
            put("minute_extra", d.extra)
            put("team_side", side)
            when {
                actor?.playerId != null -> put("player_id", actor.playerId)
                actor?.externalPlayerId != null -> put("external_player_id", actor.externalPlayerId)
            }
            put("payload", payload)
        }
        val minuteLabel = if (d.extra > 0) "${d.minute}+${d.extra}'" else "${d.minute}'"
        val label = listOf(d.type.name, s.sideName(side), actor?.playerName ?: "Sin identificar", minuteLabel).joinToString(" · ")
        _ui.update { it.copy(draft = null) }
        viewModelScope.launch {
            val result = container.queue.submit(
                PendingEvent(clientId, matchId, label, body, System.currentTimeMillis()),
            )
            // Sent: aviso con «Deshacer». Queued: aviso de que se enviará solo. Rejected: motivo del servidor.
            when (result) {
                is SubmitResult.Sent -> {
                    if (announce) _messages.tryEmit(UiMessage(label, undoClientEventId = clientId, undoEventId = result.eventId))
                    refresh()
                }
                SubmitResult.Queued -> _messages.tryEmit(
                    UiMessage("Sin conexión: «$label» guardado en el teléfono; se enviará solo.", undoClientEventId = clientId),
                )
                is SubmitResult.Rejected -> _messages.tryEmit(UiMessage(result.message))
            }
        }
    }

    /** «Deshacer»: si aún está en el celular se descarta; si ya llegó, se ANULA en el servidor (queda en el historial). */
    fun undo(clientEventId: String, eventId: String?) {
        viewModelScope.launch {
            try {
                if (container.queue.removePending(clientEventId)) {
                    _messages.tryEmit(UiMessage("Registro descartado"))
                    return@launch
                }
                val id = eventId ?: container.matches.eventIdByClientId(matchId, clientEventId)
                if (id != null) {
                    container.matches.voidEvent(id, "Deshecho por el operador (registro equivocado)")
                    _messages.tryEmit(UiMessage("Registro anulado (queda en el historial)"))
                    refresh()
                }
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage(e.friendlyMessage()))
            }
        }
    }

    // ---------------- Corrections (US-20) ----------------
    // Corrección: el servidor guarda la versión anterior y exige un motivo (auditoría, US-20).
    fun correct(event: TimelineEvent, patch: JsonObject, reason: String) {
        viewModelScope.launch {
            try {
                container.matches.correctEvent(event.eventId, patch, reason)
                _messages.tryEmit(UiMessage("Evento corregido. Se guardó la versión anterior."))
                refresh()
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage(e.friendlyMessage()))
            }
        }
    }

    fun void(event: TimelineEvent, reason: String) {
        viewModelScope.launch {
            try {
                container.matches.voidEvent(event.eventId, reason)
                _messages.tryEmit(UiMessage("Evento anulado. Sigue visible en la cronología como anulado."))
                refresh()
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage(e.friendlyMessage()))
            }
        }
    }

    companion object {
        // Construye el «patch» de corrección solo con los campos que cambian ("" = quitar el jugador).
        fun patchFor(
            player: LineupPlayer?,
            clearPlayer: Boolean,
            minute: Int?,
            extra: Int?,
            payload: JsonObject?,
        ): JsonObject = buildJsonObject {
            if (clearPlayer) {
                put("player_id", "")
                put("external_player_id", "")
            } else if (player != null) {
                if (player.playerId != null) {
                    put("player_id", player.playerId)
                    put("external_player_id", "")
                } else {
                    put("external_player_id", player.externalPlayerId ?: "")
                    put("player_id", "")
                }
            }
            if (minute != null) put("minute", minute)
            if (extra != null) put("minute_extra", extra)
            if (payload != null) put("payload", payload)
        }

        fun payloadWith(base: JsonObject, key: String, value: String?): JsonObject {
            val map = base.toMutableMap()
            if (value == null) map.remove(key) else map[key] = JsonPrimitive(value)
            return JsonObject(map)
        }

        fun payloadWith(base: JsonObject, key: String, value: Boolean): JsonObject {
            val map = base.toMutableMap()
            map[key] = JsonPrimitive(value)
            return JsonObject(map)
        }
    }
}
