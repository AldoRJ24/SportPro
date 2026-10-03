package com.example.sporpro2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Publicacion
import com.example.sporpro2.model.PublicacionInsert
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ComunidadState {
    data object Loading : ComunidadState
    data class Success(val publicaciones: List<Publicacion>) : ComunidadState
    data class Error(val message: String) : ComunidadState
}

class ComunidadViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ComunidadState>(ComunidadState.Loading)
    val uiState: StateFlow<ComunidadState> = _uiState.asStateFlow()

    init { fetchPublicaciones() }

    fun fetchPublicaciones() {
        viewModelScope.launch {
            _uiState.value = ComunidadState.Loading
            try {
                val pubs = SupabaseClient.client.postgrest["publicaciones"]
                    .select {
                        order("fecha_publicacion", order = Order.DESCENDING)
                    }
                    .decodeList<Publicacion>()
                _uiState.value = ComunidadState.Success(pubs)
            } catch (e: Exception) {
                _uiState.value = ComunidadState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun insertPublicacion(contenido: String) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.postgrest["publicaciones"].insert(PublicacionInsert(contenido))
                fetchPublicaciones() // Refrescar muro
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error al publicar", e)
            }
        }
    }
}
