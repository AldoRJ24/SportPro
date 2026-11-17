package com.sportpro.app.core

/**
 * Enlace del QR del árbitro (migración 016): https://tu-pagina.vercel.app/#CODIGO
 *
 * El código va después de «#» a propósito: esa parte del enlace NUNCA se envía al servidor
 * de la página (no queda en sus registros) ni a otras páginas. Solo la lee la página web.
 * Lógica pura (sin Android) para poder probarla con JUnit.
 */
object RefereeLinks {

    /** El código válido: 64 caracteres hexadecimales en minúscula (lo genera el servidor). */
    private val TOKEN = Regex("^[0-9a-f]{64}$")

    /**
     * Arma el enlace. Devuelve null si falta configurar la dirección de la página
     * (REFEREE_WEB_URL en local.properties), si no es https o si el código no es válido.
     */
    fun linkFor(baseUrl: String?, token: String?): String? {
        val base = baseUrl.orEmpty().trim().trimEnd('/')
        if (!base.startsWith("https://") || base.length <= "https://".length) return null
        val t = token.orEmpty().trim().lowercase()
        if (!TOKEN.matches(t)) return null
        return "$base/#$t"
    }
}
