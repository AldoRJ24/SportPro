package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MatchRecord(
    val id: String,
    val equipoLocal: String,
    val equipoVisitante: String,
    val golesLocal: Int,
    val golesVisitante: Int,
    val fecha: String,
    val torneo: String,
    val estado: String // Finalizado, Suspendido, etc.
)

sealed interface PastMatchesState {
    data object Loading : PastMatchesState
    data class Success(val matches: List<MatchRecord>) : PastMatchesState
    data class Error(val message: String) : PastMatchesState
}

class PastMatchesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<PastMatchesState>(PastMatchesState.Loading)
    val uiState: StateFlow<PastMatchesState> = _uiState.asStateFlow()

    init {
        loadPastMatches()
    }

    private fun loadPastMatches() {
        viewModelScope.launch {
            _uiState.value = PastMatchesState.Loading
            try {
                kotlinx.coroutines.delay(800)
                val mockMatches = listOf(
                    MatchRecord("1", "SportPro Sub-15", "Halcones FC", 3, 1, "05 Oct 2023", "Liga Regional", "Finalizado"),
                    MatchRecord("2", "SportPro Sub-17", "Tigres del Norte", 2, 2, "06 Oct 2023", "Torneo de Verano", "Finalizado"),
                    MatchRecord("3", "SportPro Mayores", "Deportivo Sur", 1, 0, "08 Oct 2023", "Liga Regional", "Finalizado")
                )
                _uiState.value = PastMatchesState.Success(mockMatches)
            } catch (e: Exception) {
                _uiState.value = PastMatchesState.Error("Error al cargar partidos")
            }
        }
    }
}
