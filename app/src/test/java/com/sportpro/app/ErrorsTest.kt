package com.sportpro.app

import com.sportpro.app.core.data.friendlyMessage
import io.ktor.client.plugins.HttpRequestTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Mensajes de error para la persona: un tiempo agotado no debe decir «sin conexión». */
class ErrorsTest {

    @Before
    fun setUp() = TestText.install()

    @Test
    fun `un tiempo de espera agotado tiene su propio mensaje`() {
        val e = HttpRequestTimeoutException("https://ejemplo/functions/v1/generate-match-summary", 90_000)
        assertEquals(TestText.key(R.string.error_timeout), e.friendlyMessage())
    }

    @Test
    fun `un error de red sigue diciendo sin conexion`() {
        assertEquals(TestText.key(R.string.error_no_connection), IOException("sin red").friendlyMessage())
    }
}
