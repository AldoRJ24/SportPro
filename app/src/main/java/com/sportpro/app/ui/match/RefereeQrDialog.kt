package com.sportpro.app.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sportpro.app.BuildConfig
import com.sportpro.app.R
import com.sportpro.app.core.RefereeLinks
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.QrImage
import com.sportpro.app.ui.components.copyToClipboard
import com.sportpro.app.ui.navigation.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * QR del árbitro: el DT lo muestra y el árbitro lo escanea con la cámara de su celular. Se abre
 * la página web donde registra los eventos sin crear cuenta (migración 016).
 *
 * El código lo crea y lo valida el servidor; aquí solo se pide, se dibuja y se puede:
 *  - copiar el enlace (para mandarlo por mensaje);
 *  - generar uno nuevo (el anterior deja de servir al instante);
 *  - desactivarlo.
 */
@Composable
fun RefereeQrDialog(matchId: String, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val link = RefereeLinks.linkFor(BuildConfig.REFEREE_WEB_URL, token)
    val copied = stringResource(R.string.referee_copied)
    val renewed = stringResource(R.string.referee_renewed)

    // Ejecuta una acción contra el servidor mostrando la barra de progreso y el error, si hay.
    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null; info = null
            try { action() } catch (e: Exception) { error = e.friendlyMessage() }
            busy = false
        }
    }

    LaunchedEffect(matchId) { run { token = container.matches.refereeToken(matchId) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.referee_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.referee_explain), style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                when {
                    // Falta la dirección de la página en local.properties: se explica cómo arreglarlo
                    BuildConfig.REFEREE_WEB_URL.isBlank() || (token != null && link == null) ->
                        Text(stringResource(R.string.referee_missing_url), color = MaterialTheme.colorScheme.error)
                    link != null -> {
                        QrImage(link, size = 220.dp)
                        Text(stringResource(R.string.referee_warning), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { copyToClipboard(context, link); info = copied }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.referee_copy))
                        }
                        OutlinedButton(
                            onClick = { run { token = container.matches.refereeToken(matchId, regenerate = true); info = renewed } },
                            enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.referee_new_code)) }
                        TextButton(
                            onClick = { run { container.matches.revokeRefereeLink(matchId); onDismiss() } },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.referee_disable), color = MaterialTheme.colorScheme.error) }
                    }
                }
                info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.referee_close)) } },
    )
}
