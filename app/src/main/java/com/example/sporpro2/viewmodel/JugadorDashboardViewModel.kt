package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JugadorRecord(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val nombre: String = "",
    @SerialName("posicion_principal") val posicionPrincipal: String = "",
    @SerialName("foto_url") val fotoUrl: String = ""
)

data class JugadorDashboardState(
    val nombre: String = "Jugador",
    val posicionPrincipal: String = "Sin Posición",
    val fotoUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class JugadorDashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(JugadorDashboardState())
    val uiState: StateFlow<JugadorDashboardState> = _uiState.asStateFlow()

    init {
        loadJugadorProfile()
    }

    fun loadJugadorProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                val currentUserId = currentUser?.id ?: ""

                if (currentUserId.isNotEmpty()) {
                    // Consulta a la tabla jugadores de Supabase usando el ID del usuario actual
                    val records = SupabaseClient.client.postgrest["jugadores"]
                        .select { filter { eq("user_id", currentUserId) } }
                        .decodeList<JugadorRecord>()

                    if (records.isNotEmpty()) {
                        val jugador = records.first()
                        _uiState.value = JugadorDashboardState(
                            nombre = jugador.nombre.ifEmpty { 
                                currentUser?.userMetadata?.get("name")?.toString()?.replace("\"", "") ?: "Jugador"
                            },
                            posicionPrincipal = jugador.posicionPrincipal.ifEmpty { "Jugador Principal" },
                            fotoUrl = jugador.fotoUrl,
                            isLoading = false
                        )
                        return@launch
                    }
                }

                // Fallback si no hay registro aún en la tabla jugadores
                val fallbackName = currentUser?.userMetadata?.get("name")?.toString()?.replace("\"", "") ?: "Jugador"
                _uiState.value = JugadorDashboardState(
                    nombre = fallbackName,
                    posicionPrincipal = "Jugador Principal",
                    fotoUrl = "",
                    isLoading = false
                )
            } catch (e: Exception) {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                val fallbackName = currentUser?.userMetadata?.get("name")?.toString()?.replace("\"", "") ?: "Jugador"
                _uiState.value = JugadorDashboardState(
                    nombre = fallbackName,
                    posicionPrincipal = "Jugador Principal",
                    fotoUrl = "",
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
}
