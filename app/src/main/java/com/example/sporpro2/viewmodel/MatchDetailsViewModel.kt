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

@Serializable
data class EventoPartidoRecord(
    val id: String = "",
    val partido_id: String = "",
    val minuto: Int = 0,
    val jugador_nombre: String = "",
    val tipo_evento: String = "", // "GOL", "AMARILLA", "ROJA", "TIRO_ARCO", "TIRO_ESQUINA", "CAMBIO"
    val equipo: String = "" // "LOCAL" o "VISITANTE"
)

enum class MatchEventType { GOAL, YELLOW_CARD, RED_CARD, SUBSTITUTION, SHOT_ON_TARGET, CORNER_KICK, UNKNOWN }

data class MatchEvent(
    val minute: Int,
    val player: String,
    val type: MatchEventType,
    val description: String = ""
)

data class GoalScorer(
    val player: String,
    val minute: Int
)

data class MatchStats(
    val shotsOnTargetLocal: Int = 8,
    val shotsOnTargetVisitor: Int = 4,
    val possessionLocal: String = "55%",
    val possessionVisitor: String = "45%",
    val foulsLocal: Int = 10,
    val foulsVisitor: Int = 12,
    val yellowCardsLocal: Int = 2,
    val yellowCardsVisitor: Int = 3,
    val redCardsLocal: Int = 0,
    val redCardsVisitor: Int = 1
)

data class MatchDetailsState(
    val localTeam: String = "SportPro",
    val visitorTeam: String = "Halcones FC",
    val localScore: Int = 2,
    val visitorScore: Int = 1,
    val localScorers: List<GoalScorer> = listOf(
        GoalScorer("C. Sánchez", 12),
        GoalScorer("P. Rojas", 45)
    ),
    val visitorScorers: List<GoalScorer> = listOf(
        GoalScorer("J. Pérez", 60)
    ),
    val events: List<MatchEvent> = emptyList(),
    val stats: MatchStats = MatchStats(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MatchDetailsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MatchDetailsState())
    val uiState: StateFlow<MatchDetailsState> = _uiState.asStateFlow()

    fun loadMatchDetails(matchId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Obtener todos los eventos para este partido exacto desde Supabase
                val records = SupabaseClient.client.postgrest["eventos_partido"]
                    .select { filter { eq("partido_id", matchId) } }
                    .decodeList<EventoPartidoRecord>()

                var localGoals = 0
                var visitorGoals = 0
                var localYellows = 0
                var visitorYellows = 0
                var localReds = 0
                var visitorReds = 0
                var localShots = 0
                var visitorShots = 0
                var localFouls = 0
                var visitorFouls = 0

                val localScorersList = mutableListOf<GoalScorer>()
                val visitorScorersList = mutableListOf<GoalScorer>()

                val mappedEvents = records.mapNotNull { record ->
                    val isLocal = record.equipo.equals("LOCAL", ignoreCase = true)
                    
                    val type = when (record.tipo_evento.uppercase()) {
                        "GOL" -> {
                            if (isLocal) {
                                localGoals++
                                localScorersList.add(GoalScorer(record.jugador_nombre, record.minuto))
                            } else {
                                visitorGoals++
                                visitorScorersList.add(GoalScorer(record.jugador_nombre, record.minuto))
                            }
                            MatchEventType.GOAL
                        }
                        "AMARILLA" -> {
                            if (isLocal) localYellows++ else visitorYellows++
                            MatchEventType.YELLOW_CARD
                        }
                        "ROJA" -> {
                            if (isLocal) localReds++ else visitorReds++
                            MatchEventType.RED_CARD
                        }
                        "CAMBIO" -> MatchEventType.SUBSTITUTION
                        "TIRO_ARCO" -> {
                            if (isLocal) localShots++ else visitorShots++
                            MatchEventType.SHOT_ON_TARGET
                        }
                        "FALTA" -> {
                            if (isLocal) localFouls++ else visitorFouls++
                            MatchEventType.UNKNOWN
                        }
                        else -> MatchEventType.UNKNOWN
                    }

                    if (type in listOf(MatchEventType.GOAL, MatchEventType.YELLOW_CARD, MatchEventType.RED_CARD, MatchEventType.SUBSTITUTION)) {
                        MatchEvent(
                            minute = record.minuto,
                            player = record.jugador_nombre,
                            type = type,
                            description = if (isLocal) "Equipo Local" else "Equipo Visitante"
                        )
                    } else {
                        null
                    }
                }.sortedBy { it.minute }

                if (records.isEmpty()) {
                    // Si no hay datos en la DB para este ID, cargar datos demostrativos
                    val defaultEvents = listOf(
                        MatchEvent(12, "Carlos Sánchez", MatchEventType.GOAL, "Equipo Local"),
                        MatchEvent(34, "Luis Martínez", MatchEventType.YELLOW_CARD, "Equipo Local"),
                        MatchEvent(45, "Pedro Rojas", MatchEventType.GOAL, "Equipo Local"),
                        MatchEvent(60, "Juan Pérez", MatchEventType.GOAL, "Equipo Visitante"),
                        MatchEvent(75, "Jorge Vega", MatchEventType.SUBSTITUTION, "Equipo Local"),
                        MatchEvent(88, "Andrés Gómez", MatchEventType.RED_CARD, "Equipo Visitante")
                    )
                    _uiState.value = MatchDetailsState(
                        localTeam = "SportPro",
                        visitorTeam = "Halcones FC",
                        localScore = 2,
                        visitorScore = 1,
                        localScorers = listOf(GoalScorer("C. Sánchez", 12), GoalScorer("P. Rojas", 45)),
                        visitorScorers = listOf(GoalScorer("J. Pérez", 60)),
                        events = defaultEvents,
                        stats = MatchStats(),
                        isLoading = false
                    )
                } else {
                    _uiState.value = MatchDetailsState(
                        localTeam = "SportPro",
                        visitorTeam = "Rival FC",
                        localScore = localGoals,
                        visitorScore = visitorGoals,
                        localScorers = localScorersList,
                        visitorScorers = visitorScorersList,
                        events = mappedEvents,
                        stats = MatchStats(
                            shotsOnTargetLocal = localShots.coerceAtLeast(5),
                            shotsOnTargetVisitor = visitorShots.coerceAtLeast(3),
                            possessionLocal = "55%",
                            possessionVisitor = "45%",
                            foulsLocal = localFouls.coerceAtLeast(8),
                            foulsVisitor = visitorFouls.coerceAtLeast(10),
                            yellowCardsLocal = localYellows,
                            yellowCardsVisitor = visitorYellows,
                            redCardsLocal = localReds,
                            redCardsVisitor = visitorReds
                        ),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
