package com.sportpro.app

import com.sportpro.app.core.data.AppJson
import com.sportpro.app.core.data.offline.PendingEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialización de la cola sin conexión. PendingEventQueue guarda en un archivo JSON la lista
 * de eventos pendientes y la vuelve a leer al abrir la app; aquí se comprueba ese ida y vuelta
 * con la misma configuración ([AppJson]) y sin tocar el disco.
 */
class PendingEventTest {

    private fun event(id: String, at: Long = 1_000L) = PendingEvent(
        clientEventId = id,
        matchId = "m1",
        label = "Gol · 12'",
        createdAtMs = at,
        // «body» es la fila tal cual se enviará a match_events
        body = buildJsonObject {
            put("match_id", "m1")
            put("client_event_id", id)
            put("minute", 12)
            put("minute_extra", 0)
            put("team_side", "home")
            put("player_id", JsonNull)
            put("payload", buildJsonObject { put("result", "converted") })
        },
    )

    @Test
    fun `una lista de eventos sobrevive a guardarse y leerse`() {
        val original = listOf(event("a", 1_000L), event("b", 2_000L))
        val text = AppJson.encodeToString(original)
        val restored = AppJson.decodeFromString<List<PendingEvent>>(text)
        assertEquals(original, restored)
    }

    @Test
    fun `el client_event_id se conserva, es lo que evita duplicados al reintentar`() {
        val restored = AppJson.decodeFromString<List<PendingEvent>>(AppJson.encodeToString(listOf(event("uuid-123"))))
        assertEquals("uuid-123", restored.single().clientEventId)
        assertEquals(JsonPrimitive("uuid-123"), restored.single().body["client_event_id"])
    }

    @Test
    fun `el cuerpo conserva numeros, nulos y objetos anidados`() {
        val body = AppJson.decodeFromString<List<PendingEvent>>(AppJson.encodeToString(listOf(event("a")))).single().body
        assertEquals(JsonPrimitive(12), body["minute"])
        assertEquals(JsonNull, body["player_id"])
        assertEquals(JsonPrimitive("converted"), (body["payload"] as JsonObject)["result"])
    }

    @Test
    fun `el orden de la lista se conserva para enviar en el orden en que se registraron`() {
        val original = (1..5).map { event("e$it", it * 1_000L) }
        val restored = AppJson.decodeFromString<List<PendingEvent>>(AppJson.encodeToString(original))
        assertEquals(original.map { it.clientEventId }, restored.map { it.clientEventId })
        assertEquals(restored.sortedBy { it.createdAtMs }, restored)
    }

    @Test
    fun `una cola vacia se guarda y se lee como lista vacia`() {
        assertEquals("[]", AppJson.encodeToString(emptyList<PendingEvent>()))
        assertTrue(AppJson.decodeFromString<List<PendingEvent>>("[]").isEmpty())
    }

    @Test
    fun `un archivo de una version futura con campos nuevos se sigue leyendo`() {
        // Si una actualización agrega un campo, la cola guardada por la versión nueva no debe
        // romper a quien todavía tiene la anterior (ignoreUnknownKeys en AppJson).
        val json = """[{"clientEventId":"a","matchId":"m1","label":"Gol","body":{"minute":3},"createdAtMs":5,"campoNuevo":true}]"""
        val restored = AppJson.decodeFromString<List<PendingEvent>>(json)
        assertEquals("a", restored.single().clientEventId)
        assertEquals(5L, restored.single().createdAtMs)
    }
}
