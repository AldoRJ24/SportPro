package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Pago
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PagosState {
    data object Loading : PagosState
    data class Success(val pagos: List<Pago>) : PagosState
    data class Error(val message: String) : PagosState
}

class PagosViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<PagosState>(PagosState.Loading)
    val uiState: StateFlow<PagosState> = _uiState.asStateFlow()

    init { fetchPagos() }

    fun fetchPagos() {
        viewModelScope.launch {
            _uiState.value = PagosState.Loading
            try {
                val pagos = SupabaseClient.client.postgrest["pagos_simulados"].select().decodeList<Pago>()
                _uiState.value = PagosState.Success(pagos)
            } catch (e: Exception) {
                _uiState.value = PagosState.Error(e.message ?: "Error")
            }
        }
    }
}
