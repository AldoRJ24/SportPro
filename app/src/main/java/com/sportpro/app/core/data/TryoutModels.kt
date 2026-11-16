package com.sportpro.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId

// ---------------- Avisos de pruebas (migración 011) ----------------
// Reflejo de las funciones SQL tryout_*. Permisos y conteos llegan resueltos por el servidor.

/** Un aviso de prueba tal como lo devuelve tryout_search. */
@Serializable
data class Tryout(
    val id: String,
    @SerialName("academy_id") val academyId: String,
    @SerialName("academy_name") val academyName: String = "",
    @SerialName("crest_path") val crestPath: String? = null,
    val title: String,
    val category: String = "",
    @SerialName("min_age") val minAge: Int = 0,
    @SerialName("max_age") val maxAge: Int = 0,
    /** Códigos POR/DEF/MED/DEL. Lista vacía = se buscan todas las posiciones. */
    val positions: List<String> = emptyList(),
    @SerialName("event_at") val eventAt: String? = null,
    val venue: String = "",
    val district: String = "",
    val slots: Int = 0,
    @SerialName("approved_count") val approvedCount: Int = 0,
    /** Inscripciones que esperan respuesta. El servidor solo lo informa a quien gestiona el aviso. */
    @SerialName("pending_count") val pendingCount: Int = 0,
    val notes: String? = null,
    /** open | closed | cancelled */
    val status: String = "open",
    /** Soy el admin de la academia o el DT que lo publicó. */
    @SerialName("can_manage") val canManage: Boolean = false,
    /** Cuántas inscripciones vivas (pendientes o aceptadas) tengo yo en este aviso. */
    @SerialName("my_applications") val myApplications: Int = 0,
) {
    val slotsLeft: Int get() = (slots - approvedCount).coerceAtLeast(0)
    val isOpen: Boolean get() = status == "open"
}

/** Inscripción vista por quien gestiona el aviso. El menor llega ya como «Nombre I.». */
@Serializable
data class TryoutApplicant(
    val id: String,
    @SerialName("player_name") val playerName: String = "",
    @SerialName("player_age") val playerAge: Int? = null,
    @SerialName("player_position") val playerPosition: String? = null,
    @SerialName("requested_by_name") val requestedByName: String = "",
    @SerialName("by_guardian") val byGuardian: Boolean = false,
    /** pending | approved | rejected */
    val status: String = "pending",
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Una inscripción que yo envié (my_tryout_applications). */
@Serializable
data class MyTryoutApplication(
    val id: String,
    @SerialName("tryout_id") val tryoutId: String,
    val title: String = "",
    @SerialName("academy_name") val academyName: String = "",
    @SerialName("event_at") val eventAt: String? = null,
    val venue: String = "",
    @SerialName("tryout_status") val tryoutStatus: String = "open",
    @SerialName("player_id") val playerId: String = "",
    @SerialName("player_name") val playerName: String = "",
    /** pending | approved | rejected | withdrawn */
    val status: String = "pending",
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    /** Todavía cuenta: se puede retirar. */
    val isLive: Boolean get() = status == "pending" || status == "approved"
}

/** Filtro de fecha de la búsqueda: todas, próximos 7 días o próximos 30 días. */
enum class TryoutDateFilter(val days: Long?) { ALL(null), WEEK(7), MONTH(30) }

/** Por qué un jugador puede o no inscribirse a un aviso. */
enum class TryoutEligibility { OK, TOO_YOUNG, TOO_OLD, UNKNOWN }

/** Primer problema del formulario para publicar un aviso (null = todo bien). */
enum class TryoutFormError { TITLE, CATEGORY, AGES, DATE, PAST, VENUE, DISTRICT, SLOTS }

/**
 * Lógica pura de avisos de pruebas (sin Android ni red, para tests unitarios).
 * Solo sirve para mostrar y avisar antes de enviar: el servidor vuelve a validar todo.
 */
object Tryouts {
    /** El servidor calcula la edad con la fecha de la prueba en hora de Perú; aquí se hace igual. */
    val ZONE: ZoneId = ZoneId.of("America/Lima")

    /** Rango (desde, hasta) que se manda al servidor. ALL = sin límite. */
    fun dateRange(filter: TryoutDateFilter, today: LocalDate): Pair<LocalDate?, LocalDate?> =
        filter.days?.let { today to today.plusDays(it) } ?: (null to null)

    /** Día de la prueba en hora de Perú, o null si el aviso no trae fecha válida. */
    fun eventDay(eventAt: String?): LocalDate? = parseInstant(eventAt)?.atZone(ZONE)?.toLocalDate()

    /** Años cumplidos en un día concreto. */
    fun ageOn(birthIso: String?, day: LocalDate): Int? {
        val birth = runCatching { LocalDate.parse(birthIso?.take(10)) }.getOrNull() ?: return null
        return Period.between(birth, day).years
    }

    /** ¿El jugador tendrá la edad del aviso el día de la prueba? (misma regla que tryout_apply) */
    fun eligibility(birthIso: String?, tryout: Tryout): TryoutEligibility {
        val day = eventDay(tryout.eventAt) ?: return TryoutEligibility.UNKNOWN
        val age = ageOn(birthIso, day) ?: return TryoutEligibility.UNKNOWN
        return when {
            age < tryout.minAge -> TryoutEligibility.TOO_YOUNG
            age > tryout.maxAge -> TryoutEligibility.TOO_OLD
            else -> TryoutEligibility.OK
        }
    }

    /** Valores distintos de un campo, ordenados, para armar los chips de filtro. */
    fun options(tryouts: List<Tryout>, field: (Tryout) -> String): List<String> =
        tryouts.map(field).filter { it.isNotBlank() }.distinct().sorted()

    /**
     * Revisa el formulario en el mismo orden en que se muestra y devuelve el primer problema.
     * Los límites son los de la función SQL tryout_create.
     */
    fun validateForm(
        title: String, category: String, minAge: Int?, maxAge: Int?,
        date: LocalDate?, time: LocalTime?, venue: String, district: String, slots: Int?,
        now: LocalDateTime,
    ): TryoutFormError? = when {
        title.trim().length < 3 -> TryoutFormError.TITLE
        category.trim().length < 2 -> TryoutFormError.CATEGORY
        minAge == null || maxAge == null || minAge < 4 || maxAge > 60 || minAge > maxAge -> TryoutFormError.AGES
        date == null || time == null -> TryoutFormError.DATE
        !LocalDateTime.of(date, time).isAfter(now) -> TryoutFormError.PAST
        venue.trim().length < 3 -> TryoutFormError.VENUE
        district.trim().length < 2 -> TryoutFormError.DISTRICT
        slots == null || slots !in 1..200 -> TryoutFormError.SLOTS
        else -> null
    }
}
