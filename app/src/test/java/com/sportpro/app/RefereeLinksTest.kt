package com.sportpro.app

import com.sportpro.app.core.RefereeLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Enlace del QR del árbitro: el código va después de «#» y solo se arma con datos válidos. */
class RefereeLinksTest {

    private val token = "0123456789abcdef".repeat(4)

    @Test
    fun `el codigo va despues del numeral`() {
        assertEquals("https://arbitro.vercel.app/#$token", RefereeLinks.linkFor("https://arbitro.vercel.app", token))
    }

    @Test
    fun `se quita la barra final de la direccion`() {
        assertEquals("https://arbitro.vercel.app/#$token", RefereeLinks.linkFor("https://arbitro.vercel.app/", token))
    }

    @Test
    fun `sin direccion configurada o sin https no hay enlace`() {
        assertNull(RefereeLinks.linkFor("", token))
        assertNull(RefereeLinks.linkFor(null, token))
        assertNull(RefereeLinks.linkFor("http://arbitro.vercel.app", token))
        assertNull(RefereeLinks.linkFor("https://", token))
    }

    @Test
    fun `un codigo mal formado no genera enlace`() {
        assertNull(RefereeLinks.linkFor("https://arbitro.vercel.app", null))
        assertNull(RefereeLinks.linkFor("https://arbitro.vercel.app", "abc"))
        assertNull(RefereeLinks.linkFor("https://arbitro.vercel.app", "z".repeat(64)))
    }

    @Test
    fun `el codigo se normaliza a minusculas`() {
        assertEquals("https://a.vercel.app/#$token", RefereeLinks.linkFor("https://a.vercel.app", token.uppercase()))
    }
}
