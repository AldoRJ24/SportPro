package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnnouncementRecord(
    val id: String,
    val titulo: String,
    val mensaje: String,
    val fecha: String,
    val autor: String
)

sealed interface CommunityState {
    data object Loading : CommunityState
    data class Success(val announcements: List<AnnouncementRecord>) : CommunityState
    data class Error(val message: String) : CommunityState
}

class CommunityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<CommunityState>(CommunityState.Loading)
    val uiState: StateFlow<CommunityState> = _uiState.asStateFlow()

    init {
        loadAnnouncements()
    }

    fun loadAnnouncements() {
        viewModelScope.launch {
            _uiState.value = CommunityState.Loading
            try {
                kotlinx.coroutines.delay(800) // Simulación
                val mockData = listOf(
                    AnnouncementRecord(
                        id = "1",
                        titulo = "¡Bienvenidos a la Nueva Temporada!",
                        mensaje = "Estamos emocionados de comenzar los entrenamientos este lunes. Por favor, recuerden traer hidratación y llegar 15 minutos antes.",
                        fecha = "10 Oct 2023",
                        autor = "Administración"
                    ),
                    AnnouncementRecord(
                        id = "2",
                        titulo = "Cambio de Horario - Categoría Sub-15",
                        mensaje = "El partido del fin de semana se ha reprogramado para las 10:00 AM en el campo principal debido a mantenimientos.",
                        fecha = "12 Oct 2023",
                        autor = "Cuerpo Técnico"
                    )
                )
                _uiState.value = CommunityState.Success(mockData)
            } catch (e: Exception) {
                _uiState.value = CommunityState.Error(e.message ?: "Error al cargar el muro")
            }
        }
    }
}
