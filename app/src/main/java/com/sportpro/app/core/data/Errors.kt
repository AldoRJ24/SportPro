package com.sportpro.app.core.data

import com.sportpro.app.R
import com.sportpro.app.core.AppText
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException

/** true si el servidor respondió (validación o permiso). false significa un problema de red. */
fun Throwable.isServerAnswer(): Boolean = this is RestException

/** Violación de clave única (23505): sirve para tratar un evento reenviado como ya guardado (idempotencia). */
fun Throwable.isDuplicate(): Boolean = this is PostgrestRestException && this.code == "23505"

/** Mensaje para la persona, en español. Los errores de negocio ya vienen en español desde las funciones SQL; aquí solo se traducen los errores técnicos. */
fun Throwable.friendlyMessage(): String {
    val raw = when (this) {
        is RestException -> listOfNotNull(error, description).joinToString(" ")
        else -> message.orEmpty()
    }
    val lower = raw.lowercase()
    return when {
        // El servidor no alcanzó a responder a tiempo: no es lo mismo que no tener internet
        this is HttpRequestTimeoutException -> AppText.get(R.string.error_timeout)
        this !is RestException -> AppText.get(R.string.error_no_connection)
        "invalid login credentials" in lower || "invalid_credentials" in lower -> AppText.get(R.string.error_bad_credentials)
        "email not confirmed" in lower -> AppText.get(R.string.error_email_not_confirmed)
        "already registered" in lower || "user_already_exists" in lower -> AppText.get(R.string.error_email_taken)
        "password" in lower && "6" in lower -> AppText.get(R.string.error_password_short)
        "row-level security" in lower || "permission denied" in lower -> AppText.get(R.string.error_no_permission)
        "jwt" in lower -> AppText.get(R.string.error_session_expired)
        else -> this.error.ifBlank { AppText.get(R.string.error_unexpected) }
    }
}
