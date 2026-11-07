package com.sportpro.app.core.data

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException

/** True when the server answered (validation/permission). False means a network or transport problem. */
fun Throwable.isServerAnswer(): Boolean = this is RestException

/** Unique violation (23505): used to treat a resent event as already saved (idempotency). */
fun Throwable.isDuplicate(): Boolean = this is PostgrestRestException && this.code == "23505"

/** Human message in Spanish. Server business errors are already written in Spanish in the SQL functions. */
fun Throwable.friendlyMessage(): String {
    val raw = when (this) {
        is RestException -> listOfNotNull(error, description).joinToString(" ")
        else -> message.orEmpty()
    }
    val lower = raw.lowercase()
    return when {
        this !is RestException -> "Sin conexión con el servidor. Revisa tu internet e inténtalo otra vez."
        "invalid login credentials" in lower || "invalid_credentials" in lower -> "Correo o contraseña incorrectos."
        "email not confirmed" in lower -> "Confirma tu correo antes de iniciar sesión (revisa tu bandeja)."
        "already registered" in lower || "user_already_exists" in lower -> "Ese correo ya tiene una cuenta."
        "password" in lower && "6" in lower -> "La contraseña debe tener al menos 6 caracteres."
        "row-level security" in lower || "permission denied" in lower -> "No tienes permiso para esta acción."
        "jwt" in lower -> "Tu sesión expiró. Vuelve a iniciar sesión."
        else -> (this as RestException).error.ifBlank { "Ocurrió un error inesperado." }
    }
}
