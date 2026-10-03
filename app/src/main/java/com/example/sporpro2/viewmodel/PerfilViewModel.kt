package com.example.sporpro2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Jugador
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PerfilState {
    data object Loading : PerfilState
    data class Success(val jugador: Jugador?) : PerfilState
    data class Saved(val jugador: Jugador) : PerfilState
    data class Error(val message: String) : PerfilState
}

class PerfilViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<PerfilState>(PerfilState.Loading)
    val uiState: StateFlow<PerfilState> = _uiState.asStateFlow()

    init { cargarPerfil() }

    fun cargarPerfil() {
        viewModelScope.launch {
            _uiState.value = PerfilState.Loading
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user == null) {
                    _uiState.value = PerfilState.Error("No hay sesión activa")
                    return@launch
                }
                
                val jugadores = SupabaseClient.client.postgrest["jugadores"]
                    .select {
                        filter { eq("usuario_id", user.id) }
                    }
                    .decodeList<Jugador>()
                
                // Si no existe el perfil, enviamos null para que la UI muestre el formulario vacío
                _uiState.value = PerfilState.Success(jugadores.firstOrNull())
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error al cargar perfil", e)
                _uiState.value = PerfilState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun guardarPerfil(posicion: String, peso: Double, estatura: Double, pieDominante: String) {
        viewModelScope.launch {
            _uiState.value = PerfilState.Loading
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user == null) {
                    _uiState.value = PerfilState.Error("No hay sesión activa")
                    return@launch
                }

                val existentes = SupabaseClient.client.postgrest["jugadores"]
                    .select { filter { eq("usuario_id", user.id) } }
                    .decodeList<Jugador>()

                if (existentes.isNotEmpty()) {
                    val existente = existentes.first()
                    val perfilActualizar = existente.copy(
                        posicionPrincipal = posicion,
                        pesoKg = peso,
                        estaturaCm = estatura,
                        pieDominante = pieDominante,
                        estado = "Activo"
                    )
                    SupabaseClient.client.postgrest["jugadores"]
                        .update(perfilActualizar) {
                            filter { eq("usuario_id", user.id) }
                        }
                } else {
                    val nuevoPerfil = Jugador(
                        usuarioId = user.id,
                        posicionPrincipal = posicion,
                        pesoKg = peso,
                        estaturaCm = estatura,
                        pieDominante = pieDominante,
                        estado = "Activo"
                    )
                    SupabaseClient.client.postgrest["jugadores"].insert(nuevoPerfil)
                }

                val actualizado = SupabaseClient.client.postgrest["jugadores"]
                    .select { filter { eq("usuario_id", user.id) } }
                    .decodeSingle<Jugador>()
                
                _uiState.value = PerfilState.Saved(actualizado)
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error al guardar perfil", e)
                _uiState.value = PerfilState.Error(e.message ?: "Error al guardar el perfil")
            }
        }
    }

    fun subirAvatar(byteArray: ByteArray) {
        viewModelScope.launch {
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull() ?: return@launch
                val bucket = SupabaseClient.client.storage["avatars"]
                
                bucket.upload("${user.id}.jpg", byteArray) {
                    upsert = true
                }
                val url = bucket.publicUrl("${user.id}.jpg")
                
                val existentes = SupabaseClient.client.postgrest["jugadores"]
                    .select { filter { eq("usuario_id", user.id) } }
                    .decodeList<Jugador>()
                
                if (existentes.isNotEmpty()) {
                    val existente = existentes.first()
                    SupabaseClient.client.postgrest["jugadores"]
                        .update(existente.copy(avatarUrl = url)) {
                            filter { eq("usuario_id", user.id) }
                        }
                } else {
                    val nuevoPerfil = Jugador(
                        usuarioId = user.id,
                        avatarUrl = url,
                        estado = "Activo"
                    )
                    SupabaseClient.client.postgrest["jugadores"].insert(nuevoPerfil)
                }
                
                cargarPerfil()
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error al subir avatar", e)
            }
        }
    }
}
