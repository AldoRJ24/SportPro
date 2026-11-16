package com.sportpro.app

import com.sportpro.app.core.data.NOTIFICATION_CATEGORIES
import com.sportpro.app.core.data.NotificationTarget
import com.sportpro.app.core.data.groupByDay
import com.sportpro.app.core.data.notificationDay
import com.sportpro.app.core.data.notificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** A dónde lleva cada notificación y cómo se agrupan por día. Sin red ni Android. */
class NotificationTargetsTest {

    /** Simula el JSON `data` de una notificación con un mapa. */
    private fun target(kind: String, vararg data: Pair<String, String>) =
        notificationTarget(kind) { key -> data.toMap()[key] }

    @Test
    fun `convocatoria y partido en vivo abren el partido`() {
        assertEquals(NotificationTarget.Match("m1"), target("callup", "match_id" to "m1"))
        assertEquals(NotificationTarget.Match("m1"), target("match_live", "match_id" to "m1"))
    }

    @Test
    fun `un comentario abre el feed`() {
        assertEquals(NotificationTarget.Feed, target("post_comment", "match_id" to "m1"))
        assertEquals(NotificationTarget.Feed, target("post_comment_removed"))
    }

    @Test
    fun `el pago por confirmar lleva al admin a los movimientos del jugador`() {
        assertEquals(
            NotificationTarget.TeamFees("t1", "p1"),
            target("fee_payment_reported", "team_id" to "t1", "player_id" to "p1"),
        )
    }

    @Test
    fun `los demas avisos de mensualidades llevan a mis mensualidades`() {
        listOf("fee_charge", "fee_payment_confirmed", "fee_payment_rejected", "fee_payment_voided", "fee_charge_waived", "fee_overdue")
            .forEach { kind -> assertEquals(kind, NotificationTarget.MyFees, target(kind, "team_id" to "t1")) }
    }

    @Test
    fun `los avisos de documentos abren los documentos del jugador`() {
        assertEquals(NotificationTarget.PlayerDocs("p1"), target("document_uploaded", "player_id" to "p1"))
        assertEquals(NotificationTarget.PlayerDocs("p1"), target("document_rejected", "player_id" to "p1"))
    }

    @Test
    fun `solicitudes y vinculos llevan al perfil`() {
        listOf("team_join_request", "team_join_answer", "team_player_removed", "guardian_request", "guardian_response", "invite_used")
            .forEach { kind -> assertEquals(kind, NotificationTarget.Profile, target(kind)) }
    }

    @Test
    fun `sin el id necesario o con tipo desconocido no hay destino`() {
        assertNull(target("callup"))                    // falta match_id
        assertNull(target("document_uploaded"))         // falta player_id
        assertNull(target("fee_payment_reported"))      // falta team_id
        assertNull(target("algo_nuevo", "match_id" to "m1"))
    }

    @Test
    fun `el dia se calcula en la zona horaria del celular`() {
        val lima = ZoneId.of("America/Lima")
        // 03:00 UTC del día 16 todavía es el día 15 a las 22:00 en Lima
        assertEquals(LocalDate.of(2026, 11, 15), notificationDay("2026-11-16T03:00:00+00:00", lima))
        assertEquals(LocalDate.of(2026, 11, 16), notificationDay("2026-11-16T15:00:00+00:00", lima))
        assertNull(notificationDay(null, lima))
        assertNull(notificationDay("no es fecha", lima))
    }

    @Test
    fun `agrupar por dia conserva el orden de llegada`() {
        val d1 = LocalDate.of(2026, 11, 16)
        val d2 = LocalDate.of(2026, 11, 15)
        val items = listOf("a" to d1, "b" to d1, "c" to d2, "d" to null, "e" to d2)
        val groups = groupByDay(items) { it.second }
        assertEquals(listOf(d1, d2, null), groups.map { it.first })
        assertEquals(listOf("a", "b"), groups[0].second.map { it.first })
        assertEquals(listOf("c", "e"), groups[1].second.map { it.first })
    }

    @Test
    fun `hay cinco categorias configurables`() {
        assertEquals(listOf("callups", "live", "comments", "requests", "payments"), NOTIFICATION_CATEGORIES)
    }
}
