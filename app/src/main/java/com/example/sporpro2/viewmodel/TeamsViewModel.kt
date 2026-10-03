package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TeamRecord(
    val id: String = "",
    val nombre: String = "",
    val categoria: String = "",
    val dtAsignadoId: String = "",
    val estado: String = ""
)

sealed interface TeamsState {
    data object Loading : TeamsState
    data class Success(val teams: List<TeamRecord>) : TeamsState
    data class Error(val message: String) : TeamsState
}

class TeamsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<TeamsState>(TeamsState.Loading)
    val uiState: StateFlow<TeamsState> = _uiState.asStateFlow()

    init {
        leerEquipos()
    }

    fun leerEquipos() {
        viewModelScope.launch {
            _uiState.value = TeamsState.Loading
            try {
                // Simulación con datos
                kotlinx.coroutines.delay(1000)
                val teams = listOf(
                    TeamRecord("1", "Los Tigres", "Sub-15", "DT_001", "Activo"),
                    TeamRecord("2", "Águilas", "Sub-17", "DT_002", "Inactivo"),
                    TeamRecord("3", "Leones FC", "Mayores", "DT_003", "Activo")
                )
                _uiState.value = TeamsState.Success(teams)
            } catch (e: Exception) {
                _uiState.value = TeamsState.Error(e.message ?: "Error desconocido")
            }
        }
    }
}
