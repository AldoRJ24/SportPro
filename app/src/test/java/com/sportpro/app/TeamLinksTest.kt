package com.sportpro.app

import com.sportpro.app.core.TeamLinks
import com.sportpro.app.ui.teams.normalizeTeamCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El código de equipo debe leerse igual venga del QR (enlace), escrito a mano o con espacios. */
class TeamLinksTest {

    @Test
    fun `el enlace del QR contiene el codigo y se vuelve a leer igual`() {
        val link = TeamLinks.linkFor("EQ-ABCD-2345")
        assertEquals("sportpro://team/EQ-ABCD-2345", link)
        assertEquals("EQ-ABCD-2345", TeamLinks.parse(link))
    }

    @Test
    fun `acepta el codigo escrito de varias formas`() {
        assertEquals("EQ-ABCD-2345", normalizeTeamCode("EQ-ABCD-2345"))
        assertEquals("EQ-ABCD-2345", normalizeTeamCode(" eq abcd 2345 "))
        assertEquals("EQ-ABCD-2345", normalizeTeamCode("abcd2345"))
        assertEquals("EQ-ABCD-2345", normalizeTeamCode("SPORTPRO://TEAM/eq-abcd-2345"))
    }

    @Test
    fun `rechaza textos que no son un codigo de equipo`() {
        assertNull(normalizeTeamCode(null))
        assertNull(normalizeTeamCode(""))
        assertNull(normalizeTeamCode("EQ-ABC"))
        assertNull(normalizeTeamCode("sportpro://invite/DT-ABCD-2345-99"))
        assertNull(normalizeTeamCode("https://example.com/"))
    }

    @Test
    fun `el codigo pendiente se entrega una sola vez`() {
        TeamLinks.offer("sportpro://team/EQ-ABCD-2345")
        assertEquals("EQ-ABCD-2345", TeamLinks.consume())
        assertNull(TeamLinks.consume())
    }
}
