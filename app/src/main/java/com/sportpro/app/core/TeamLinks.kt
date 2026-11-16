package com.sportpro.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Código de equipo que llega por QR o deep link: sportpro://team/EQ-XXXX-XXXX.
 *
 * El QR del equipo contiene ese ENLACE (no solo el código). Así, al escanearlo con la cámara
 * normal del teléfono, Android abre SportPro directamente en «Unirme a un equipo».
 * Antes el QR tenía solo el texto del código y la cámara no sabía qué hacer con él.
 *
 * Funciona igual que [InviteLinks]: MainActivity llama a offer(), el código queda «pendiente»
 * en memoria y la pantalla lo consume una sola vez. Si todavía no hay sesión, espera ahí
 * hasta que el usuario inicie sesión.
 */
object TeamLinks {
    const val SCHEME = "sportpro"
    const val HOST = "team"

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Texto que se dibuja dentro del QR del equipo. */
    fun linkFor(code: String): String = "$SCHEME://$HOST/$code"

    /**
     * Acepta el enlace completo, «EQ-ABCD-EFGH», «eq abcd efgh» o solo los 8 caracteres.
     * Devuelve el código normalizado «EQ-XXXX-XXXX», o null si no tiene esa forma.
     */
    fun parse(raw: String?): String? {
        val text = raw.orEmpty().trim()
        // Si es un enlace, el código es lo que va después de la última barra
        val tail = if (text.contains("://")) text.substringAfterLast('/') else text
        val clean = tail.uppercase().filter { it.isLetterOrDigit() }
        val body = if (clean.length == 10 && clean.startsWith("EQ")) clean.drop(2) else clean
        return if (body.length == 8) "EQ-${body.take(4)}-${body.drop(4)}" else null
    }

    /** Deja el código pendiente para que la interfaz abra «Unirme a un equipo» con él. */
    fun offer(raw: String?) {
        parse(raw)?.let { _pending.value = it }
    }

    /** Entrega el código una sola vez y lo limpia (para no buscar el mismo equipo dos veces). */
    fun consume(): String? = _pending.value.also { _pending.value = null }
}
