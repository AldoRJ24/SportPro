package com.sportpro.app.ui.fees

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.R
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.FeeStatementRow
import com.sportpro.app.core.data.FeeTeamPlayer
import com.sportpro.app.core.data.FeeTeamSummary
import com.sportpro.app.core.data.friendlyMessage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Mensaje breve para el usuario (snackbar).
 * - [text]: mensaje ya escrito, normalmente el error en español que devolvió el servidor.
 * - [res]: texto fijo de strings.xml.
 * - [plural] + [quantity]: texto con plural («Se generaron 3 cobros»).
 * El ViewModel no conoce el Context, por eso guarda el id del recurso y la pantalla lo traduce.
 */
data class FeeMessage(
    val text: String? = null,
    @StringRes val res: Int? = null,
    @PluralsRes val plural: Int? = null,
    val quantity: Int = 0,
)

// ---------------------------------------------------------------------
// 1) Lista de equipos que gestiono (admin o DT)
// ---------------------------------------------------------------------

data class FeesOverviewUi(
    val loading: Boolean = true,
    val error: String? = null,
    val teams: List<FeeTeamSummary> = emptyList(),
)

class FeesOverviewViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(FeesOverviewUi())
    val ui: StateFlow<FeesOverviewUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val teams = container.fees.summaryForMe()
                _ui.update { it.copy(loading = false, teams = teams) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }
}

// ---------------------------------------------------------------------
// 2) Un equipo: plan, selección de jugadores, generar cobros y recordatorios
// ---------------------------------------------------------------------

data class TeamFeesUi(
    val loading: Boolean = true,
    val error: String? = null,
    val summary: FeeTeamSummary? = null,
    val players: List<FeeTeamPlayer> = emptyList(),
    /** true mientras se envía una acción: desactiva los botones para no enviarla dos veces. */
    val busy: Boolean = false,
    val message: FeeMessage? = null,
)

class TeamFeesViewModel(private val container: AppContainer, private val teamId: String) : ViewModel() {
    private val _ui = MutableStateFlow(TeamFeesUi())
    val ui: StateFlow<TeamFeesUi> = _ui.asStateFlow()

    init { load() }

    /** [silent] = recarga sin mostrar el indicador de carga (después de una acción). */
    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _ui.update { it.copy(loading = true, error = null) }
            try {
                // El resumen trae el plan y los totales; la lista trae a cada jugador.
                val summary = container.fees.summaryForMe().firstOrNull { it.teamId == teamId }
                val players = container.fees.teamPlayers(teamId)
                _ui.update { it.copy(loading = false, error = null, summary = summary, players = players) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Ejecuta una acción del servidor, muestra el resultado y recarga los datos. */
    private fun act(block: suspend () -> FeeMessage) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val message = try { block() } catch (e: Exception) { FeeMessage(text = e.friendlyMessage()) }
            _ui.update { it.copy(busy = false, message = message) }
            load(silent = true)
        }
    }

    fun setPlan(amount: Double, dueDay: Int) = act {
        container.fees.setPlan(teamId, amount, dueDay)
        FeeMessage(res = R.string.fees_plan_saved)
    }

    /**
     * Marca o desmarca a un jugador. Se cambia primero en pantalla (respuesta inmediata) y,
     * si el servidor lo rechaza, la recarga posterior devuelve el interruptor a su sitio.
     */
    fun setPlayer(player: FeeTeamPlayer, enabled: Boolean) {
        _ui.update { s -> s.copy(players = s.players.map { if (it.playerId == player.playerId) it.copy(enabled = enabled) else it }) }
        viewModelScope.launch {
            try {
                container.fees.setPlayer(teamId, player.playerId, enabled)
            } catch (e: Exception) {
                _ui.update { it.copy(message = FeeMessage(text = e.friendlyMessage())) }
            }
            load(silent = true)
        }
    }

    fun generate() = act {
        val created = container.fees.generateCharges(teamId)
        if (created == 0) FeeMessage(res = R.string.fees_generated_none)
        else FeeMessage(plural = R.plurals.fees_generated, quantity = created)
    }

    fun remind() = act {
        val sent = container.fees.sendReminders(teamId)
        if (sent == 0) FeeMessage(res = R.string.fees_reminders_none)
        else FeeMessage(plural = R.plurals.fees_reminders_sent, quantity = sent)
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
}

// ---------------------------------------------------------------------
// 3) Estado de cuenta (lo usa quien paga y también el DT / admin)
// ---------------------------------------------------------------------

data class FeeStatementUi(
    val loading: Boolean = true,
    val error: String? = null,
    val rows: List<FeeStatementRow> = emptyList(),
    val busy: Boolean = false,
    val message: FeeMessage? = null,
) {
    /** Suma de lo pendiente y vencido. */
    val totalDue: Double get() = rows.filter { it.isOpen }.sumOf { it.amount }
}

/**
 * [teamId] y [playerId] nulos = «mis mensualidades» (lo que yo pago).
 * Con valores = vista del DT o admin de ese equipo o jugador.
 * Se recarga sola al llegar una notificación: así el apoderado ve «Pago confirmado» sin
 * salir de la pantalla, y el admin ve aparecer el pago que le acaban de informar.
 */
@OptIn(FlowPreview::class)
class FeeStatementViewModel(
    private val container: AppContainer,
    private val teamId: String?,
    private val playerId: String?,
) : ViewModel() {
    private val _ui = MutableStateFlow(FeeStatementUi())
    val ui: StateFlow<FeeStatementUi> = _ui.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            container.account.observeNotifications().debounce(300).catch { }.collect { load(silent = true) }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _ui.update { it.copy(loading = true, error = null) }
            try {
                val rows = container.fees.statement(teamId, playerId)
                _ui.update { it.copy(loading = false, error = null, rows = rows) }
            } catch (e: Exception) {
                // En una recarga silenciosa no se tapa la lista que ya se ve con un error
                if (!silent) _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    private fun act(@StringRes done: Int, block: suspend () -> Unit) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val message = try {
                block()
                FeeMessage(res = done)
            } catch (e: Exception) {
                FeeMessage(text = e.friendlyMessage())
            }
            _ui.update { it.copy(busy = false, message = message) }
            load(silent = true)
        }
    }

    fun report(row: FeeStatementRow, method: String, reference: String, note: String) =
        act(R.string.fees_done_reported) { container.fees.reportPayment(row.chargeId, method, reference, note) }

    fun register(row: FeeStatementRow, method: String, reference: String, note: String) =
        act(R.string.fees_done_registered) { container.fees.registerPayment(row.chargeId, method, reference, note) }

    fun confirm(row: FeeStatementRow) {
        val id = row.paymentId ?: return
        act(R.string.fees_done_confirmed) { container.fees.decidePayment(id, accept = true, reason = null) }
    }

    fun reject(row: FeeStatementRow, reason: String) {
        val id = row.paymentId ?: return
        act(R.string.fees_done_rejected) { container.fees.decidePayment(id, accept = false, reason = reason) }
    }

    fun void(row: FeeStatementRow, reason: String) {
        val id = row.paymentId ?: return
        act(R.string.fees_done_voided) { container.fees.voidPayment(id, reason) }
    }

    fun waive(row: FeeStatementRow, reason: String) =
        act(R.string.fees_done_waived) { container.fees.waiveCharge(row.chargeId, reason) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
}
