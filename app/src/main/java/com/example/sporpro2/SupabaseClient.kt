package com.example.sporpro2

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/**
 * Cliente central de Supabase para SportPro.
 * Incluye los módulos necesarios para la Semana 7:
 * - Auth (Autenticación)
 * - Postgrest (Base de datos)
 * - Realtime (Para eventos en tiempo real descritos en el diseño técnico)
 */
object SupabaseClient {

    // URL Raíz (sin rest/v1)
    private const val SUPABASE_URL = "https://yvekgctqioakmgswzdvq.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl2ZWtnY3RxaW9ha21nc3d6ZHZxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA5OTkxMDQsImV4cCI6MjEwNjU3NTEwNH0.ivUpm-dVkJz4RWa6mpx0wptAFRyh1lEq1to63NTAUyk"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
        install(Auth) {
            scheme = "sportpro"
            host = "login-callback"
        }
        install(Postgrest)
        install(Realtime)
    }
}
