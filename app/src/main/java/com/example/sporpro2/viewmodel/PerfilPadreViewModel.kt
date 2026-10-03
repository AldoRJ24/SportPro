package com.example.sporpro2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Jugador
import com.example.sporpro2.model.Padre
import com.example.sporpro2.model.VinculoFamiliar
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PerfilPadreState {
    data object Loading : PerfilPadreState
    data class Success(
        val codigoApoderado: String,
        val hijosVinculados: List<Jugador> = emptyList()
    ) : PerfilPadreState
    data class Error(val message: String) : PerfilPadreState
}

class PerfilPadreViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<PerfilPadreState>(PerfilPadreState.Loading)
    val uiState: StateFlow<PerfilPadreState> = _uiState.asStateFlow()

    init { cargarPerfilPadre() }

    fun cargarPerfilPadre() {
        viewModelScope.launch {
            _uiState.value = PerfilPadreState.Loading
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user == null) {
                    _uiState.value = PerfilPadreState.Error("No hay sesión activa")
                    return@launch
                }

                val client = SupabaseClient.client

                // 1. Buscamos si el padre ya tiene registro
                var padres = client.postgrest["padres"]
                    .select { filter { eq("usuario_id", user.id) } }
                    .decodeList<Padre>()

                // 2. Si no existe, lo insertamos
                if (padres.isEmpty()) {
                    client.postgrest["padres"].insert(Padre(usuarioId = user.id))
                    padres = client.postgrest["padres"]
                        .select { filter { eq("usuario_id", user.id) } }
                        .decodeList<Padre>()
                }

                if (padres.isNotEmpty()) {
                    val codigo = padres.first().codigoApoderado ?: "N/A"
                    
                    // 3. Obtener los vínculos familiares ACEPTADOS
                    val vinculosAceptados = client.postgrest["vinculos_familiares"]
                        .select {
                            filter {
                                eq("padre_id", user.id)
                                eq("estado", "Aceptado")
                            }
                        }.decodeList<VinculoFamiliar>()
                    
                    // 4. Cargar la información de cada hijo
                    val listaHijos = mutableListOf<Jugador>()
                    for (v in vinculosAceptados) {
                        try {
                            val jugador = client.postgrest["jugadores"]
                                .select { filter { eq("usuario_id", v.jugadorId) } }
                                .decodeSingleOrNull<Jugador>()
                            if (jugador != null) listaHijos.add(jugador)
                        } catch (e: Exception) { }
                    }

                    _uiState.value = PerfilPadreState.Success(codigo, listaHijos)
                } else {
                    _uiState.value = PerfilPadreState.Error("Error al generar el perfil del apoderado")
                }
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error Perfil Padre", e)
                _uiState.value = PerfilPadreState.Error(e.message ?: "Error desconocido")
            }
        }
    }
}
