package com.sportpro.app

import com.sportpro.app.core.data.AppJson
import com.sportpro.app.core.data.FeeStatementRow
import com.sportpro.app.core.data.Fees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Lógica pura de mensualidades: no usa red ni Android, corre en la PC con `gradlew testDebugUnitTest`. */
class FeesTest {

    @Test
    fun `money usa siempre punto decimal y dos decimales`() {
        assertEquals("S/ 120.00", Fees.money(120.0))
        assertEquals("S/ 99.50", Fees.money(99.5))
    }

    @Test
    fun `periodLabel convierte el primer dia del mes en nombre de mes`() {
        assertEquals("Noviembre 2026", Fees.periodLabel("2026-11-01"))
        assertEquals("Enero 2027", Fees.periodLabel("2027-01-01"))
        // Texto que no es fecha: se devuelve igual en vez de fallar
        assertEquals("abc", Fees.periodLabel("abc"))
        assertEquals("", Fees.periodLabel(null))
    }

    @Test
    fun `dateLabel pasa de formato ISO a dia mes anio`() {
        assertEquals("10/11/2026", Fees.dateLabel("2026-11-10"))
        assertEquals("", Fees.dateLabel(null))
    }

    @Test
    fun `parseAmount acepta punto o coma y rechaza montos invalidos`() {
        assertEquals(120.0, Fees.parseAmount("120")!!, 0.0)
        assertEquals(120.5, Fees.parseAmount(" 120,5 ")!!, 0.0)
        assertEquals(10000.0, Fees.parseAmount("10000")!!, 0.0)
        assertNull(Fees.parseAmount("0"))            // tiene que ser mayor que 0
        assertNull(Fees.parseAmount("10000.01"))     // pasa del máximo
        assertNull(Fees.parseAmount("12.345"))       // más de 2 decimales
        assertNull(Fees.parseAmount("-5"))
        assertNull(Fees.parseAmount("abc"))
        assertNull(Fees.parseAmount(""))
    }

    @Test
    fun `parseDueDay solo acepta del 1 al 28`() {
        assertEquals(1, Fees.parseDueDay("1"))
        assertEquals(28, Fees.parseDueDay(" 28 "))
        assertNull(Fees.parseDueDay("0"))
        assertNull(Fees.parseDueDay("29"))
        assertNull(Fees.parseDueDay("x"))
    }

    @Test
    fun `el numero de operacion es obligatorio salvo en efectivo`() {
        assertTrue(Fees.isReferenceValid("cash", ""))
        assertFalse(Fees.isReferenceValid("yape", ""))
        assertFalse(Fees.isReferenceValid("plin", "123"))
        assertTrue(Fees.isReferenceValid("transfer", "1234"))
    }

    @Test
    fun `isAdult cambia justo el dia que cumple 18`() {
        val today = LocalDate.of(2026, 11, 15)
        assertTrue(Fees.isAdult("2008-11-15", today))
        assertFalse(Fees.isAdult("2008-11-16", today))
        assertFalse(Fees.isAdult(null, today))
        assertFalse(Fees.isAdult("no-es-fecha", today))
    }

    @Test
    fun `la fila del estado de cuenta se lee del JSON del servidor`() {
        // Así llega una fila de fee_statement: cobro vencido con un pago informado por confirmar
        val json = """
            {"charge_id":"c1","team_id":"t1","team_name":"Halcones","player_id":"p1","player_name":"Jugador Uno",
             "period":"2026-11-01","amount":120.00,"currency":"PEN","due_date":"2026-11-10","status":"overdue",
             "waived_reason":null,"can_pay":true,"can_admin":false,"payment_id":"y1","payment_status":"reported",
             "method":"yape","reference":"123456","note":null,"paid_on":"2026-11-15","columna_nueva":"se ignora"}
        """.trimIndent()
        val row = AppJson.decodeFromString<FeeStatementRow>(json)
        assertEquals(120.0, row.amount, 0.0)
        assertTrue(row.isOpen)
        assertTrue(row.isAwaitingReview)
        assertTrue(row.canPay)
        assertNull(row.note)
    }
}
