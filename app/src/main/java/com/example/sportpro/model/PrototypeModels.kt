package com.example.sportpro.model

// US-01 & US-02: Partido y Eventos
data class MatchEvent(
    val id: String,
    val minute: Int,
    val type: EventType,
    val player: String,
    val description: String
)

enum class EventType {
    GOAL, YELLOW_CARD, RED_CARD, FOUL, SUBSTITUTION
}

data class LiveMatchState(
    val homeTeam: String = "SportPro Sub-15",
    val awayTeam: String = "Rival FC",
    val homeScore: Int = 2,
    val awayScore: Int = 1,
    val period: String = "2do Tiempo",
    val minute: Int = 68,
    val isFinished: Boolean = false,
    val events: List<MatchEvent> = listOf(
        MatchEvent("1", 12, EventType.GOAL, "Mateo Gómez", "Gol de jugada individual"),
        MatchEvent("2", 34, EventType.YELLOW_CARD, "Lucas Silva", "Falta táctica"),
        MatchEvent("3", 52, EventType.GOAL, "Carlos Pérez", "Remate de cabeza en tiro de esquina")
    )
)

// US-03: Perfil de Jugador
data class PlayerProfile(
    val id: String,
    val fullName: String,
    val birthDate: String, // YYYY-MM-DD
    val age: Int,
    val heightCm: Int,
    val weightKg: Double,
    val dominantFoot: String,
    val position: String,
    val photoUrl: String? = null,
    val isAgeVerified: Boolean = true,
    val isDataProtected: Boolean = true,
    val parentName: String? = "María Rodríguez",
    val parentPhone: String? = "+56 9 8765 4321"
)

// US-04 & US-09: Convocatorias y Alineación
enum class AvailabilityStatus {
    CONFIRMED, PENDING, DECLINED
}

data class PlayerAvailability(
    val playerId: String,
    val playerName: String,
    val position: String,
    val status: AvailabilityStatus
)

data class MatchCallUp(
    val id: String,
    val opponent: String,
    val date: String,
    val location: String,
    val category: String,
    val players: List<PlayerAvailability>
)

// US-05: Entrenamientos y Ejercicios
data class Exercise(
    val id: String,
    val title: String,
    val category: String,
    val durationMinutes: Int,
    val description: String,
    val isFavorite: Boolean = false
)

data class AttendanceRecord(
    val playerId: String,
    val playerName: String,
    val isPresent: Boolean = true,
    val note: String = ""
)

// US-06: Muro de la Comunidad y Moderación
data class CommunityPost(
    val id: String,
    val authorName: String,
    val authorRole: String,
    val timeAgo: String,
    val content: String,
    val likesCount: Int,
    val reportsCount: Int = 0,
    val isHidden: Boolean = false,
    val comments: List<String> = emptyList()
)

// US-07: Anuncios Internos
data class InternalAnnouncement(
    val id: String,
    val title: String,
    val sender: String,
    val targetGroup: String,
    val date: String,
    val message: String,
    val isUrgent: Boolean = false
)

// US-08: Equipos y Categorías
data class TeamCategory(
    val id: String,
    val name: String,
    val ageRange: String,
    val coachName: String,
    val playerCapacity: Int,
    val registeredPlayers: Int
)

// US-10: Mensualidades Simuladas
enum class PaymentStatus {
    PAID, PENDING, OVERDUE
}

data class MonthlyPayment(
    val id: String,
    val monthYear: String,
    val playerName: String,
    val amount: String,
    val dueDate: String,
    val status: PaymentStatus,
    val paymentDate: String? = null
)

// US-11: Estadísticas por Jugador
data class PlayerStats(
    val playerName: String,
    val matchesPlayed: Int,
    val goals: Int,
    val assists: Int,
    val yellowCards: Int,
    val redCards: Int,
    val minutesPlayed: Int
)

// US-12: Convocatorias / Pruebas Abiertas
data class TryoutNotice(
    val id: String,
    val title: String,
    val categoryAge: String,
    val positionRequired: String,
    val date: String,
    val time: String,
    val location: String,
    val requirements: String
)
