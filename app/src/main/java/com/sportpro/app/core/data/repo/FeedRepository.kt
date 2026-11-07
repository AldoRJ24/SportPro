package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.FeedPost
import com.sportpro.app.core.data.LikeResult
import com.sportpro.app.core.data.PostComment
import com.sportpro.app.core.data.decodeRows
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * PUBLICACIONES DE INICIO (migración 006). Cuando el DT aprueba el resumen con IA, el servidor lo
 * expone como publicación para su audiencia (equipo / academia / todos). Todo pasa por funciones
 * SQL que validan quién puede ver, comentar o moderar; la app no escribe en las tablas.
 */
class FeedRepository(private val client: SupabaseClient) {

    // Publicaciones que puedo ver, de la más reciente a la más antigua, con likes y comentarios.
    suspend fun feed(limit: Int = 30): List<FeedPost> =
        client.postgrest.rpc("get_feed", buildJsonObject { put("p_limit", limit) }).decodeRows()

    // Me gusta / quitar me gusta. Devuelve el estado final y el total (el servidor cuenta).
    suspend fun toggleLike(matchId: String): LikeResult =
        client.postgrest.rpc("toggle_post_like", buildJsonObject { put("p_match", matchId) }).decodeScalar()

    // Comentarios visibles (los eliminados no se devuelven). Menores aparecen como «Nombre I.».
    suspend fun comments(matchId: String): List<PostComment> =
        client.postgrest.rpc("get_post_comments", buildJsonObject { put("p_match", matchId) }).decodeRows()

    // Comentar: el servidor valida longitud (1–500), anti-spam y avisa a quien publicó.
    suspend fun addComment(matchId: String, body: String) {
        client.postgrest.rpc("add_post_comment", buildJsonObject {
            put("p_match", matchId)
            put("p_body", body)
        })
    }

    // Eliminar: mi comentario, o cualquiera si modero la publicación. Queda en la auditoría.
    suspend fun deleteComment(commentId: String) {
        client.postgrest.rpc("delete_post_comment", buildJsonObject { put("p_comment", commentId) })
    }

    // Moderación: activar o desactivar los comentarios de la publicación.
    suspend fun setCommentsEnabled(matchId: String, enabled: Boolean) {
        client.postgrest.rpc("set_post_comments_enabled", buildJsonObject {
            put("p_match", matchId)
            put("p_enabled", enabled)
        })
    }

    /** Tiempo real: avisa cuando alguien da like o comenta en publicaciones que puedo ver (RLS). */
    fun observe(): Flow<Unit> = channelFlow {
        val channel = client.channel("feed-${UUID.randomUUID()}")
        val likes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "post_likes" }
        val comments = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "post_comments" }
        launch { merge(likes, comments).collect { send(Unit) } }
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch { runCatching { client.realtime.removeChannel(channel) } }
        }
    }
}
