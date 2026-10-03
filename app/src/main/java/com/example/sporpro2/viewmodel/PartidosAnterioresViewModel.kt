package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PartidoRecord(
    val id: String,
    val equipoLocal: String,
    val equipoVisitante: String,
    val marcadorLocal: Int,
    val marcadorVisitante: Int,
    val fecha: String,
    val competicion: String,
    val estado: String
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

    private fun cargarPartidos() {
        viewModelScope.launch {
            _uiState.value = PartidosAnterioresState.Loading
            try {
                // Simulación de carga desde base de datos
                kotlinx.coroutines.delay(800)
                val mockPartidos = listOf(
                    PartidoRecord("1", "SportPro Sub-15", "Halcones FC", 3, 1, "05 Oct 2023", "Liga Regional", "Finalizado"),
                    PartidoRecord("2", "SportPro Sub-17", "Tigres del Norte", 2, 2, "06 Oct 2023", "Torneo de Verano", "Finalizado"),
                    PartidoRecord("3", "SportPro Mayores", "Deportivo Sur", 1, 0, "08 Oct 2023", "Liga Nacional", "Finalizado")
                )
                _uiState.value = PartidosAnterioresState.Success(mockPartidos)
            } catch (e: Exception) {
                _uiState.value = PartidosAnterioresState.Error("No se pudieron cargar los partidos.")
            }
        }
    }
}
