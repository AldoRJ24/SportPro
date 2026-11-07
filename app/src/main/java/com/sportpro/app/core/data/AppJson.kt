package com.sportpro.app.core.data

import io.github.jan.supabase.postgrest.result.PostgrestResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Single JSON configuration for the app: tolerant to new server columns. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Decodes a PostgREST response (JSON array) with [AppJson]. */
inline fun <reified T> PostgrestResult.decodeRows(): List<T> =
    if (data.isBlank()) emptyList() else AppJson.decodeFromString<List<T>>(data)

/** Decodes a scalar RPC response such as `true` or `"uuid"`. */
inline fun <reified T> PostgrestResult.decodeScalar(): T = AppJson.decodeFromString<T>(data)
