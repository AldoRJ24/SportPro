package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.SignUpData
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository(private val client: SupabaseClient) {

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    // Inicio de sesión con correo y contraseña (Supabase Auth). La sesión se guarda y se renueva sola.
    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

     /**
      * REGISTRO. Los datos del formulario viajan como metadata del usuario y en el servidor el trigger
      * handle_new_user (migración 003) decide si se acepta:
      * - edad mínima: jugador 13+, apoderado / admin / DT 18+;
      * - admin y DT SOLO con una invitación válida (nadie puede asignarse ese rol);
      * - el admin debe registrar el nombre de su academia (se crea ya verificada);
      * - al jugador se le crea su perfil deportivo (players).
      * Si alguna regla falla, Supabase rechaza el alta completa (no queda nada a medias).
      * @return true si la cuenta aún debe confirmarse por correo (botón «Confirmar mi cuenta»).
      */
    suspend fun signUp(form: SignUpData): Boolean {
        client.auth.signUpWith(Email) {
            this.email = form.email
            this.password = form.password
            data = buildJsonObject {
                put("account_type", form.accountType)
                put("first_name", form.firstName)
                put("last_name", form.lastName)
                put("full_name", "${form.firstName} ${form.lastName}".trim())
                put("birth_date", form.birthDateIso)
                if (!form.inviteCode.isNullOrBlank()) put("invite_code", form.inviteCode)
                if (!form.academyName.isNullOrBlank()) put("academy_name", form.academyName)
                if (!form.academyCity.isNullOrBlank()) put("academy_city", form.academyCity)
            }
        }
        return client.auth.currentSessionOrNull() == null
    }

    suspend fun signOut() {
        client.auth.signOut()
    }
}
