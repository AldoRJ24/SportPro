package com.sportpro.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Period
import java.util.Locale

// ---------------- Mensualidades (migración 008) ----------------
// Estos modelos son el reflejo exacto de lo que devuelven las funciones SQL fee_*.
// La app NO calcula deudas ni permisos: los recibe ya resueltos por el servidor.

/** Resumen de un equipo que gestiono (fee_summary_for_me). */
@Serializable
data class FeeTeamSummary(
    @SerialName("team_id") val teamId: String,
    @SerialName("team_name") val teamName: String,
    val category: String? = null,
    @SerialName("academy_name") val academyName: String? = null,
    /** true = soy admin de la academia: puedo fijar el monto y confirmar pagos. El DT solo mira. */
    @SerialName("can_admin") val canAdmin: Boolean = false,
    /** null = el admin aún no definió la mensualidad del equipo. */
    @SerialName("plan_amount") val planAmount: Double? = null,
    @SerialName("due_day") val dueDay: Int? = null,
    val enrolled: Int = 0,
    val debt: Double = 0.0,
    @SerialName("pending_count") val pendingCount: Int = 0,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    @SerialName("reported_count") val reportedCount: Int = 0,
)

/** Jugador del plantel con su situación de pago (fee_team_players). */
@Serializable
data class FeeTeamPlayer(
    @SerialName("player_id") val playerId: String,
    @SerialName("player_name") val playerName: String,
    @SerialName("jersey_number") val jerseyNumber: Int? = null,
    /** El DT lo eligió para pagar mensualidad. */
    val enabled: Boolean = false,
    val debt: Double = 0.0,
    @SerialName("pending_count") val pendingCount: Int = 0,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    @SerialName("reported_count") val reportedCount: Int = 0,
    @SerialName("last_paid_on") val lastPaidOn: String? = null,
)

/** Un cobro mensual con su último movimiento de pago (fee_statement). */
@Serializable
data class FeeStatementRow(
    @SerialName("charge_id") val chargeId: String,
    @SerialName("team_id") val teamId: String,
    @SerialName("team_name") val teamName: String = "",
    @SerialName("player_id") val playerId: String,
    @SerialName("player_name") val playerName: String = "",
    val period: String,
    val amount: Double = 0.0,
    val currency: String = "PEN",
    @SerialName("due_date") val dueDate: String? = null,
    /** pending | overdue | paid | waived («overdue» lo calcula el servidor). */
    val status: String = "pending",
    @SerialName("waived_reason") val waivedReason: String? = null,
    /** El servidor dice si YO puedo pagar este cobro (apoderado, o jugador adulto). */
    @SerialName("can_pay") val canPay: Boolean = false,
    /** El servidor dice si YO soy el admin que recibe el dinero. */
    @SerialName("can_admin") val canAdmin: Boolean = false,
    @SerialName("payment_id") val paymentId: String? = null,
    /** reported | confirmed | rejected | voided, o null si nunca se pagó. */
    @SerialName("payment_status") val paymentStatus: String? = null,
    val method: String? = null,
    val reference: String? = null,
    val note: String? = null,
    @SerialName("paid_on") val paidOn: String? = null,
    @SerialName("reported_by_name") val reportedByName: String? = null,
    @SerialName("reported_at") val reportedAt: String? = null,
    @SerialName("decided_by_name") val decidedByName: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("decision_reason") val decisionReason: String? = null,
) {
    /** Todavía se debe (pendiente o vencido). */
    val isOpen: Boolean get() = Fees.isOpen(status)
    /** La familia ya informó el pago y falta que el admin lo revise. */
    val isAwaitingReview: Boolean get() = paymentStatus == "reported"
}

/**
 * Lógica pura de mensualidades (sin Android ni red, para poder probarla con tests unitarios).
 * Solo sirve para mostrar y para avisar antes de enviar: el servidor vuelve a validar todo.
 */
object Fees {
    /** Métodos de pago en el orden en que se muestran. Los códigos son los de la base de datos. */
    val METHODS: List<String> = listOf("yape", "plin", "transfer", "cash")

    private val MONTHS = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** 120.0 → «S/ 120.00». Locale.US para que el separador decimal siempre sea el punto. */
    fun money(amount: Double): String = "S/ " + String.format(Locale.US, "%.2f", amount)

    /** «2026-11-01» → «Noviembre 2026». Si el texto no es una fecha, lo devuelve tal cual. */
    fun periodLabel(iso: String?): String {
        val parts = iso?.take(10)?.split("-").orEmpty()
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (year == null || month == null || month !in 1..12) return iso.orEmpty()
        return MONTHS[month - 1].replaceFirstChar { it.uppercase() } + " " + year
    }

    /** «2026-11-10» → «10/11/2026». */
    fun dateLabel(iso: String?): String {
        val parts = iso?.take(10)?.split("-").orEmpty()
        if (parts.size != 3 || parts.any { it.toIntOrNull() == null }) return iso.orEmpty()
        return "${parts[2]}/${parts[1]}/${parts[0]}"
    }

    /**
     * Lee el monto que escribe el admin: acepta «120», «120.5» o «120,50».
     * Devuelve null si no es un número, tiene más de 2 decimales, es 0 o pasa de 10000
     * (los mismos límites que valida la función SQL fee_set_plan).
     */
    fun parseAmount(text: String): Double? {
        val clean = text.trim().replace(',', '.')
        if (!Regex("""\d{1,5}(\.\d{1,2})?""").matches(clean)) return null
        val value = clean.toDoubleOrNull() ?: return null
        return value.takeIf { it > 0.0 && it <= 10000.0 }
    }

    /** Día de vencimiento válido: del 1 al 28 (para que exista también en febrero). */
    fun parseDueDay(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..28 }

    /** Con Yape, Plin o transferencia hay que dar el número de operación; en efectivo no existe. */
    fun needsReference(method: String): Boolean = method != "cash"

    /** El número de operación sirve si no hace falta o si tiene al menos 4 caracteres. */
    fun isReferenceValid(method: String, reference: String): Boolean =
        !needsReference(method) || reference.trim().length >= 4

    fun isOpen(status: String): Boolean = status == "pending" || status == "overdue"

    /** ¿Ya cumplió 18? Un jugador menor no ve ni paga su mensualidad: lo hace su apoderado. */
    fun isAdult(birthIso: String?, today: LocalDate = LocalDate.now()): Boolean {
        val birth = runCatching { LocalDate.parse(birthIso?.take(10)) }.getOrNull() ?: return false
        return Period.between(birth, today).years >= 18
    }
}
