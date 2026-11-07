package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.Callup
import com.sportpro.app.core.data.EventType
import com.sportpro.app.core.data.ExternalDraft
import com.sportpro.app.core.data.ExternalPlayer
import com.sportpro.app.core.data.IdRow
import com.sportpro.app.core.data.LineupPlayer
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.OperatorRow
import com.sportpro.app.core.data.SummaryRow
import com.sportpro.app.core.data.TimelineEvent
import com.sportpro.app.core.data.decodeRows
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Repositorio de PARTIDOS: única puerta entre la app y las tablas/RPC de partidos en Supabase.
 *
 * Lógica general:
 * - Las lecturas usan PostgREST (select) o RPC; las RLS del servidor deciden qué filas ve cada usuario.
 * - Lo que cambia el estado del partido (iniciar, entretiempo, finalizar, corregir, anular) se hace
 *   con funciones SQL SECURITY DEFINER que validan permisos y reglas EN EL SERVIDOR, así una app
 *   modificada no puede saltarse las reglas.
 * - Los eventos se insertan en match_events; triggers del servidor les asignan número de secuencia
 *   y autor, validan los datos y recalculan el marcador.
 * - Realtime: se escuchan cambios de Postgres y la pantalla vuelve a pedir los datos por RPC.
 */
class MatchRepository(private val client: SupabaseClient) {

    private fun uid(): String = client.auth.currentUserOrNull()?.id ?: error("No autenticado")

    // ---------------- Read ----------------
    suspend fun listMatches(): List<MatchRow> =
        client.from("matches").select(Columns.raw(MATCH_COLS)) {
            order("scheduled_at", Order.DESCENDING)
            limit(100)
        }.decodeRows()

    suspend fun getMatch(matchId: String): MatchRow? =
        client.from("matches").select(Columns.raw(MATCH_COLS)) { filter { eq("id", matchId) } }
            .decodeRows<MatchRow>().firstOrNull()

    // Cronología del partido. La RPC devuelve los eventos con los nombres ya filtrados por privacidad
    // (un menor aparece como «Nombre + inicial» según quién consulta).
    suspend fun timeline(matchId: String): List<TimelineEvent> =
        client.postgrest.rpc("get_match_timeline", buildJsonObject { put("p_match", matchId) }).decodeRows()

    // Alineación de ambos lados (titular/suplente, dorsal y posición), incluido el rival externo.
    suspend fun lineup(matchId: String): List<LineupPlayer> =
        client.postgrest.rpc("get_match_lineup", buildJsonObject { put("p_match", matchId) }).decodeRows()

    // Catálogo configurable de tipos de evento (gol, tarjeta, cambio...): solo activos y en orden.
    suspend fun eventTypes(): List<EventType> =
        client.from("event_types")
            .select(Columns.raw("id,academy_id,code,name,icon,group_kind,scoring_rule,required_payload,is_active,sort_order")) {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }.decodeRows()

    // Permisos del usuario en ESTE partido, calculados por el servidor:
    // manager = DT/admin del equipo local; operator = persona designada para registrar en vivo.
    // Si la consulta falla se asume «sin permiso» (falla segura).
    suspend fun isManager(matchId: String): Boolean =
        runCatching {
            client.postgrest.rpc("is_match_manager", buildJsonObject { put("p_match", matchId) }).decodeScalar<Boolean>()
        }.getOrDefault(false)

    suspend fun isOperator(matchId: String): Boolean =
        runCatching {
            client.postgrest.rpc("is_match_operator", buildJsonObject { put("p_match", matchId) }).decodeScalar<Boolean>()
        }.getOrDefault(false)

    suspend fun currentOperator(matchId: String): OperatorRow? =
        client.from("match_operators").select(Columns.raw("user_id,active")) {
            filter {
                eq("match_id", matchId)
                eq("active", true)
            }
        }.decodeRows<OperatorRow>().firstOrNull()

