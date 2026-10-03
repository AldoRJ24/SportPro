package com.example.sporpro2.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sporpro2.SupabaseClient
import com.example.sporpro2.model.Equipo
import com.example.sporpro2.model.EventoPartido
import com.example.sporpro2.model.Usuario
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EventoPartidoUI(
    val id: String,
    val tipoEvento: String,
    val createdAt: String,
    val nombreJugador: String,
    val nombreEquipo: String
)

sealed interface PartidoVivoState {
    data object Loading : PartidoVivoState
    data class Success(val eventos: List<EventoPartidoUI>) : PartidoVivoState
    data class Error(val message: String) : PartidoVivoState
}

class PartidoEnVivoViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<PartidoVivoState>(PartidoVivoState.Loading)
    val uiState: StateFlow<PartidoVivoState> = _uiState.asStateFlow()

    private val _showEventDialog = MutableStateFlow(false)
    val showEventDialog: StateFlow<Boolean> = _showEventDialog.asStateFlow()

    private val _selectedEventType = MutableStateFlow("")
    val selectedEventType: StateFlow<String> = _selectedEventType.asStateFlow()

    private val _equiposDelPartido = MutableStateFlow<List<Equipo>>(emptyList())
    val equiposDelPartido: StateFlow<List<Equipo>> = _equiposDelPartido.asStateFlow()

    private val _jugadoresDelEquipoSeleccionado = MutableStateFlow<List<Usuario>>(emptyList())
    val jugadoresDelEquipoSeleccionado: StateFlow<List<Usuario>> = _jugadoresDelEquipoSeleccionado.asStateFlow()

    private val _selectedTeamId = MutableStateFlow("")
    val selectedTeamId: StateFlow<String> = _selectedTeamId.asStateFlow()

    private val _selectedPlayerId = MutableStateFlow("")
    val selectedPlayerId: StateFlow<String> = _selectedPlayerId.asStateFlow()

    private val _nombreLocal = MutableStateFlow("Equipo Local")
    val nombreLocal: StateFlow<String> = _nombreLocal.asStateFlow()

    private val _nombreVisita = MutableStateFlow("Equipo Visita")
    val nombreVisita: StateFlow<String> = _nombreVisita.asStateFlow()

    private val _isPartidoActivo = MutableStateFlow(true)
    val isPartidoActivo: StateFlow<Boolean> = _isPartidoActivo.asStateFlow()

    // ID de partido simulado o fijo para demostración
    private val partidoDemoId = "partido_demo_001"

    init {
        cargarEquiposYEventos()
    }

    private fun cargarEquiposYEventos() {
        viewModelScope.launch {
            _uiState.value = PartidoVivoState.Loading
            try {
                // 1. Cargar 2 equipos para la demo
                val equipos = SupabaseClient.client.postgrest["equipos"]
                    .select { limit(2) }
                    .decodeList<Equipo>()
                _equiposDelPartido.value = equipos
                
                if (equipos.isNotEmpty()) _nombreLocal.value = equipos[0].nombre ?: "Equipo Local"
                if (equipos.size > 1) _nombreVisita.value = equipos[1].nombre ?: "Equipo Visita"

                // 2. Cargar eventos históricos
                val eventos = SupabaseClient.client.postgrest["eventos_partido"]
                    .select {
                        order("created_at", order = Order.DESCENDING)
                    }
                    .decodeList<EventoPartido>()
                
                val eventosUI = eventos.map { mapearAUI(it) }
                _uiState.value = PartidoVivoState.Success(eventosUI)
                
                // 3. Suscribirse a Realtime
                suscribirseEventosRealtime()
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error inicializando consola DT", e)
                _uiState.value = PartidoVivoState.Error(e.message ?: "Error inicializando consola DT")
            }
        }
    }

    private suspend fun mapearAUI(evento: EventoPartido): EventoPartidoUI {
        var nombreJ = "Jugador ${evento.jugadorId?.take(3)}"
        var nombreE = "Equipo ${evento.equipoInvolucrado?.take(3)}"
        
        try {
            if (evento.jugadorId != null) {
                val uId = evento.jugadorId.trim()
                if (uId.isNotEmpty()) {
                    val u = SupabaseClient.client.postgrest["usuarios"]
                        .select { filter { eq("id", uId) } }
                        .decodeSingleOrNull<Usuario>()
                    if (u?.nombreCompleto != null) nombreJ = u.nombreCompleto
                }
            }
            if (evento.equipoInvolucrado != null) {
                val eId = evento.equipoInvolucrado.trim()
                if (eId.isNotEmpty()) {
                    // Buscamos en la lista cacheada primero
                    val eqCache = _equiposDelPartido.value.find { it.id == eId }
                    if (eqCache != null) {
                        nombreE = eqCache.nombre ?: "Equipo"
                    } else {
                        val eq = SupabaseClient.client.postgrest["equipos"]
                            .select { filter { eq("id", eId) } }
                            .decodeSingleOrNull<Equipo>()
                        if (eq?.nombre != null) nombreE = eq.nombre
                    }
                }
            }
        } catch (e: Exception) { }

        return EventoPartidoUI(
            id = evento.id ?: "",
            tipoEvento = evento.tipoEvento ?: "",
            createdAt = evento.createdAt ?: "",
            nombreJugador = nombreJ,
            nombreEquipo = nombreE
        )
    }

    private fun suscribirseEventosRealtime() {
        viewModelScope.launch {
            try {
                val channel = SupabaseClient.client.channel("partido_en_vivo")
                val flow = channel.postgresChangeFlow<PostgresAction.Insert>("public") {
                    table = "eventos_partido"
                }
                
                channel.subscribe()

                flow.collect { action ->
                    val nuevoEvento = action.decodeRecord<EventoPartido>()
                    val eventoUI = mapearAUI(nuevoEvento)
                    
                    val currentState = _uiState.value
                    if (currentState is PartidoVivoState.Success) {
                        val listaActualizada = listOf(eventoUI) + currentState.eventos
                        _uiState.value = PartidoVivoState.Success(listaActualizada)
                    } else {
                        _uiState.value = PartidoVivoState.Success(listOf(eventoUI))
                    }
                }
            } catch (e: Exception) {
                Log.e("Realtime", "Error en suscripción", e)
            }
        }
    }

    fun onTeamSelected(equipoId: String) {
        _selectedTeamId.value = equipoId
        _selectedPlayerId.value = ""
        viewModelScope.launch {
            try {
                val jugadores = SupabaseClient.client.postgrest["usuarios"]
                    .select { 
                        filter { eq("rol", "JUG") } 
                        limit(5)
                    }
                    .decodeList<Usuario>()
                _jugadoresDelEquipoSeleccionado.value = jugadores
            } catch (e: Exception) {
                Log.e("Supabase", "Error cargando jugadores", e)
            }
        }
    }

    fun onPlayerSelected(jugadorId: String) {
        _selectedPlayerId.value = jugadorId
    }

    fun abrirDialogo(tipoEvento: String) {
        _selectedEventType.value = tipoEvento
        _selectedTeamId.value = ""
        _selectedPlayerId.value = ""
        _jugadoresDelEquipoSeleccionado.value = emptyList()
        _showEventDialog.value = true
    }

    fun cerrarDialogo() {
        _showEventDialog.value = false
    }

    fun registrarEventoConfirmado() {
        if (_selectedTeamId.value.isBlank() || _selectedPlayerId.value.isBlank()) return
        
        viewModelScope.launch {
            try {
                val nuevoEvento = EventoPartido(
                    partidoId = partidoDemoId,
                    equipoInvolucrado = _selectedTeamId.value,
                    jugadorId = _selectedPlayerId.value,
                    tipoEvento = _selectedEventType.value
                    // No enviamos createdAt ni minuto para que Supabase use DEFAULT NOW()
                )
                
                SupabaseClient.client.postgrest["eventos_partido"].insert(nuevoEvento)
                cerrarDialogo()
                
                // CRÍTICO: Recargar eventos inmediatamente después de insertar
                cargarEquiposYEventos()
            } catch (e: Exception) {
                Log.e("SupabaseError", "Error al registrar evento", e)
            }
        }
    }
}
