package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConvocatoriaRecord(
    val id: String,
    val jugadorNombre: String,
    val esTitular: Boolean,
    val respuesta: String
)

data class LineupState(
    val convocatorias: List<ConvocatoriaRecord> = emptyList(),
    val isSaving: Boolean = false
)

class LineupViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LineupState())
    val uiState: StateFlow<LineupState> = _uiState.asStateFlow()

    init {
        loadConvocatorias()
    }

    private fun loadConvocatorias() {
        val mockData = listOf(
            ConvocatoriaRecord("1", "Carlos Sánchez", false, "Confirmado"),
            ConvocatoriaRecord("2", "Luis Martínez", true, "Confirmado"),
            ConvocatoriaRecord("3", "Jorge Vega", false, "Pendiente")
        )
        _uiState.value = _uiState.value.copy(convocatorias = mockData)
    }

    fun setEsTitular(id: String, esTitular: Boolean) {
        val updated = _uiState.value.convocatorias.map {
            if (it.id == id) it.copy(esTitular = esTitular) else it
        }
        _uiState.value = _uiState.value.copy(convocatorias = updated)
    }
}
