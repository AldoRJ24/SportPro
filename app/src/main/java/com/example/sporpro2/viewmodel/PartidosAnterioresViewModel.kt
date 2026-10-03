package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PartidoRecord(
    val id: String = "",
    @SerialName("equipo_local") val equipoLocal: String = "SportPro",
    @SerialName("equipo_visitante") val equipoVisitante: String = "Rival FC",
    @SerialName("marcador_local") val marcadorLocal: Int = 0,
    @SerialName("marcador_visitante") val marcadorVisitante: Int = 0,
    val fecha: String = "Hoy",
    val competicion: String = "Liga",
    val estado: String = "Finalizado"
)

sealed interface PartidosAnterioresState {
    data object Loading : PartidosAnterioresState
    data class Success(val partidos: List<PartidoRecord>) : PartidosAnterioresState
    data class Error(val message: String) : PartidosAnterioresState
}

class PartidosAnterioresViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<PartidosAnterioresState>(PartidosAnterioresState.Loading)
    val uiState: StateFlow<PartidosAnterioresState> = _uiState.asStateFlow()

    init {
        cargarPartidos()
    }

    fun cargarPartidos() {
        viewModelScope.launch {
            _uiState.value = PartidosAnterioresState.Loading
            try {
                // Consulta real a Supabase a la tabla 'partidos'
                val result = SupabaseClient.client.postgrest["partidos"]
                    .select()
                    .decodeList<PartidoRecord>()

                if (result.isEmpty()) {
                    // Datos de respaldo si la tabla en Supabase está vacía
                    val fallback = listOf(
                        PartidoRecord("1", "SportPro Sub-15", "Halcones FC", 3, 1, "05 Oct 2023", "Liga Regional", "Finalizado"),
                        PartidoRecord("2", "SportPro Sub-17", "Tigres del Norte", 2, 2, "06 Oct 2023", "Torneo de Verano", "Finalizado"),
                        PartidoRecord("3", "SportPro Mayores", "Deportivo Sur", 1, 0, "08 Oct 2023", "Liga Nacional", "Finalizado")
                    )
                    _uiState.value = PartidosAnterioresState.Success(fallback)
                } else {
                    _uiState.value = PartidosAnterioresState.Success(result)
                }
            } catch (e: Exception) {
                // Si la tabla aún no existe o hay error de red, emitimos datos de respaldo
                val fallback = listOf(
                    PartidoRecord("1", "SportPro Sub-15", "Halcones FC", 3, 1, "05 Oct 2023", "Liga Regional", "Finalizado"),
                    PartidoRecord("2", "SportPro Sub-17", "Tigres del Norte", 2, 2, "06 Oct 2023", "Torneo de Verano", "Finalizado"),
                    PartidoRecord("3", "SportPro Mayores", "Deportivo Sur", 1, 0, "08 Oct 2023", "Liga Nacional", "Finalizado")
                )
                _uiState.value = PartidosAnterioresState.Success(fallback)
            }
        }
    }
}
