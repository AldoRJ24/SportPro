package com.example.sporpro2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Jugador
import com.example.sporpro2.model.Padre
import com.example.sporpro2.model.Usuario
import com.example.sporpro2.model.VinculoFamiliar
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
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

    private val _isBlockedMinor = MutableStateFlow(false)
    val isBlockedMinor: StateFlow<Boolean> = _isBlockedMinor.asStateFlow()

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
                if (status is SessionStatus.Authenticated) {
                    verificarBloqueoMinor(status.session.user)
                } else {
                    _isBlockedMinor.value = false
                }
            }
        }
    }

    suspend fun validarYVincularPadre(codigoIngresado: String, jugadorUserId: String? = null): Boolean {
        try {
            val client = SupabaseClient.client
            val codigoClean = codigoIngresado.trim().replace("#", "")
            
            if (codigoClean.isBlank()) {
                _errorMessage.value = "Por favor ingresa un código de apoderado válido."
                return false
            }

            // Paso 1 (Validar): SELECT id FROM padres WHERE codigo_apoderado = codigoIngresado
            val todosPadres = client.postgrest["padres"].select().decodeList<Padre>()
            val padreEncontrado = todosPadres.firstOrNull { p ->
                val c = p.codigoApoderado?.trim()?.replace("#", "") ?: ""
                c == codigoClean
            }

            // Paso 2 (Error): Si la consulta devuelve nulo o vacía, emitir error
            if (padreEncontrado == null) {
                _errorMessage.value = "El código ingresado no existe. Por favor, verifica con tu apoderado."
                return false
            }

            // Paso 3 (Éxito): Si la consulta encuentra un ID, hacer INSERT INTO vinculos_familiares
            val targetUserId = jugadorUserId ?: client.auth.currentUserOrNull()?.id
            if (targetUserId != null) {
                val vinculosExistentes = client.postgrest["vinculos_familiares"].select {
                    filter { eq("jugador_id", targetUserId) }
                }.decodeList<VinculoFamiliar>()

                if (vinculosExistentes.isEmpty()) {
                    client.postgrest["vinculos_familiares"].insert(
                        VinculoFamiliar(
                            jugadorId = targetUserId,
                            padreId = padreEncontrado.usuarioId,
                            estado = "Pendiente"
                        )
                    )
                }
            }

            // Paso 4 (Feedback): Cambia el estado a "Solicitud Enviada" (bloqueado esperando aprobación)
            _isBlockedMinor.value = true
            return true

        } catch (e: Exception) {
            Log.e("SupabaseError", "Error validando apoderado", e)
            _errorMessage.value = "El código ingresado no existe. Por favor, verifica con tu apoderado."
            return false
        }
    }

    private suspend fun verificarBloqueoMinor(user: UserInfo?) {
        if (user == null) return
        val role = user.userMetadata?.get("role")?.toString()?.replace("\"", "")
        val edadStr = user.userMetadata?.get("edad")?.toString()?.replace("\"", "")?.toIntOrNull()
        val name = user.userMetadata?.get("name")?.toString()?.replace("\"", "") ?: "Jugador Menor"
        
        if (role == "JUG" && edadStr != null && edadStr < 18) {
            try {
                val codigoRaw = user.userMetadata?.get("codigo_apoderado")?.toString()?.replace("\"", "") ?: ""
                val codigoClean = codigoRaw.trim().replace("#", "")
                val client = SupabaseClient.client
                
                // Aseguramos que exista registro en la tabla jugadores
                val jugadores = client.postgrest["jugadores"].select {
                    filter { eq("usuario_id", user.id) }
                }.decodeList<Jugador>()
                
                if (jugadores.isEmpty()) {
                    client.postgrest["jugadores"].insert(
                        Jugador(
                            usuarioId = user.id,
                            nombre = name,
                            codigoApoderado = codigoClean,
                            estado = "Activo"
                        )
                    )
                }
                
                val vinculos = client.postgrest["vinculos_familiares"].select { 
                    filter { eq("jugador_id", user.id) } 
                }.decodeList<VinculoFamiliar>()
                
                if (vinculos.isEmpty()) {
                    validarYVincularPadre(codigoClean, user.id)
                } else {
                    // Si ya existe vínculo, está bloqueado si NO está "Aceptado"
                    _isBlockedMinor.value = vinculos.first().estado != "Aceptado"
                }
            } catch (e: Exception) {
                // En caso de error de red, bloqueamos por seguridad
                _isBlockedMinor.value = true 
            }
        } else {
            _isBlockedMinor.value = false
        }
    }

    fun signUp(email: String, password: String, name: String, role: String, edad: String = "", codigoApoderado: String = "") {
        viewModelScope.launch {
            try {
                var idPadreEncontrado: String? = null
                val esMenor = role == "JUG" && (edad.toIntOrNull() ?: 18) < 18
                
                // Si es menor de edad, validamos primero que el código de apoderado exista en padres
                if (esMenor) {
                    if (codigoApoderado.isBlank()) {
                        _errorMessage.value = "Ingresa el código de tu apoderado."
                        return@launch
                    }
                    val codigoClean = codigoApoderado.trim().replace("#", "")
                    val client = SupabaseClient.client
                    val todosPadres = client.postgrest["padres"].select().decodeList<Padre>()
                    val padreMatch = todosPadres.firstOrNull { p ->
                        val c = p.codigoApoderado?.trim()?.replace("#", "") ?: ""
                        c == codigoClean
                    }
                    
                    if (padreMatch == null) {
                        _errorMessage.value = "El código ingresado no existe. Por favor, verifica con tu apoderado."
                        return@launch
                    }
                    idPadreEncontrado = padreMatch.usuarioId
                }

                // 1. Crear usuario en Supabase Auth
                val userInfo = auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    this.data = buildJsonObject {
                        put("name", name)
                        put("role", role)
                        put("edad", edad)
                        put("codigo_apoderado", codigoApoderado)
                    }
                }

                val currentUserId = userInfo?.id ?: auth.currentUserOrNull()?.id

                // Inserción secuencial en las tablas relacionales para evitar FK violations
                if (currentUserId != null) {
                    val client = SupabaseClient.client

                    if (role == "JUG") {
                        // Paso 1: INSERT INTO usuarios (id, nombre_completo, correo, rol)
                        try {
                            client.postgrest["usuarios"].insert(
                                Usuario(
                                    id = currentUserId,
                                    nombreCompleto = name,
                                    correo = email,
                                    rol = "JUG"
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("SupabaseError", "Error insertando en usuarios (JUG)", e)
                        }

                        // Paso 2: INSERT INTO jugadores (usuario_id)
                        try {
                            client.postgrest["jugadores"].insert(
                                Jugador(
                                    usuarioId = currentUserId,
                                    nombre = name,
                                    codigoApoderado = codigoApoderado.trim().replace("#", ""),
                                    estado = "Activo"
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("SupabaseError", "Error insertando en jugadores", e)
                        }

                        // Paso 3 y 4: Si es menor de edad, INSERT INTO vinculos_familiares
                        if (esMenor && idPadreEncontrado != null) {
                            try {
                                client.postgrest["vinculos_familiares"].insert(
                                    VinculoFamiliar(
                                        jugadorId = currentUserId,
                                        padreId = idPadreEncontrado,
                                        estado = "Pendiente"
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("SupabaseError", "Error insertando en vinculos_familiares", e)
                            }
                        }

                    } else if (role == "PAD") {
                        // Paso 1: INSERT INTO usuarios (id, nombre_completo, correo, rol)
                        try {
                            client.postgrest["usuarios"].insert(
                                Usuario(
                                    id = currentUserId,
                                    nombreCompleto = name,
                                    correo = email,
                                    rol = "PAD"
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("SupabaseError", "Error insertando en usuarios (PAD)", e)
                        }

                        // Paso 2: INSERT INTO padres (usuario_id)
                        try {
                            client.postgrest["padres"].insert(
                                Padre(usuarioId = currentUserId)
                            )
                        } catch (e: Exception) {
                            Log.e("SupabaseError", "Error insertando en padres", e)
                        }
                    }
                }

                _isRegistrationSuccessCheckEmail.value = true
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error general en registro", e)
                _errorMessage.value = e.message ?: "Error al registrarse"
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

    fun logout() {
        viewModelScope.launch {
            try {
                auth.signOut()
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
