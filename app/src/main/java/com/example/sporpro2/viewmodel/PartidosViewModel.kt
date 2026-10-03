package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Partido
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PartidosState {
    data object Loading : PartidosState
    data class Success(val partidos: List<Partido>) : PartidosState
    data class Error(val message: String) : PartidosState
}

class PartidosViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<PartidosState>(PartidosState.Loading)
    val uiState: StateFlow<PartidosState> = _uiState.asStateFlow()

    init { fetchPartidos() }

    fun fetchPartidos() {
        viewModelScope.launch {
            _uiState.value = PartidosState.Loading
            try {
                val partidos = SupabaseClient.client.postgrest["partidos"]
                    .select {
                        filter { eq("estado", "Finalizado") }
                    }
                    .decodeList<Partido>()
                _uiState.value = PartidosState.Success(partidos)
            } catch (e: Exception) {
                _uiState.value = PartidosState.Error(e.message ?: "Error")
            }
        }
    }
}
