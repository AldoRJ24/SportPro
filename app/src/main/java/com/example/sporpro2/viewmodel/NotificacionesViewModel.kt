package com.example.sporpro2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Jugador
import com.example.sporpro2.model.Padre
import com.example.sporpro2.model.Usuario
import com.example.sporpro2.model.VinculoFamiliar
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SolicitudUI(val id: String, val jugadorNombre: String)

sealed interface NotificacionesState {
    data object Loading : NotificacionesState
    data class Success(val solicitudes: List<SolicitudUI>) : NotificacionesState
    data class Error(val message: String) : NotificacionesState
}

class NotificacionesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<NotificacionesState>(NotificacionesState.Loading)
    val uiState: StateFlow<NotificacionesState> = _uiState.asStateFlow()

    init { fetchSolicitudes() }

    fun fetchSolicitudes() {
        viewModelScope.launch {
            _uiState.value = NotificacionesState.Loading
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user == null) {
                    _uiState.value = NotificacionesState.Error("Sesión no iniciada")
                    return@launch
                }
                
                val client = SupabaseClient.client
                
                // 1. Obtener perfil de apoderado del usuario actual
                val padres = client.postgrest["padres"].select {
                    filter { eq("usuario_id", user.id) }
                }.decodeList<Padre>()
                
                if (padres.isNotEmpty()) {
                    val miCodigoRaw = padres.first().codigoApoderado ?: ""
                    val miCodigoClean = miCodigoRaw.trim().replace("#", "")
                    
                    if (miCodigoClean.isNotBlank()) {
                        // 2. Buscar si hay jugadores registrados con este código de apoderado
                        val todosJugadores = client.postgrest["jugadores"].select().decodeList<Jugador>()
                        val jugadoresCoincidentes = todosJugadores.filter { j ->
                            j.codigoApoderado?.trim()?.replace("#", "") == miCodigoClean
                        }
                        
                        // 3. Crear vínculos pendientes para cualquier jugador que no lo tenga aún
                        for (j in jugadoresCoincidentes) {
                            val uId = j.usuarioId ?: continue
                            val vinculosExistentes = client.postgrest["vinculos_familiares"].select {
                                filter { eq("jugador_id", uId) }
                            }.decodeList<VinculoFamiliar>()
                            
                            if (vinculosExistentes.isEmpty()) {
                                client.postgrest["vinculos_familiares"].insert(
                                    VinculoFamiliar(
                                        jugadorId = uId,
                                        padreId = user.id,
                                        estado = "Pendiente"
                                    )
                                )
                            }
                        }
                    }
                }
                
                // 4. Consultar solicitudes pendientes para este apoderado (padre_id, estado)
                val vinculosPendientes = client.postgrest["vinculos_familiares"].select {
                    filter { 
                        eq("padre_id", user.id)
                        eq("estado", "Pendiente") 
                    }
                }.decodeList<VinculoFamiliar>()
                
                val solicitudes = vinculosPendientes.map { v ->
                    var nombreJugador = "Jugador en espera (${v.jugadorId.take(5)})"
                    try {
                        val usuario = client.postgrest["usuarios"].select { 
                            filter { eq("id", v.jugadorId) } 
                        }.decodeSingleOrNull<Usuario>()
                        
                        if (usuario?.nombreCompleto != null) {
                            nombreJugador = usuario.nombreCompleto
                        }
                    } catch (e: Exception) { }
                    SolicitudUI(v.id ?: "", nombreJugador)
                }
                
                _uiState.value = NotificacionesState.Success(solicitudes)
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error al cargar notificaciones", e)
                _uiState.value = NotificacionesState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun aceptarVinculo(vinculoId: String) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.postgrest["vinculos_familiares"]
                    .update({ set("estado", "Aceptado") }) {
                        filter { eq("id", vinculoId) }
                    }
                // Recarga la lista de notificaciones después de actualizar
                fetchSolicitudes()
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error al aceptar vínculo", e)
            }
        }
    }
}
