package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.DocList
import com.sportpro.app.core.data.DocTeamRow
import com.sportpro.app.core.data.Docs
import com.sportpro.app.core.data.decodeRows
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Documentos del jugador (migración 009): DNI, ficha médica, seguro y autorización de viaje.
 *
 * Son datos sensibles de menores, por eso:
 *  - el bucket «documents» es PRIVADO: no existe una URL pública de ningún archivo;
 *  - para abrir uno se pide una URL firmada que vence en 5 minutos;
 *  - Storage y las funciones SQL deciden quién sube y quién ve. La app no decide nada.
 */
class DocumentRepository(private val client: SupabaseClient) {

    /** Documentos vigentes de un jugador y qué puedo hacer yo (subir / revisar). */
    suspend fun list(playerId: String): DocList =
        client.postgrest.rpc("doc_list", buildJsonObject { put("p_player", playerId) }).decodeScalar()

    /** Vista del DT / admin: qué le falta a cada jugador del plantel. */
    suspend fun teamStatus(teamId: String): List<DocTeamRow> =
        client.postgrest.rpc("doc_team_status", buildJsonObject { put("p_team", teamId) }).decodeRows()

    /**
     * Sube un documento en dos pasos:
     *  1) el archivo va a Storage, a la carpeta del jugador (Storage rechaza cualquier otra carpeta);
     *  2) se registra en la tabla con doc_register, que comprueba que el archivo existe de verdad.
     * Cada subida usa un nombre nuevo (UUID) y upsert = false: nunca se pisa un archivo anterior,
     * la versión vieja queda guardada como historial.
     */
    suspend fun upload(playerId: String, docType: String, bytes: ByteArray, mime: String, expiresIso: String?) {
        val path = Docs.pathFor(playerId, UUID.randomUUID().toString(), mime)
            ?: error("Formato de archivo no permitido")
        client.storage.from(BUCKET).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mime)
        }
        client.postgrest.rpc("doc_register", buildJsonObject {
            put("p_player", playerId)
            put("p_type", docType)
            put("p_path", path)
            put("p_mime", mime)
            put("p_expires", expiresIso)
        })
    }

    /** El DT o el admin aprueba, o rechaza con motivo. */
    suspend fun review(docId: String, accept: Boolean, reason: String?) {
        client.postgrest.rpc("doc_review", buildJsonObject {
            put("p_doc", docId)
            put("p_accept", accept)
            put("p_reason", reason)
        })
    }

    /** URL temporal para abrir el archivo. Vence sola a los 5 minutos; solo la obtiene quien tiene permiso de lectura. */
    suspend fun signedUrl(filePath: String): String =
        client.storage.from(BUCKET).createSignedUrl(filePath, 5.minutes)

    private companion object {
        const val BUCKET = "documents"
    }
}
