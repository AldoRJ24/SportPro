package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Entrenamiento
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EntrenamientosState {
    data object Loading : EntrenamientosState
    data class Success(val entrenamientos: List<Entrenamiento>) : EntrenamientosState
    data class Error(val message: String) : EntrenamientosState
}

class EntrenamientosViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<EntrenamientosState>(EntrenamientosState.Loading)
    val uiState: StateFlow<EntrenamientosState> = _uiState.asStateFlow()

    init { fetchEntrenamientos() }

    fun fetchEntrenamientos() {
        viewModelScope.launch {
            _uiState.value = EntrenamientosState.Loading
            try {
                val entrenamientos = SupabaseClient.client.postgrest["entrenamientos"]
                    .select() // Listar entrenamientos, podría añadirse filtro de fecha
                    .decodeList<Entrenamiento>()
                _uiState.value = EntrenamientosState.Success(entrenamientos)
            } catch (e: Exception) {
                _uiState.value = EntrenamientosState.Error(e.message ?: "Error")
            }
        }
    }
}
