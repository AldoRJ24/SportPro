package com.sportpro.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.DigitMaskTransformation
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel
import com.sportpro.app.ui.theme.ErrorRed
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.WarnAmber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuardianLinkUi(val busy: Boolean = false, val error: String? = null)

/** Un jugador menor no usa la app hasta que un apoderado apruebe el vínculo (migración 003). */
class GuardianLinkViewModel(private val container: AppContainer, private val onChanged: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(GuardianLinkUi())
    val ui: StateFlow<GuardianLinkUi> = _ui.asStateFlow()

    init {
        // La respuesta del apoderado llega como notificación (Realtime) → se vuelve a verificar el acceso al instante.
        viewModelScope.launch {
            container.account.observeNotifications().catch { }.collect { onChanged() }
        }
    }

    fun request(code8: String) {
        if (code8.length != 8) {
            _ui.update { it.copy(error = "El código tiene 8 caracteres después de «SP-».") }
            return
        }
        // El usuario escribe 8 caracteres; se arma el formato del código del apoderado: SP-XXXX-XXXX.
        val code = "SP-${code8.take(4)}-${code8.drop(4)}"
        viewModelScope.launch {
            _ui.update { GuardianLinkUi(busy = true) }
            try {
                container.account.requestGuardianLink(code)
                _ui.update { GuardianLinkUi() }
                onChanged()
            } catch (e: Exception) {
                _ui.update { GuardianLinkUi(error = e.friendlyMessage()) }
            }
        }
    }
}

@Composable
fun GuardianLinkScreen(session: SessionViewModel) {
    val container = LocalAppContainer.current
    val ctx by session.context.collectAsStateWithLifecycle()
    val vm: GuardianLinkViewModel = viewModel { GuardianLinkViewModel(container) { session.refreshContext() } }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val account = ctx?.account
    var code by rememberSaveable { mutableStateOf("") }
    var retyping by rememberSaveable { mutableStateOf(false) }
    val pending = account?.guardianStatus == "pending" && !retyping

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SPORTPRO", color = Mint400, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Vincula a tu apoderado", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Hola, ${ctx?.displayName ?: ""}. Como eres menor de edad, para activar tu cuenta necesitamos la autorización de tu apoderado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (pending) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("SOLICITUD ENVIADA", WarnAmber)
                    Text(
                        "Esperando que ${account?.guardianName ?: "tu apoderado"} acepte la vinculación.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Cuando la acepte, entrarás automáticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { session.refreshContext() }) { Text("Actualizar") }
                        OutlinedButton(onClick = { retyping = true }) { Text("Usar otro código") }
                    }
                }
            }
        } else {
            if (account?.guardianStatus == "rejected") {
                Text(
                    "${account?.guardianName ?: "El apoderado"} rechazó la solicitud. Revisa el código con tu apoderado.",
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CÓDIGO DEL APODERADO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(8) },
                        prefix = { Text("SP-") },
                        placeholder = { Text("XXXX-XXXX") },
                        singleLine = true,
                        visualTransformation = DigitMaskTransformation("####-####"),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.error?.let { Text(it, color = ErrorRed) }
            Button(
                onClick = { retyping = false; vm.request(code) },
                enabled = !ui.busy && code.length == 8,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("Solicitar vinculación") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tu apoderado encuentra su código en Perfil → Mis hijos, y debe revisar y aprobar la solicitud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { session.signOut() }) { Text("Cerrar sesión") }
    }
}
