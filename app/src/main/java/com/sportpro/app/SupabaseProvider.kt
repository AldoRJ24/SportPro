package com.sportpro.app

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Single Supabase client for the whole app.
 * Only the project URL and the publishable key ship in the APK (both from local.properties
 * via BuildConfig). Secret keys never live in the client.
 */
object SupabaseProvider {

    const val DEEPLINK_SCHEME = "sportpro"
    const val DEEPLINK_AUTH_HOST = "auth-callback"

    /** Public URL of an avatar stored in the «avatars» bucket. */
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
