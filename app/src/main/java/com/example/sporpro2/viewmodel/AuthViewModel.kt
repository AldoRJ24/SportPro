package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthViewModel : ViewModel() {
    private val auth = SupabaseClient.client.auth

    private val _sessionStatus = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
    val sessionStatus: StateFlow<SessionStatus> = _sessionStatus.asStateFlow()

    private val _isRegistrationSuccessCheckEmail = MutableStateFlow(false)
    val isRegistrationSuccessCheckEmail: StateFlow<Boolean> = _isRegistrationSuccessCheckEmail.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val userRole: StateFlow<String> = _sessionStatus.map { status ->
        if (status is SessionStatus.Authenticated) {
            // El SDK convierte metadata a JsonObject, extraemos la clave "role"
            val role = status.session.user?.userMetadata?.get("role")?.toString()?.replace("\"", "")
            role ?: "ADM"
        } else {
            ""
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        // Escuchar el estado de la sesión, incluyendo el inicio de sesión desde un Deep Link
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                _sessionStatus.value = status
            }
        }
    }

    fun signUp(email: String, password: String, name: String, role: String) {
        viewModelScope.launch {
            try {
                auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    this.data = buildJsonObject {
                        put("name", name)
                        put("role", role)
                    }
                }
                // Si llegamos aquí, la creación fue exitosa. Como 'Confirm Email' está activado, 
                // el usuario aún no está logueado, debemos decirle que revise su correo.
                _isRegistrationSuccessCheckEmail.value = true
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun resetRegistrationState() {
        _isRegistrationSuccessCheckEmail.value = false
    }
}
