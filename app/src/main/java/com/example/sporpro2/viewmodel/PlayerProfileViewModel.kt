package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerProfileState(
    val posicionPrincipal: String = "",
    val pieDominante: String = "",
    val fechaNacimiento: String = "",
    val pesoKg: String = "",
    val estaturaCm: String = "",
    val fotoUrl: String = "",
    val padreId: String = "",
    val parentesco: String = "",
    val isLoading: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class PlayerProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerProfileState())
    val uiState: StateFlow<PlayerProfileState> = _uiState.asStateFlow()

    fun onPosicionPrincipalChange(newValue: String) { _uiState.value = _uiState.value.copy(posicionPrincipal = newValue) }
    fun onPieDominanteChange(newValue: String) { _uiState.value = _uiState.value.copy(pieDominante = newValue) }
    fun onFechaNacimientoChange(newValue: String) { _uiState.value = _uiState.value.copy(fechaNacimiento = newValue) }
    fun onPesoKgChange(newValue: String) { _uiState.value = _uiState.value.copy(pesoKg = newValue) }
    fun onEstaturaCmChange(newValue: String) { _uiState.value = _uiState.value.copy(estaturaCm = newValue) }
    fun onFotoUrlChange(newValue: String) { _uiState.value = _uiState.value.copy(fotoUrl = newValue) }
    
    // Vinculación Padres
    fun onPadreIdChange(newValue: String) { _uiState.value = _uiState.value.copy(padreId = newValue) }
    fun onParentescoChange(newValue: String) { _uiState.value = _uiState.value.copy(parentesco = newValue) }

    fun guardarPerfil() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, saveSuccess = false)
            try {
                // Simular el guardado
                kotlinx.coroutines.delay(1000) 
                _uiState.value = _uiState.value.copy(isLoading = false, saveSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
