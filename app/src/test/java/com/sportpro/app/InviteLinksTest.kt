package com.sportpro.app

import com.sportpro.app.core.InviteLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Códigos de invitación que llegan por QR o deep link (sportpro://invite/CODIGO). */
class InviteLinksTest {

    @Test
    fun `el enlace del QR contiene el codigo y se vuelve a leer igual`() {
        val link = InviteLinks.linkFor("DT-ABCD-2345")
        assertEquals("sportpro://invite/DT-ABCD-2345", link)
        assertEquals("DT-ABCD-2345", InviteLinks.parse(link))
    }

    @Test
    fun `acepta el codigo solo, en minusculas y con espacios alrededor`() {
        assertEquals("ADM-ABCD-2345", InviteLinks.parse("ADM-ABCD-2345"))
        assertEquals("ADM-ABCD-2345", InviteLinks.parse("  adm-abcd-2345  "))
        assertEquals("DT-ABCD-2345", InviteLinks.parse("SPORTPRO://INVITE/dt-abcd-2345"))
    }

    @Test
    fun `quita los caracteres que no son letras, numeros ni guiones`() {
        assertEquals("DT-ABCD-2345", InviteLinks.parse("DT-ABCD-2345\n"))
        assertEquals("DT-ABCD-2345", InviteLinks.parse("DT-AB CD-23 45"))
    }

    @Test
    fun `rechaza textos vacios o demasiado cortos`() {
        assertNull(InviteLinks.parse(null))
        assertNull(InviteLinks.parse(""))
        assertNull(InviteLinks.parse("   "))
        assertNull(InviteLinks.parse("DT-12"))
        assertNull(InviteLinks.parse("sportpro://invite/"))
    }

    @Test
    fun `la invitacion pendiente se entrega una sola vez`() {
        InviteLinks.clear()
        InviteLinks.offer("sportpro://invite/DT-ABCD-2345")
        assertEquals("DT-ABCD-2345", InviteLinks.pending.value)
        assertEquals("DT-ABCD-2345", InviteLinks.consume())
        assertNull(InviteLinks.consume())
    }

    @Test
    fun `un enlace invalido no pisa la invitacion pendiente`() {
        InviteLinks.clear()
        InviteLinks.offer("DT-ABCD-2345")
        InviteLinks.offer("x")
        assertEquals("DT-ABCD-2345", InviteLinks.consume())
    }
}
