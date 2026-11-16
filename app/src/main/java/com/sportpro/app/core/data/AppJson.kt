package com.sportpro.app.core.data

import io.github.jan.supabase.postgrest.result.PostgrestResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Configuración única de JSON para toda la app: tolera columnas nuevas del servidor sin fallar. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Convierte una respuesta de PostgREST (arreglo JSON) en una lista, usando [AppJson]. */
inline fun <reified T> PostgrestResult.decodeRows(): List<T> =
    if (data.isBlank()) emptyList() else AppJson.decodeFromString<List<T>>(data)

/** Convierte la respuesta de una función que devuelve un solo valor, como `true` o `"uuid"`. */
inline fun <reified T> PostgrestResult.decodeScalar(): T = AppJson.decodeFromString<T>(data)