    // ---------------- Live control ----------------
    /**
     * Cambia el estado del partido. action = start | halftime | second_half | finish.
     * La función del servidor change_match_state, en una sola transacción:
     *  1. verifica que quien llama sea el DT o el operador;
     *  2. verifica que la transición sea válida (programado → en vivo → entretiempo → 2.º tiempo → final);
     *  3. al iniciar, exige las alineaciones con los titulares completos;
     *  4. inserta el evento de control (inicio, fin del 1.er tiempo...) y actualiza el reloj
     *     (period_started_at + elapsed_before_seconds), con el que cada celular calcula el minuto.
     */
    suspend fun changeState(matchId: String, action: String) {
        client.postgrest.rpc("change_match_state", buildJsonObject {
            put("p_match", matchId)
            put("p_action", action)
        })
    }

    /**
     * REGISTRAR UN EVENTO (gol, tarjeta, cambio...). Se inserta una fila en match_events y el servidor
     * completa el resto con triggers, en este orden:
     *  a) prepare: bloquea el partido, asigna seq (orden de llegada), created_by = usuario y marca
     *     el evento como «tardío» si el partido ya había terminado;
     *  b) validate: tipo activo y de la academia, equipo obligatorio, datos obligatorios del tipo
     *     (por ejemplo, el resultado del penal) y que el jugador pertenezca a ese equipo;
     *  c) score: recalcula el marcador con los eventos vigentes (el autogol suma al rival).
     * client_event_id es un UUID creado en el celular: si el mismo evento llega dos veces (reintento
     * sin conexión) la base lo rechaza como duplicado (código 23505) y nunca se repite.
     * RLS: solo el operador o el DT pueden insertar (can_record_event).
     */
    suspend fun insertEvent(body: JsonObject): String =
        client.from("match_events").insert(buildJsonArray { add(body) }) { select(Columns.raw("id")) }
            .decodeRows<IdRow>().first().id

    // Busca el id real de un evento a partir del UUID del celular (para «Deshacer» tras un reintento).
    suspend fun eventIdByClientId(matchId: String, clientEventId: String): String? =
        client.from("match_events").select(Columns.raw("id")) {
            filter {
                eq("match_id", matchId)
                eq("client_event_id", clientEventId)
            }
        }.decodeRows<IdRow>().firstOrNull()?.id

    // ANULAR: el evento no se borra; queda con status = voided, quién, cuándo y el motivo.
    // Un trigger guarda la versión anterior en match_event_revisions y el marcador se recalcula.
    suspend fun voidEvent(eventId: String, reason: String) {
        client.postgrest.rpc("void_match_event", buildJsonObject {
            put("p_event", eventId)
            put("p_reason", reason)
        })
    }

    /**
     * CORREGIR un evento. patch lleva solo las claves que cambian: minute, minute_extra, team_side,
     * player_id, external_player_id, payload, notes.
     * El servidor exige un motivo, guarda la versión anterior (auditoría) y vuelve a validar el evento.
     * Permisos: el DT siempre; el operador solo mientras el partido está en juego.
     */
    suspend fun correctEvent(eventId: String, patch: JsonObject, reason: String) {
        client.postgrest.rpc("correct_match_event", buildJsonObject {
            put("p_event", eventId)
            put("p_patch", patch)
            put("p_reason", reason)
        })
    }

    // ---------------- Setup (US-04, US-09, D-14, D-15, D-16) ----------------
    /**
     * CREAR PARTIDO. El id se genera en el celular para insertar enseguida el plantel del rival
     * externo con ese mismo id. El trigger del servidor deriva la academia del equipo local y valida
     * que el rival sea un equipo registrado O un nombre externo.
     */
    suspend fun createMatch(
        homeTeamId: String,
        awayTeamId: String?,
        awayExternalName: String?,
        formatPlayers: Int,
        halfMinutes: Int,
        scheduledAtIso: String,
        venue: String?,
        visibility: String,
        externalRoster: List<ExternalDraft>,
    ): String {
        val id = UUID.randomUUID().toString()
        client.from("matches").insert(buildJsonArray {
            add(buildJsonObject {
                put("id", id)
                put("home_team_id", homeTeamId)
                if (awayTeamId != null) put("away_team_id", awayTeamId) else put("away_external_name", awayExternalName)
                put("format_players", formatPlayers)
                put("half_minutes", halfMinutes)
                put("scheduled_at", scheduledAtIso)
                if (!venue.isNullOrBlank()) put("venue", venue)
                put("visibility", visibility)
            })
        })
        if (awayTeamId == null && externalRoster.isNotEmpty()) addExternalPlayers(id, externalRoster)
        return id
    }

