package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.AppJson
import com.sportpro.app.core.data.VoiceParse
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Respuesta de voice_access: si puedo usar el asistente y, si no, por qué. */
@Serializable
data class VoiceAccess(val allowed: Boolean = false, val reason: String? = null, val plan: String? = null, val remaining: Int? = null)

/**
 * Modo manos libres (migración 024 + Edge Function «voice-match-parse»).
 * El celular transcribe la voz; aquí solo se manda el TEXTO para que la IA lo entienda.
 * El audio nunca sale del celular.
 */
class VoiceRepository(private val client: SupabaseClient) {

    suspend fun access(): VoiceAccess = client.postgrest.rpc("voice_access").decodeScalar()

    /**
     * Entiende una frase. [question] y [options] son lo último que preguntó el asistente: así
     * una respuesta corta («la segunda», «a las 4») se interpreta bien.
     * Los errores de negocio (sin plan, no entendí) vuelven dentro de [VoiceParse.error].
     */
    suspend fun parse(text: String, question: String?, options: List<String>): VoiceParse {
        val response = client.functions.invoke(function = "voice-match-parse") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("text", text)
                question?.let { put("question", it) }
                put("options", JsonArray(options.map { JsonPrimitive(it) }))
            }.toString())
            timeout { requestTimeoutMillis = 30_000 }
        }
        return AppJson.decodeFromString(VoiceParse.serializer(), response.bodyAsText())
    }

    /** Descuenta 1 uso de IA: se llama una vez, cuando el asistente creó el partido. */
    suspend fun recordUsage(id: String?) {
        client.postgrest.rpc("voice_usage_record", buildJsonObject { put("p_match", id) })
    }
}
