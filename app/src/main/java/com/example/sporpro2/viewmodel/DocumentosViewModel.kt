package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Documento
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DocumentosState {
    data object Loading : DocumentosState
    data class Success(val documentos: List<Documento>) : DocumentosState
    data class Error(val message: String) : DocumentosState
}

class DocumentosViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DocumentosState>(DocumentosState.Loading)
    val uiState: StateFlow<DocumentosState> = _uiState.asStateFlow()

    init { fetchDocumentos() }

    fun fetchDocumentos() {
        viewModelScope.launch {
            _uiState.value = DocumentosState.Loading
            try {
                val docs = SupabaseClient.client.postgrest["documentos_jugador"].select().decodeList<Documento>()
                _uiState.value = DocumentosState.Success(docs)
            } catch (e: Exception) {
                _uiState.value = DocumentosState.Error(e.message ?: "Error")
            }
        }
    }
}
