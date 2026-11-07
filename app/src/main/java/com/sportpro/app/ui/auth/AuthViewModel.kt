package com.sportpro.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.InviteLinks
import com.sportpro.app.core.data.InvitationCheck
import com.sportpro.app.core.data.SignUpData
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.parseDateDigits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

data class AuthUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

/** Invitación adjunta al registro (QR, enlace o código escrito). Si es válida, fija el tipo de cuenta (adm/dt). */
data class InviteState(
    val code: String? = null,
    val checking: Boolean = false,
    val check: InvitationCheck? = null,
    val error: String? = null,
) {
    val valid: Boolean get() = check?.valid == true
    val kind: String? get() = if (valid) check?.kind else null
}

data class RegisterForm(
    val accountType: String = "pad",   // pad | jug (adm/dt only through an invitation)
    val firstName: String = "",
    val lastName: String = "",
    val birthDigits: String = "",
    val email: String = "",
    val password: String = "",
    val academyName: String = "",
    val academyCity: String = "",
)

// Inicio de sesión y registro. Valida en el celular para dar mensajes claros; el servidor vuelve a validar todo.
class AuthViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    private val _invite = MutableStateFlow(InviteState())
    val invite: StateFlow<InviteState> = _invite.asStateFlow()

    private val _form = MutableStateFlow(RegisterForm())
    val form: StateFlow<RegisterForm> = _form.asStateFlow()

    fun clearMessages() = _ui.update { it.copy(error = null, info = null) }

    fun updateForm(f: (RegisterForm) -> RegisterForm) {
        _form.update(f)
        _ui.update { it.copy(error = null) }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _ui.update { it.copy(error = "Ingresa tu correo y contraseña.") }
            return
        }
        viewModelScope.launch {
            _ui.update { AuthUiState(busy = true) }
            try {
                container.auth.signIn(email.trim(), password)
                _ui.update { AuthUiState() } // session flow switches the app to the main shell
            } catch (e: Exception) {
                _ui.update { AuthUiState(error = e.friendlyMessage()) }
            }
        }
    }

    /** Valida la invitación ANTES de crear la cuenta (check_invitation): muestra para qué es y de qué academia. */
    fun applyInvite(raw: String?) {
        val code = InviteLinks.parse(raw)
        if (code == null) {
            _invite.value = InviteState(error = "Código de invitación inválido")
            return
        }
        viewModelScope.launch {
            _invite.value = InviteState(code = code, checking = true)
            try {
                val check = container.account.checkInvitation(code)
                _invite.value = InviteState(
                    code = code,
                    check = check,
                    error = if (check.valid) null else (check.reason ?: "Invitación no válida"),
                )
            } catch (e: Exception) {
                _invite.value = InviteState(code = code, error = e.friendlyMessage())
            }
        }
    }

    fun clearInvite() {
        _invite.value = InviteState()
    }

    fun signUp(onDone: () -> Unit) {
        val f = _form.value
        val inv = _invite.value
        // El tipo lo fija la invitación (adm/dt) o lo elige el usuario (apoderado o jugador).
        val type = inv.kind ?: f.accountType
        val date = parseDateDigits(f.birthDigits)
        val age = date?.let { Period.between(it, LocalDate.now()).years }
        val minAge = if (type == "jug") 13 else 18
        val who = when (type) {
            "pad" -> "apoderado"
            "adm" -> "administrador de academia"
            "dt" -> "entrenador"
            else -> "jugador"
        }
        // Validaciones del formulario en orden; se muestra el primer error encontrado.
        val error = when {
            inv.code != null && !inv.valid -> "La invitación no es válida: ${inv.error ?: "revísala"}"
            f.firstName.trim().length < 2 -> "Escribe tu nombre."
            f.lastName.trim().length < 2 -> "Escribe tu apellido."
            f.birthDigits.length < 8 -> "Completa la fecha de nacimiento: 8 dígitos (DD/MM/AAAA)."
            date == null -> "Esa fecha de nacimiento no existe. Revisa día y mes."
            date.isAfter(LocalDate.now()) -> "La fecha de nacimiento no puede ser futura."
            age != null && age < 13 ->
                "Los menores de 13 años no crean cuenta: su apoderado crea su perfil en «Mis hijos»."
            age != null && age < minAge -> "Para registrarte como $who debes ser mayor de edad (18+)."
            type == "adm" && f.academyName.trim().length < 2 -> "Registra el nombre de tu academia para completar el registro."
            !f.email.contains("@") -> "Correo inválido."
            f.password.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
            else -> null
        }
        if (error != null) {
            _ui.update { it.copy(error = error, info = null) }
            return
        }
        viewModelScope.launch {
            _ui.update { AuthUiState(busy = true) }
            try {
                val needsConfirm = container.auth.signUp(
                    SignUpData(
                        accountType = type,
                        firstName = f.firstName.trim(),
                        lastName = f.lastName.trim(),
                        birthDateIso = date.toString(),
                        email = f.email.trim(),
                        password = f.password,
                        inviteCode = if (inv.valid) inv.code else null,
                        academyName = if (type == "adm") f.academyName.trim() else null,
                        academyCity = if (type == "adm") f.academyCity.trim() else null,
                    ),
                )
                clearInvite()
                _form.value = RegisterForm()
                if (needsConfirm) {
                    _ui.update {
                        AuthUiState(info = "Cuenta creada. Te enviamos un correo para confirmarla; luego inicia sesión.")
                    }
                    onDone()
                } else {
                    _ui.update { AuthUiState() }
                }
            } catch (e: Exception) {
                val msg = e.friendlyMessage()
                _ui.update {
                    AuthUiState(
                        // Si el trigger del servidor rechaza el alta, Supabase devuelve un error genérico: se traduce.
                        error = if (msg.contains("Database error", ignoreCase = true)) {
                            "El servidor rechazó el registro (revisa edad, invitación y datos de la academia)."
                        } else msg,
                    )
                }
            }
        }
    }
}
