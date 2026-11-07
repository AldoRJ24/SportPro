package com.sportpro.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.InvitationCheck
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.navigation.LocalAppContainer
import kotlinx.coroutines.launch

/** QR de invitación abierto con la sesión iniciada: se acepta con la cuenta actual (debe ser mayor de edad). */
@Composable
fun RedeemInviteDialog(code: String, onDismiss: () -> Unit, onRedeemed: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var check by remember(code) { mutableStateOf<InvitationCheck?>(null) }
    var error by remember(code) { mutableStateOf<String?>(null) }
    var busy by remember(code) { mutableStateOf(false) }
    var academyName by remember(code) { mutableStateOf("") }
    var academyCity by remember(code) { mutableStateOf("") }
    LaunchedEffect(code) {
        try {
            check = container.account.checkInvitation(code)
        } catch (e: Exception) {
            error = e.friendlyMessage()
        }
    }
    val c = check
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invitación $code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    c == null && error == null -> LinearProgressIndicator()
                    c != null && !c.valid -> Text(c.reason ?: "Invitación no válida")
                    c != null && c.kind == "adm" -> {
                        Text("Te invitaron como administrador de academias. Para aceptarla con esta cuenta registra tu academia.")
                        OutlinedTextField(academyName, { academyName = it.take(80) }, label = { Text("Nombre de la academia") }, singleLine = true)
                        OutlinedTextField(academyCity, { academyCity = it.take(60) }, label = { Text("Ciudad") }, singleLine = true)
                    }
                    c != null -> Text("Te invitaron como entrenador (DT) de ${c.academyName ?: "una academia"}. ¿Aceptar con esta cuenta?")
                }
                if (busy) LinearProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Si la invitación es para otra persona, ciérrala y que esa persona cree su cuenta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = c?.valid == true && !busy && (c?.kind != "adm" || academyName.trim().length >= 2),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            container.account.redeemInvitation(code, academyName.trim().ifBlank { null }, academyCity.trim().ifBlank { null })
                            onRedeemed()
                        } catch (e: Exception) {
                            error = e.friendlyMessage()
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Aceptar invitación") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
