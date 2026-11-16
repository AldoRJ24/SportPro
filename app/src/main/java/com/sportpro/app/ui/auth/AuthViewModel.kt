package com.sportpro.app.ui.auth
import com.sportpro.app.R
import com.sportpro.app.core.AppText

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
            _ui.update { it.copy(error = AppText.get(R.string.auth_ingresa_tu_correo_y_contrasena)) }
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
            _invite.value = InviteState(error = AppText.get(R.string.auth_codigo_de_invitacion_invalido))
            return
        }
        viewModelScope.launch {
            _invite.value = InviteState(code = code, checking = true)
            try {
                val check = container.account.checkInvitation(code)
                _invite.value = InviteState(
                    code = code,
                    check = check,
                    error = if (check.valid) null else (check.reason ?: AppText.get(R.string.auth_invitacion_no_valida)),
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
            "adm" -> AppText.get(R.string.auth_administrador_de_academia)
            "dt" -> "entrenador"
            else -> "jugador"
        }
        // Validaciones del formulario en orden; se muestra el primer error encontrado.
        val error = when {
            inv.code != null && !inv.valid -> AppText.get(R.string.auth_la_invitacion_no_es_valida, inv.error ?: AppText.get(R.string.auth_revisala))
            f.firstName.trim().length < 2 -> AppText.get(R.string.auth_escribe_tu_nombre)
            f.lastName.trim().length < 2 -> AppText.get(R.string.auth_escribe_tu_apellido)
            f.birthDigits.length < 8 -> AppText.get(R.string.auth_completa_la_fecha_de_nacimiento)
            date == null -> AppText.get(R.string.auth_esa_fecha_de_nacimiento_no)
            date.isAfter(LocalDate.now()) -> AppText.get(R.string.auth_la_fecha_de_nacimiento_no)
            age != null && age < 13 ->
                AppText.get(R.string.auth_los_menores_de_13_anos)
            age != null && age < minAge -> AppText.get(R.string.auth_para_registrarte_como_x_debes, who)
            type == "adm" && f.academyName.trim().length < 2 -> AppText.get(R.string.auth_registra_el_nombre_de_tu)
            !f.email.contains("@") -> AppText.get(R.string.auth_correo_invalido)
            f.password.length < 6 -> AppText.get(R.string.auth_la_contrasena_debe_tener_al)
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
                        AuthUiState(info = AppText.get(R.string.auth_cuenta_creada_te_enviamos_un))
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
                            AppText.get(R.string.auth_el_servidor_rechazo_el_registro)
                        } else msg,
                    )
                }
            }
        }
    }
}
