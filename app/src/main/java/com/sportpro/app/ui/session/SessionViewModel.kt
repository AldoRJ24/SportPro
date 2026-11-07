package com.sportpro.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.UserContext
import com.sportpro.app.core.data.friendlyMessage
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String) : SessionState
}

/**
 * Sesión de toda la app + contexto del usuario (perfil, roles, academias, hijos, estado de cuenta).
 * Los roles SIEMPRE vienen del servidor (tablas con RLS), nunca de campos editables en el celular.
 * Al iniciar sesión se carga el contexto; al cerrarla se limpia.
 */
class SessionViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<SessionState> = container.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> SessionState.SignedIn(status.session.user?.id ?: "")
                is SessionStatus.NotAuthenticated -> SessionState.SignedOut
                else -> SessionState.Loading
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    private val _context = MutableStateFlow<UserContext?>(null)
    val context: StateFlow<UserContext?> = _context.asStateFlow()

    private val _contextError = MutableStateFlow<String?>(null)
    val contextError: StateFlow<String?> = _contextError.asStateFlow()

    init {
        viewModelScope.launch {
            state.collect { s ->
                if (s is SessionState.SignedIn) {
                    refreshContext()
                } else {
                    _context.value = null
                }
            }
        }
    }

    // Recarga el contexto (tras vincular apoderado, aceptar una invitación, editar el perfil, etc.).
    fun refreshContext() {
        viewModelScope.launch {
            _contextError.value = null
            try {
                _context.value = container.club.loadContext()
            } catch (e: Exception) {
                _contextError.value = e.friendlyMessage()
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { runCatching { container.auth.signOut() } }
    }
}
