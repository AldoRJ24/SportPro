package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Jugador
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DirectorioState {
    data object Loading : DirectorioState
    data class Success(val jugadores: List<Jugador>) : DirectorioState
    data class Error(val message: String) : DirectorioState
}

class DirectorioViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DirectorioState>(DirectorioState.Loading)
    val uiState: StateFlow<DirectorioState> = _uiState.asStateFlow()

    private var todosLosJugadores = listOf<Jugador>()

    init { fetchJugadores() }

    fun fetchJugadores() {
        viewModelScope.launch {
            _uiState.value = DirectorioState.Loading
            try {
                todosLosJugadores = SupabaseClient.client.postgrest["jugadores"].select().decodeList<Jugador>()
                _uiState.value = DirectorioState.Success(todosLosJugadores)
            } catch (e: Exception) {
                _uiState.value = DirectorioState.Error(e.message ?: "Error")
            }
        }
    }

    fun filtrar(query: String) {
        if (query.isBlank()) {
            _uiState.value = DirectorioState.Success(todosLosJugadores)
        } else {
            val filtrados = todosLosJugadores.filter { 
                it.posicionPrincipal?.contains(query, ignoreCase = true) == true ||
                it.estado?.contains(query, ignoreCase = true) == true
            }
            _uiState.value = DirectorioState.Success(filtrados)
        }
    }
}
