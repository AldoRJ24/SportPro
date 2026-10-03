package com.example.sporpro2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class AnnouncementRecord(
    val id: String = "",
    val titulo: String = "",
    val mensaje: String = "",
    val fecha: String = "",
    val autor: String = ""
)

sealed interface CommunityState {
    data object Loading : CommunityState
    data class Success(val announcements: List<AnnouncementRecord>) : CommunityState
    data class Error(val message: String) : CommunityState
}

class CommunityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<CommunityState>(CommunityState.Loading)
    val uiState: StateFlow<CommunityState> = _uiState.asStateFlow()

    init {
        loadAnnouncements()
    }

    fun loadAnnouncements() {
        viewModelScope.launch {
            _uiState.value = CommunityState.Loading
            try {
                // Lee las publicaciones desde Supabase
                val records = SupabaseClient.client.postgrest["publicaciones"]
                    .select()
                    .decodeList<AnnouncementRecord>()
                
                // Muestra la lista, invirtiendo para ver las más recientes arriba
                _uiState.value = CommunityState.Success(records.reversed())
            } catch (e: Exception) {
                _uiState.value = CommunityState.Error(e.message ?: "Error al cargar el muro")
            }
        }
    }

    fun postMessage(mensaje: String, autor: String) {
        viewModelScope.launch {
            try {
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val currentDate = dateFormat.format(Date())

                val nuevaPub = mapOf(
                    "titulo" to "Nuevo Mensaje",
                    "mensaje" to mensaje,
                    "fecha" to currentDate,
                    "autor" to autor
                )
                
                // Guardar el texto en la tabla publicaciones
                SupabaseClient.client.postgrest["publicaciones"].insert(nuevaPub)
                
                // Actualizar la lista leyendo nuevamente desde la base de datos
                loadAnnouncements()
            } catch (e: Exception) {
                loadAnnouncements()
            }
        }
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.postgrest["publicaciones"].delete {
                    filter { eq("id", id) }
                }
                // Actualizar la lista leyendo nuevamente desde la base de datos
                loadAnnouncements()
            } catch (e: Exception) {
                loadAnnouncements()
            }
        }
    }
}