    // Plantel del rival externo (jugadores sin cuenta): nombre, dorsal, titular/suplente y posición.
    suspend fun addExternalPlayers(matchId: String, roster: List<ExternalDraft>) {
        if (roster.isEmpty()) return
        client.from("match_external_players").insert(buildJsonArray {
            roster.forEach { r ->
                add(buildJsonObject {
                    put("match_id", matchId)
                    put("name", r.name.trim())
                    r.jersey.toIntOrNull()?.let { put("jersey_number", it) }
                    put("is_starter", r.starter)
                    put("field_position", r.position?.code)
                })
            }
        })
    }

    suspend fun updateExternal(id: String, jersey: Int?, name: String, position: String?, starter: Boolean) {
        client.from("match_external_players").update(buildJsonObject {
            put("jersey_number", jersey)
            put("name", name.trim())
            put("field_position", position)
            put("is_starter", starter)
        }) { filter { eq("id", id) } }
    }

    suspend fun deleteExternal(id: String) {
        client.from("match_external_players").delete { filter { eq("id", id) } }
    }

    suspend fun externalPlayers(matchId: String): List<ExternalPlayer> =
        client.from("match_external_players").select(Columns.raw("id,name,jersey_number,is_starter,field_position")) {
            filter { eq("match_id", matchId) }
            order("jersey_number", Order.ASCENDING)
        }.decodeRows()

    // CONVOCATORIA (US-04): jugadores llamados al partido y su estado (pending / confirmed / declined).
    suspend fun callups(matchId: String): List<Callup> =
        client.from("match_callups")
            .select(Columns.raw("id,match_id,team_id,player_id,status,player:players(id,first_name,last_name,birth_date,position)")) {
                filter { eq("match_id", matchId) }
            }.decodeRows()

    suspend fun addCallups(matchId: String, teamId: String, playerIds: List<String>) {
        if (playerIds.isEmpty()) return
        client.from("match_callups").insert(buildJsonArray {
            playerIds.forEach { pid ->
                add(buildJsonObject {
                    put("match_id", matchId)
                    put("team_id", teamId)
                    put("player_id", pid)
                })
            }
        })
    }

    suspend fun removeCallup(callupId: String) {
        client.from("match_callups").delete { filter { eq("id", callupId) } }
    }

    // El DT registra la asistencia confirmada; el servidor guarda quién respondió y cuándo.
    suspend fun setCallupStatus(callupId: String, status: String) {
        client.from("match_callups").update(buildJsonObject { put("status", status) }) { filter { eq("id", callupId) } }
    }

    /** One lineup row: player, jersey, starter or substitute, and field position (POR/DEF/MED/DEL). */
    data class LineupEntry(val playerId: String, val jersey: Int?, val starter: Boolean, val position: String?)

    /**
     * Guarda la alineación de un lado: borra la anterior e inserta la nueva (titulares + suplentes).
     * El servidor solo acepta jugadores CONFIRMADOS en la convocatoria y del equipo correcto; al
     * iniciar el partido exige los titulares completos con exactamente 1 arquero.
     */
    suspend fun saveLineup(matchId: String, side: String, entries: List<LineupEntry>) {
        client.from("match_lineup_players").delete {
            filter {
                eq("match_id", matchId)
                eq("team_side", side)
            }
        }
        val rows: JsonArray = buildJsonArray {
            entries.forEach { e ->
                add(buildJsonObject {
                    put("match_id", matchId)
                    put("team_side", side)
                    put("player_id", e.playerId)
                    put("is_starter", e.starter)
                    put("field_position", e.position)
                    if (e.jersey != null) put("jersey_number", e.jersey)
                })
            }
        }
        if (rows.isNotEmpty()) client.from("match_lineup_players").insert(rows)
    }

