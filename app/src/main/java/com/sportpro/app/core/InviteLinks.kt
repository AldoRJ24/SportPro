package com.sportpro.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

 /**
  * Invitaciones que llegan por QR o deep link: sportpro://invite/CÓDIGO.
  * El QR contiene ese enlace; la cámara del teléfono o el escáner de la app abren SportPro con él.
  * MainActivity llama a offer(); el registro (o el diálogo, si ya hay sesión) lo consume una sola vez.
  */
object InviteLinks {
    const val SCHEME = "sportpro"
    const val HOST = "invite"

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun linkFor(code: String): String = "$SCHEME://$HOST/$code"

    /** Acepta el enlace completo o solo el código; lo normaliza (mayúsculas, sin espacios) o devuelve null. */
    fun parse(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val code = if (text.startsWith("$SCHEME://", ignoreCase = true)) text.substringAfterLast('/') else text
        val clean = code.uppercase().filter { it.isLetterOrDigit() || it == '-' }
        return clean.takeIf { it.length >= 6 }
    }

    // Deja el código «pendiente» para que la interfaz lo use (StateFlow observado por AuthNav y MainShell).
    fun offer(raw: String?) {
        parse(raw)?.let { _pending.value = it }
    }

    // Entrega el código una sola vez y lo limpia (evita aplicar la misma invitación dos veces).
    fun consume(): String? = _pending.value.also { _pending.value = null }

    fun clear() {
        _pending.value = null
    }
}
