package com.sportpro.app

import com.sportpro.app.core.data.AppJson
import com.sportpro.app.core.data.DocList
import com.sportpro.app.core.data.DocState
import com.sportpro.app.core.data.Docs
import com.sportpro.app.core.data.PlayerDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura de documentos: rutas en Storage, límites y estado que se muestra. */
class DocsTest {

    private fun doc(status: String, expired: Boolean = false) =
        PlayerDoc(id = "d1", docType = "dni", filePath = "p1/d1.pdf", status = status, isExpired = expired)

    @Test
    fun `la ruta empieza por la carpeta del jugador y usa la extension del formato`() {
        assertEquals("p1/d1.pdf", Docs.pathFor("p1", "d1", "application/pdf"))
        assertEquals("p1/d1.jpg", Docs.pathFor("p1", "d1", "image/jpeg"))
        assertEquals("p1/d1.png", Docs.pathFor("p1", "d1", "image/png"))
    }

    @Test
    fun `un formato no permitido no tiene ruta`() {
        assertNull(Docs.pathFor("p1", "d1", "application/zip"))
        assertNull(Docs.pathFor("p1", "d1", null))
        assertNull(Docs.extensionFor("video/mp4"))
    }

    @Test
    fun `el limite de tamano es 5 MB y un archivo vacio no vale`() {
        assertFalse(Docs.isSizeOk(0))
        assertTrue(Docs.isSizeOk(1))
        assertTrue(Docs.isSizeOk(5 * 1024 * 1024))
        assertFalse(Docs.isSizeOk(5 * 1024 * 1024 + 1))
    }

    @Test
    fun `sin documento se muestra FALTA si es obligatorio y OPCIONAL si no`() {
        assertEquals(DocState.MISSING, Docs.stateOf(null, required = true))
        assertEquals(DocState.OPTIONAL, Docs.stateOf(null, required = false))
    }

    @Test
    fun `un documento aprobado pero vencido se muestra como vencido`() {
        assertEquals(DocState.APPROVED, Docs.stateOf(doc("approved"), required = true))
        assertEquals(DocState.EXPIRED, Docs.stateOf(doc("approved", expired = true), required = true))
        assertEquals(DocState.PENDING, Docs.stateOf(doc("pending"), required = true))
        // Rechazado gana a vencido: lo primero que debe saber la familia es que no fue aceptado
        assertEquals(DocState.REJECTED, Docs.stateOf(doc("rejected", expired = true), required = true))
    }

    @Test
    fun `la respuesta de doc_list se lee del JSON del servidor`() {
        val json = """
            {"can_upload":true,"can_review":false,"required":["dni","medical","insurance"],
             "docs":[{"id":"d1","doc_type":"dni","file_path":"p1/d1.pdf","mime_type":"application/pdf",
                      "expires_on":null,"status":"approved","review_reason":null,"is_expired":false,
                      "uploaded_by_name":"Paola","created_at":"2026-11-15T10:00:00+00:00",
                      "reviewed_by_name":"Diego","reviewed_at":"2026-11-15T11:00:00+00:00"}]}
        """.trimIndent()
        val list = AppJson.decodeFromString<DocList>(json)
        assertTrue(list.canUpload)
        assertFalse(list.canReview)
        assertEquals("approved", list.docOf("dni")?.status)
        assertNull(list.docOf("medical"))
        assertEquals(DocState.MISSING, Docs.stateOf(list.docOf("medical"), required = "medical" in list.required))
        assertEquals(DocState.OPTIONAL, Docs.stateOf(list.docOf("travel"), required = "travel" in list.required))
    }
}
