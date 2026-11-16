package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.MyTryoutApplication
import com.sportpro.app.core.data.Tryout
import com.sportpro.app.core.data.TryoutApplicant
import com.sportpro.app.core.data.decodeRows
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Avisos de pruebas e inscripciones (migración 011).
 * Todo pasa por funciones del servidor: la app no puede escribir en las tablas tryout*.
 */
class TryoutRepository(private val client: SupabaseClient) {

    /**
     * Busca avisos. Los filtros se aplican EN EL SERVIDOR; un filtro nulo no se envía y el
     * servidor lo trata como «sin filtro». Las fechas van en formato ISO (2026-11-30).
     */
    suspend fun search(
        category: String? = null, position: String? = null,
        fromIso: String? = null, toIso: String? = null, district: String? = null,
    ): List<Tryout> =
        client.postgrest.rpc("tryout_search", buildJsonObject {
            category?.let { put("p_category", it) }
            position?.let { put("p_position", it) }
            fromIso?.let { put("p_from", it) }
            toIso?.let { put("p_to", it) }
            district?.let { put("p_district", it) }
        }).decodeRows()

    /** Publica un aviso (admin o DT de una academia verificada). Devuelve su id. */
    suspend fun create(
        academyId: String, title: String, category: String, minAge: Int, maxAge: Int, positions: List<String>,
        eventAtIso: String, venue: String, district: String, slots: Int, notes: String?,
    ): String =
        client.postgrest.rpc("tryout_create", buildJsonObject {
            put("p_academy", academyId)
            put("p_title", title)
            put("p_category", category)
            put("p_min_age", minAge)
            put("p_max_age", maxAge)
            put("p_positions", JsonArray(positions.map { JsonPrimitive(it) }))
            put("p_event_at", eventAtIso)
            put("p_venue", venue)
            put("p_district", district)
            put("p_slots", slots)
            put("p_notes", notes)
        }).decodeScalar()

    /** Cierra (open → closed), reabre o cancela un aviso que gestiono. */
    suspend fun setStatus(tryoutId: String, status: String) {
        client.postgrest.rpc("tryout_set_status", buildJsonObject {
            put("p_tryout", tryoutId)
            put("p_status", status)
        })
    }

    /** Inscribe a mi hijo (o a mí si soy adulto). El servidor valida la edad y los cupos. */
    suspend fun apply(tryoutId: String, playerId: String) {
        client.postgrest.rpc("tryout_apply", buildJsonObject {
            put("p_tryout", tryoutId)
            put("p_player", playerId)
        })
    }

    /** Retira una inscripción que yo envié. No se borra: queda «retirada». */
    suspend fun withdraw(applicationId: String) {
        client.postgrest.rpc("tryout_withdraw", buildJsonObject { put("p_application", applicationId) })
    }

    /** Quien gestiona el aviso acepta o rechaza una inscripción. */
    suspend fun decide(applicationId: String, accept: Boolean, reason: String?) {
        client.postgrest.rpc("tryout_decide", buildJsonObject {
            put("p_application", applicationId)
            put("p_accept", accept)
            put("p_reason", reason)
        })
    }

    /** Inscripciones de un aviso (solo quien lo gestiona). */
    suspend fun applicants(tryoutId: String): List<TryoutApplicant> =
        client.postgrest.rpc("tryout_applications_list", buildJsonObject { put("p_tryout", tryoutId) }).decodeRows()

    /** Las inscripciones que yo envié, con su estado. */
    suspend fun mine(): List<MyTryoutApplication> =
        client.postgrest.rpc("my_tryout_applications").decodeRows()
}
