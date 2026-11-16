package com.sportpro.app.ui.components

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sportpro.app.R

/**
 * Mensaje breve para el usuario (snackbar).
 * - [text]: mensaje ya escrito, normalmente el error en español que devolvió el servidor.
 * - [res]: texto fijo de strings.xml.
 * - [plural] + [quantity]: texto con plural («Se generaron 3 cobros»).
 * El ViewModel no conoce el Context, por eso guarda el id del recurso y la pantalla lo traduce.
 */
data class UiMessage(
    val text: String? = null,
    @StringRes val res: Int? = null,
    @PluralsRes val plural: Int? = null,
    val quantity: Int = 0,
)

/**
 * Muestra un [UiMessage] en el snackbar y avisa al ViewModel de que ya se mostró.
 * Aquí se convierte el id de strings.xml en texto, porque el ViewModel no tiene Context.
 */
@Composable
fun UiMessageEffect(message: UiMessage?, snack: SnackbarHostState, onConsumed: () -> Unit) {
    val resources = LocalResources.current
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        val text = when {
            m.text != null -> m.text
            m.plural != null -> resources.getQuantityString(m.plural, m.quantity, m.quantity)
            m.res != null -> resources.getString(m.res)
            else -> null
        }
        if (text != null) snack.showSnackbar(text)
        onConsumed()
    }
}

/**
 * Diálogo que exige un motivo (rechazar, anular, exonerar): nada se cambia sin dejar por qué.
 * El botón de guardar solo se activa con 3 caracteres o más, igual que valida el servidor.
 */
@Composable
fun ReasonDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    val valid = reason.trim().length >= 3
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.common_reason_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(200) },
                    label = { Text(stringResource(R.string.common_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(reason.trim()) }, enabled = valid) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
