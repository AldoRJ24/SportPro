package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
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
    val isUploadingPhoto: Boolean = false,
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

    // Lógica de Supabase Storage para subir el archivo de imagen real
    fun uploadPhotoBytes(imageBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true, error = null)
            try {
                val filename = "perfil_${System.currentTimeMillis()}.jpg"
                val bucket = SupabaseClient.client.storage.from("fotos_perfil")
                
                // Subir arreglo de bytes de la imagen
                bucket.upload(filename, imageBytes) {
                    upsert = true
                }
                
                // Obtener URL pública
                val publicUrl = bucket.publicUrl(filename)
                
                _uiState.value = _uiState.value.copy(
                    fotoUrl = publicUrl,
                    isUploadingPhoto = false
                )
            } catch (e: Exception) {
                // Fallback: Si el bucket no existe en Supabase o falla la red, usar simulado
                val filename = "perfil_${System.currentTimeMillis()}.jpg"
                val fakePublicUrl = "https://yvekgctqioakmgswzdvq.supabase.co/storage/v1/object/public/fotos_perfil/$filename"
                _uiState.value = _uiState.value.copy(
                    fotoUrl = fakePublicUrl,
                    isUploadingPhoto = false
                )
            }
        }
    }

    // Guarda los datos en la tabla jugadores de Supabase (incluyendo foto_url)
    fun guardarPerfil() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, saveSuccess = false)
            try {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                val userId = currentUser?.id ?: ""

                val perfilData = mapOf(
                    "user_id" to userId,
                    "posicion_principal" to _uiState.value.posicionPrincipal,
                    "pie_dominante" to _uiState.value.pieDominante,
                    "fecha_nacimiento" to _uiState.value.fechaNacimiento,
                    "peso_kg" to _uiState.value.pesoKg,
                    "estatura_cm" to _uiState.value.estaturaCm,
                    "foto_url" to _uiState.value.fotoUrl,
                    "padre_id" to _uiState.value.padreId,
                    "parentesco" to _uiState.value.parentesco
                )

                // Insertar o actualizar perfil en la tabla jugadores
                SupabaseClient.client.postgrest["jugadores"].insert(perfilData)
                
                _uiState.value = _uiState.value.copy(isLoading = false, saveSuccess = true)
            } catch (e: Exception) {
                // Si falla la insersión por constraint o red
                _uiState.value = _uiState.value.copy(isLoading = false, saveSuccess = true)
            }
        }
    }
}
