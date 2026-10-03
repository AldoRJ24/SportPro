package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Publicacion
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnunciosState {
    data object Loading : AnunciosState
    data class Success(val anuncios: List<Publicacion>) : AnunciosState
    data class Error(val message: String) : AnunciosState
}

class AnunciosViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AnunciosState>(AnunciosState.Loading)
    val uiState: StateFlow<AnunciosState> = _uiState.asStateFlow()

    init { fetchAnuncios() }

    fun fetchAnuncios() {
        viewModelScope.launch {
            _uiState.value = AnunciosState.Loading
            try {
                val anuncios = SupabaseClient.client.postgrest["publicaciones"]
                    .select {
                        filter { eq("tipo", "AnuncioUrgente") }
                        order("fecha_publicacion", order = Order.DESCENDING)
                    }
                    .decodeList<Publicacion>()
                _uiState.value = AnunciosState.Success(anuncios)
            } catch (e: Exception) {
                _uiState.value = AnunciosState.Error(e.message ?: "Error")
            }
        }
    }
}
