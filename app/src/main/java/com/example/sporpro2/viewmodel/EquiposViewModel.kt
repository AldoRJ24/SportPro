package com.example.sporpro2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Equipo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EquiposState {
    data object Loading : EquiposState
    data class Success(val equipos: List<Equipo>) : EquiposState
    data class Error(val message: String) : EquiposState
}

class EquiposViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<EquiposState>(EquiposState.Loading)
    val uiState: StateFlow<EquiposState> = _uiState.asStateFlow()

    init {
        fetchEquipos()
    }

    fun fetchEquipos() {
        viewModelScope.launch {
            _uiState.value = EquiposState.Loading
            try {
                // Hacemos el SELECT a la tabla equipos usando postgrest-kt
                val equipos = SupabaseClient.client.postgrest["equipos"]
                    .select()
                    .decodeList<Equipo>()
                _uiState.value = EquiposState.Success(equipos)
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error al obtener equipos", e)
                _uiState.value = EquiposState.Error(e.message ?: "Error desconocido")
            }
        }
    }
}
