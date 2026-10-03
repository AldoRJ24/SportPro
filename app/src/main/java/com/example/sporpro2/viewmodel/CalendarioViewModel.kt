package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Entrenamiento
import com.example.sporpro2.model.EventoCalendario
import com.example.sporpro2.model.Partido
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CalendarioState {
    data object Loading : CalendarioState
    data class Success(val eventos: List<EventoCalendario>) : CalendarioState
    data class Error(val message: String) : CalendarioState
}

class CalendarioViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<CalendarioState>(CalendarioState.Loading)
    val uiState: StateFlow<CalendarioState> = _uiState.asStateFlow()

    init { fetchEventos() }

    fun fetchEventos() {
        viewModelScope.launch {
            _uiState.value = CalendarioState.Loading
            try {
                val partidos = SupabaseClient.client.postgrest["partidos"].select().decodeList<Partido>()
                val entrenamientos = SupabaseClient.client.postgrest["entrenamientos"].select().decodeList<Entrenamiento>()
                
                val eventos = mutableListOf<EventoCalendario>()
                eventos.addAll(partidos.map { 
                    EventoCalendario(it.id, "Partido vs ${it.rival ?: "Rival"}", it.fechaHora ?: "", "PARTIDO") 
                })
                eventos.addAll(entrenamientos.map { 
                    EventoCalendario(it.id, "Entrenamiento: ${it.objetivo ?: "General"}", it.fechaHora ?: "", "ENTRENAMIENTO") 
                })
                
                // Ordenar por fecha_hora
                eventos.sortBy { it.fechaHora }
                
                _uiState.value = CalendarioState.Success(eventos)
            } catch (e: Exception) {
                _uiState.value = CalendarioState.Error(e.message ?: "Error al cargar eventos")
            }
        }
    }
}
