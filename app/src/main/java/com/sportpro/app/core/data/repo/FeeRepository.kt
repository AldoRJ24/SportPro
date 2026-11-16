package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.FeeStatementRow
import com.sportpro.app.core.data.FeeTeamPlayer
import com.sportpro.app.core.data.FeeTeamSummary
import com.sportpro.app.core.data.decodeRows
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mensualidades (migración 008). Es SIMULADO: la app no mueve dinero, solo registra pagos.
 *
 * Todo pasa por funciones del servidor (RPC). La app nunca escribe directo en las tablas
 * fee_*: no tiene permiso, y así las reglas (quién paga, quién confirma, motivos, auditoría)
 * no se pueden saltar aunque alguien modifique el APK.
 */
class FeeRepository(private val client: SupabaseClient) {

    // ---- Lectura ----

    /** Equipos que gestiono con su deuda, vencidos y pagos por confirmar. */
    suspend fun summaryForMe(): List<FeeTeamSummary> =
        client.postgrest.rpc("fee_summary_for_me").decodeRows()

    /** Plantel del equipo: quién paga mensualidad y cómo va cada uno. */
    suspend fun teamPlayers(teamId: String): List<FeeTeamPlayer> =
        client.postgrest.rpc("fee_team_players", buildJsonObject { put("p_team", teamId) }).decodeRows()

    /**
     * Estado de cuenta. Sin parámetros devuelve lo que YO pago (mis hijos, o yo si soy adulto).
     * Con equipo y/o jugador es la vista del DT o del admin. Los parámetros nulos no se envían
     * para que el servidor use su valor por defecto.
     */
    suspend fun statement(teamId: String? = null, playerId: String? = null): List<FeeStatementRow> =
        client.postgrest.rpc("fee_statement", buildJsonObject {
            teamId?.let { put("p_team", it) }
            playerId?.let { put("p_player", it) }
        }).decodeRows()

    // ---- Configuración (admin fija el monto; DT o admin elige jugadores y genera cobros) ----

    suspend fun setPlan(teamId: String, amount: Double, dueDay: Int) {
        client.postgrest.rpc("fee_set_plan", buildJsonObject {
            put("p_team", teamId)
            put("p_amount", amount)
            put("p_due_day", dueDay)
        })
    }

    suspend fun setPlayer(teamId: String, playerId: String, enabled: Boolean) {
        client.postgrest.rpc("fee_set_player", buildJsonObject {
            put("p_team", teamId)
            put("p_player", playerId)
            put("p_enabled", enabled)
        })
    }

    /** Genera los cobros del mes actual. Devuelve cuántos NUEVOS creó (0 si ya existían: es idempotente). */
    suspend fun generateCharges(teamId: String): Int =
        client.postgrest.rpc("fee_generate_charges", buildJsonObject { put("p_team", teamId) }).decodeScalar()

    /** Avisa a las familias con cobros vencidos (máximo un aviso por cobro y por día). Devuelve cuántos envió. */
    suspend fun sendReminders(teamId: String): Int =
        client.postgrest.rpc("fee_send_reminders", buildJsonObject { put("p_team", teamId) }).decodeScalar()

    // ---- Pagos ----

    /** El apoderado (o el jugador adulto) informa que pagó; queda «por confirmar» por el admin. */
    suspend fun reportPayment(chargeId: String, method: String, reference: String?, note: String?) {
        client.postgrest.rpc("fee_report_payment", buildJsonObject {
            put("p_charge", chargeId)
            put("p_method", method)
            put("p_reference", reference)
            put("p_note", note)
        })
    }

    /** El admin registra un pago que recibió fuera de la app; queda confirmado al instante. */
    suspend fun registerPayment(chargeId: String, method: String, reference: String?, note: String?) {
        client.postgrest.rpc("fee_register_payment", buildJsonObject {
            put("p_charge", chargeId)
            put("p_method", method)
            put("p_reference", reference)
            put("p_note", note)
        })
    }

    /** El admin confirma o rechaza (con motivo) un pago informado. */
    suspend fun decidePayment(paymentId: String, accept: Boolean, reason: String?) {
        client.postgrest.rpc("fee_decide_payment", buildJsonObject {
            put("p_payment", paymentId)
            put("p_accept", accept)
            put("p_reason", reason)
        })
    }

    /** El admin anula un pago confirmado por error. No se borra: queda anulado con su motivo. */
    suspend fun voidPayment(paymentId: String, reason: String) {
        client.postgrest.rpc("fee_void_payment", buildJsonObject {
            put("p_payment", paymentId)
            put("p_reason", reason)
        })
    }

    /** El admin exonera un cobro pendiente (beca, lesión…) con motivo. */
    suspend fun waiveCharge(chargeId: String, reason: String) {
        client.postgrest.rpc("fee_waive_charge", buildJsonObject {
            put("p_charge", chargeId)
            put("p_reason", reason)
        })
    }
}
