package com.sportpro.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.Callup
import com.sportpro.app.core.data.GuardianRequest
import com.sportpro.app.core.data.NotificationRow
import com.sportpro.app.core.data.TeamPlayerRow
import com.sportpro.app.core.data.UserContext
import com.sportpro.app.core.data.friendlyMessage
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
    val pendingCallups: List<Callup> get() = callups.filter { it.status == "pending" && it.match?.status == "scheduled" }
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

    /** New request/notification arrived in real time. */
    private fun reloadLight(ctx: UserContext) {
        viewModelScope.launch {
            val requests = if (ctx.isGuardianAccount) runCatching { container.account.guardianRequests() }.getOrDefault(_ui.value.requests) else emptyList()
            val notifications = runCatching { container.account.notifications() }.getOrDefault(_ui.value.notifications)
            _ui.update { it.copy(requests = requests, notifications = notifications) }
        }
    }

    fun respond(callup: Callup, accept: Boolean, ctx: UserContext?) {
        viewModelScope.launch {
            try {
                container.club.respondCallup(callup.id, accept)
                _ui.update { it.copy(message = if (accept) "Asistencia confirmada" else "Convocatoria rechazada") }
                load(ctx)
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.friendlyMessage()) }
            }
        }
    }

    /** Guardian accepts or rejects a minor's link request. */
    fun respondLink(request: GuardianRequest, accept: Boolean, onApproved: () -> Unit) {
        viewModelScope.launch {
            try {
                container.account.respondGuardianLink(request.linkId, accept)
                _ui.update {
                    it.copy(
                        requests = it.requests.filterNot { r -> r.linkId == request.linkId },
                        message = if (accept) "${request.playerName} ahora está vinculado a ti" else "Solicitud rechazada",
                    )
                }
                if (accept) onApproved()
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.friendlyMessage()) }
            }
        }
    }

    fun markAllRead() {
        val ids = _ui.value.notifications.filter { it.isUnread }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { container.account.markRead(ids) }
            _ui.update { s -> s.copy(notifications = s.notifications.map { if (it.id in ids) it.copy(readAt = "now") else it }) }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
}
