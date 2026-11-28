package com.sportpro.app.ui.profile
import com.sportpro.app.R
import com.sportpro.app.core.AppText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.Callup
import com.sportpro.app.core.data.GuardianRequest
import com.sportpro.app.core.data.NotificationRow
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.core.data.UserContext
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.core.data.parseInstant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FamilyUi(
    val loading: Boolean = false,
    val error: String? = null,
    val memberships: List<TeamPlayerRow> = emptyList(),
    val callups: List<Callup> = emptyList(),
    val guardianCode: String? = null,
    val requests: List<GuardianRequest> = emptyList(),
    val notifications: List<NotificationRow> = emptyList(),
    val message: String? = null,
) {
    /**
     * Convocatorias por responder: pendientes, de un partido programado y que TODAVÍA no empezó.
     * Antes no se miraba la fecha y seguían apareciendo «Confirmar / No asistirá» de partidos ya
     * pasados que nunca se iniciaron en la app.
     */
    val pendingCallups: List<Callup> get() {
        val now = java.time.Instant.now()
        return callups.filter {
            it.status == "pending" && it.match?.status == "scheduled" &&
                (parseInstant(it.match.scheduledAt)?.isAfter(now) ?: true)
        }
    }
    val unread: Int get() = notifications.count { it.isUnread }
}

/** «Mis hijos» (código familiar + solicitudes), convocatorias (US-04) y notificaciones. */
class FamilyViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(FamilyUi())
    val ui: StateFlow<FamilyUi> = _ui.asStateFlow()
    private var lastCtx: UserContext? = null
    private var watcher: Job? = null

    fun load(ctx: UserContext?) {
        if (ctx == null) return
        lastCtx = ctx
        val ids = ctx.children.map { it.id } + listOfNotNull(ctx.ownPlayer?.id)
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val teams = container.club.teamsOfPlayers(ids)
                val callups = container.club.callupsForPlayers(ids)
                val code = if (ctx.isGuardianAccount) runCatching { container.account.myGuardianCode() }.getOrNull() else null
                val requests = if (ctx.isGuardianAccount) runCatching { container.account.guardianRequests() }.getOrDefault(emptyList()) else emptyList()
                val notifications = runCatching { container.account.notifications() }.getOrDefault(emptyList())
                _ui.update {
                    it.copy(
                        loading = false, memberships = teams, callups = callups,
                        guardianCode = code, requests = requests, notifications = notifications,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
        if (watcher == null) {
            watcher = viewModelScope.launch {
                container.account.observeNotifications().catch { }.collect { lastCtx?.let { c -> reloadLight(c) } }
            }
        }
    }

    /** Llegó una solicitud o notificación nueva en tiempo real. */
    private fun reloadLight(ctx: UserContext) {
        viewModelScope.launch {
            val requests = if (ctx.isGuardianAccount) runCatching { container.account.guardianRequests() }.getOrDefault(_ui.value.requests) else emptyList()
            val notifications = runCatching { container.account.notifications() }.getOrDefault(_ui.value.notifications)
            // También los equipos: si el DT acaba de aceptar una solicitud, el equipo nuevo debe
            // aparecer en el perfil sin cerrar la app (si falla la red, se deja lo que ya había).
            val ids = ctx.children.map { it.id } + listOfNotNull(ctx.ownPlayer?.id)
            val teams = runCatching { container.club.teamsOfPlayers(ids) }.getOrDefault(_ui.value.memberships)
            _ui.update { it.copy(requests = requests, notifications = notifications, memberships = teams) }
        }
    }

    fun respond(callup: Callup, accept: Boolean, ctx: UserContext?) {
        viewModelScope.launch {
            try {
                container.club.respondCallup(callup.id, accept)
                // La tarjeta desaparece al instante (sin esperar la recarga): ya no está «pendiente»
                val newStatus = if (accept) "confirmed" else "declined"
                _ui.update { s ->
                    s.copy(
                        callups = s.callups.map { if (it.id == callup.id) it.copy(status = newStatus) else it },
                        message = if (accept) AppText.get(R.string.profile_asistencia_confirmada) else AppText.get(R.string.profile_convocatoria_rechazada),
                    )
                }
                load(ctx)
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.friendlyMessage()) }
            }
        }
    }

    /** El apoderado acepta o rechaza la solicitud de vínculo de un menor. */
    fun respondLink(request: GuardianRequest, accept: Boolean, onApproved: () -> Unit) {
        viewModelScope.launch {
            try {
                container.account.respondGuardianLink(request.linkId, accept)
                _ui.update {
                    it.copy(
                        requests = it.requests.filterNot { r -> r.linkId == request.linkId },
                        message = if (accept) AppText.get(R.string.profile_x_ahora_esta_vinculado_a, request.playerName) else AppText.get(R.string.profile_solicitud_rechazada),
                    )
                }
                if (accept) onApproved()
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.friendlyMessage()) }
            }
        }
    }

    /**
     * Marca TODO como leído con una sola llamada al servidor (también las que no están cargadas
     * en pantalla, porque la lista solo trae las 50 más recientes).
     */
    fun markAllRead() {
        if (_ui.value.notifications.none { it.isUnread }) return
        viewModelScope.launch {
            runCatching { container.account.markAllRead() }
            _ui.update { s -> s.copy(notifications = s.notifications.map { if (it.isUnread) it.copy(readAt = "now") else it }) }
        }
    }

    /** Marca UNA notificación como leída (al tocarla). El punto verde desaparece al instante. */
    fun markRead(id: String) {
        if (_ui.value.notifications.none { it.id == id && it.isUnread }) return
        _ui.update { s -> s.copy(notifications = s.notifications.map { if (it.id == id) it.copy(readAt = "now") else it }) }
        viewModelScope.launch { runCatching { container.account.markRead(listOf(id)) } }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
}
