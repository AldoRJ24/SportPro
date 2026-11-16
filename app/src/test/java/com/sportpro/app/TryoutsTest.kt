package com.sportpro.app

import com.sportpro.app.core.data.NotificationTarget
import com.sportpro.app.core.data.Tryout
import com.sportpro.app.core.data.TryoutDateFilter
import com.sportpro.app.core.data.TryoutEligibility
import com.sportpro.app.core.data.TryoutFormError
import com.sportpro.app.core.data.Tryouts
import com.sportpro.app.core.data.notificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Lógica pura de avisos de pruebas: filtro de fechas, edad, cupos y formulario. */
class TryoutsTest {

    private fun tryout(minAge: Int = 9, maxAge: Int = 12, slots: Int = 10, approved: Int = 0) = Tryout(
        id = "t1", academyId = "a1", title = "Prueba", category = "Sub-12", minAge = minAge, maxAge = maxAge,
        // 20:00 UTC = 15:00 en Lima del mismo día
        eventAt = "2026-12-01T20:00:00+00:00", district = "Surco", slots = slots, approvedCount = approved,
    )

    @Test
    fun `el filtro de fecha se convierte en un rango desde hoy`() {
        val today = LocalDate.of(2026, 11, 16)
        assertEquals(null to null, Tryouts.dateRange(TryoutDateFilter.ALL, today))
        assertEquals(today to LocalDate.of(2026, 11, 23), Tryouts.dateRange(TryoutDateFilter.WEEK, today))
        assertEquals(today to LocalDate.of(2026, 12, 16), Tryouts.dateRange(TryoutDateFilter.MONTH, today))
    }

    @Test
    fun `el dia de la prueba se calcula en hora de Peru`() {
        // 03:00 UTC del 2 de diciembre todavía es 1 de diciembre a las 22:00 en Lima
        assertEquals(LocalDate.of(2026, 12, 1), Tryouts.eventDay("2026-12-02T03:00:00+00:00"))
        assertNull(Tryouts.eventDay(null))
    }

    @Test
    fun `la edad se mide el dia de la prueba, no hoy`() {
        val t = tryout(minAge = 9, maxAge = 12)
        // Cumple 9 justo el día de la prueba: ya entra
        assertEquals(TryoutEligibility.OK, Tryouts.eligibility("2017-12-01", t))
        // Cumple 9 al día siguiente: todavía tiene 8
        assertEquals(TryoutEligibility.TOO_YOUNG, Tryouts.eligibility("2017-12-02", t))
        // Cumple 13 el día de la prueba: ya se pasó
        assertEquals(TryoutEligibility.TOO_OLD, Tryouts.eligibility("2013-12-01", t))
        assertEquals(TryoutEligibility.OK, Tryouts.eligibility("2013-12-02", t))
        assertEquals(TryoutEligibility.UNKNOWN, Tryouts.eligibility(null, t))
    }

    @Test
    fun `los cupos libres nunca son negativos`() {
        assertEquals(7, tryout(slots = 10, approved = 3).slotsLeft)
        assertEquals(0, tryout(slots = 2, approved = 2).slotsLeft)
        assertEquals(0, tryout(slots = 2, approved = 5).slotsLeft)
    }

    @Test
    fun `las opciones de filtro salen sin repetir y ordenadas`() {
        val list = listOf(
            tryout().copy(category = "Sub-15", district = "Surco"),
            tryout().copy(category = "Sub-12", district = "Miraflores"),
            tryout().copy(category = "Sub-15", district = ""),
        )
        assertEquals(listOf("Sub-12", "Sub-15"), Tryouts.options(list) { it.category })
        assertEquals(listOf("Miraflores", "Surco"), Tryouts.options(list) { it.district })
    }

    @Test
    fun `el formulario devuelve el primer problema que encuentra`() {
        val now = LocalDateTime.of(2026, 11, 16, 10, 0)
        val date = LocalDate.of(2026, 12, 1)
        val time = LocalTime.of(15, 0)
        fun check(
            title: String = "Prueba de arqueros", category: String = "Sub-12", min: Int? = 9, max: Int? = 12,
            d: LocalDate? = date, t: LocalTime? = time, venue: String = "Cancha 1", district: String = "Surco", slots: Int? = 20,
        ) = Tryouts.validateForm(title, category, min, max, d, t, venue, district, slots, now)

        assertNull(check())
        assertEquals(TryoutFormError.TITLE, check(title = " a "))
        assertEquals(TryoutFormError.CATEGORY, check(category = ""))
        assertEquals(TryoutFormError.AGES, check(min = 12, max = 9))
        assertEquals(TryoutFormError.AGES, check(min = null))
        assertEquals(TryoutFormError.DATE, check(t = null))
        assertEquals(TryoutFormError.PAST, check(d = LocalDate.of(2026, 11, 16), t = LocalTime.of(9, 0)))
        assertEquals(TryoutFormError.VENUE, check(venue = "ab"))
        assertEquals(TryoutFormError.DISTRICT, check(district = ""))
        assertEquals(TryoutFormError.SLOTS, check(slots = 0))
        assertEquals(TryoutFormError.SLOTS, check(slots = 201))
    }

    @Test
    fun `las notificaciones de pruebas abren el aviso`() {
        val data = mapOf("tryout_id" to "t1")
        assertEquals(NotificationTarget.Tryout("t1"), notificationTarget("tryout_application") { data[it] })
        assertEquals(NotificationTarget.Tryout("t1"), notificationTarget("tryout_answer") { data[it] })
        assertNull(notificationTarget("tryout_answer") { null })
    }
}
