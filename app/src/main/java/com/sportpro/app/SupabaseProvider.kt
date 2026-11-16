package com.sportpro.app

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Cliente único de Supabase para toda la app.
 * En el APK solo van la URL del proyecto y la clave publicable (ambas desde local.properties
 * vía BuildConfig). Las claves secretas nunca viven en la app.
 */
object SupabaseProvider {

    const val DEEPLINK_SCHEME = "sportpro"
    const val DEEPLINK_AUTH_HOST = "auth-callback"

    /** URL pública de una foto de perfil guardada en el bucket «avatars». */
    fun avatarUrl(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/avatars/$it" }

    /** URL pública de la foto de una academia (bucket «academy-logos»); sus equipos usan la misma. */
    fun academyLogoUrl(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/academy-logos/$it" }

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        check(isConfigured) { "Missing SUPABASE_URL or SUPABASE_PUBLISHABLE_KEY in local.properties" }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth) {
                // Deep link: el botón del correo de verificación abre la app ya con sesión iniciada
                scheme = DEEPLINK_SCHEME
                host = DEEPLINK_AUTH_HOST
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }
}