    // Designa al OPERADOR (quien registra en vivo). El servidor deja un solo operador activo a la vez
    // y solo el DT/admin del equipo puede designarlo.
    suspend fun setOperator(matchId: String, userId: String) {
        client.postgrest.rpc("set_match_operator", buildJsonObject {
            put("p_match", matchId)
            put("p_user", userId)
        })
    }

    // ---------------- AI summary (US-02, §9.5) ----------------
    /**
     * RESUMEN CON IA. Llama a la Edge Function «generate-match-summary», que en el servidor:
     * verifica que seas el DT y que el partido haya terminado, arma los eventos con seudónimos
     * (J1, R1...) sin nombres de menores, pide el borrador a Gemini, comprueba que no invente
     * jugadores y guarda el borrador. La clave de Gemini vive solo en el servidor.
     */
    suspend fun generateSummary(matchId: String): String {
        val response = client.functions.invoke(
            function = "generate-match-summary",
            body = buildJsonObject { put("match_id", matchId) },
        )
        return response.bodyAsText()
    }

    suspend fun summary(matchId: String): SummaryRow? =
        client.from("match_summaries")
            .select(Columns.raw("match_id,status,ai_text,final_text,generation_count,visibility")) {
                filter { eq("match_id", matchId) }
            }.decodeRows<SummaryRow>().firstOrNull()

    // El DT edita el borrador (cada edición queda versionada); approveSummary lo publica.
    suspend fun saveSummaryEdit(matchId: String, text: String) {
        client.postgrest.rpc("save_summary_edit", buildJsonObject {
            put("p_match", matchId)
            put("p_text", text)
        })
    }

    suspend fun approveSummary(matchId: String, visibility: String) {
        client.postgrest.rpc("approve_summary", buildJsonObject {
            put("p_match", matchId)
            put("p_visibility", visibility)
        })
    }

    // ---------------- Realtime ----------------
    /**
     * TIEMPO REAL. Abre un canal de Supabase Realtime y emite cada vez que cambia el partido, alguno de
     * sus eventos o el catálogo. No se usan los datos del aviso: la pantalla vuelve a pedir la
     * cronología por RPC, que aplica las reglas de privacidad. Así los 2 celulares ven lo mismo en
     * 1–2 segundos. Al cerrar la pantalla el canal se elimina (awaitClose).
     */
    fun observeMatch(matchId: String): Flow<Unit> = channelFlow {
        val channel = client.channel("match-$matchId-${UUID.randomUUID()}")
        val matchFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "matches"
            filter("id", FilterOperator.EQ, matchId)
        }
        val eventsFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "match_events"
            filter("match_id", FilterOperator.EQ, matchId)
        }
        val catalogFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "event_types"
        }
        // Las 3 suscripciones se combinan en una sola señal: «algo cambió, vuelve a leer».
        launch { merge(matchFlow, eventsFlow, catalogFlow).collect { send(Unit) } }
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    /** Avisa de cualquier cambio en los partidos visibles (listas y franja «En vivo»). */
    fun observeMatches(): Flow<Unit> = channelFlow {
        val channel = client.channel("matches-${UUID.randomUUID()}")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "matches" }
        launch { flow.collect { send(Unit) } }
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    /** Avisa cuando cambia la convocatoria de un partido (respuestas de apoderados o jugadores). */
    fun observeCallups(matchId: String): Flow<Unit> = channelFlow {
        val channel = client.channel("callups-$matchId-${UUID.randomUUID()}")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "match_callups"
            filter("match_id", FilterOperator.EQ, matchId)
        }
        launch { flow.collect { send(Unit) } }
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    companion object {
        const val MATCH_COLS =
            "id,academy_id,home_team_id,away_team_id,away_external_name,format_players,half_minutes,scheduled_at,venue," +
                "visibility,status,period,period_started_at,elapsed_before_seconds,clock_running,finished_at,home_score,away_score," +
                "home:teams!home_team_id(id,name,category,academy_id,crest_path)," +
                "away:teams!away_team_id(id,name,category,academy_id,crest_path)"
    }
}
