package com.sportpro.app.ui.family

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportpro.app.core.data.GuardianRequest
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.ui.components.Crest
import com.sportpro.app.ui.theme.Mint400

fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, subject))
}

/** «Mi código familiar»: única por apoderado; el hijo lo escribe al crear su cuenta. */
@Composable
fun FamilyCodeCard(code: String?, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mi código familiar", style = MaterialTheme.typography.titleMedium)
            Text(
                "Compártelo únicamente con tus hijos. Lo escriben al crear su cuenta y tú apruebas la solicitud.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CÓDIGO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(code ?: "…", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { code?.let { clipboard.setText(AnnotatedString(it)) } }, enabled = code != null) {
                            Text("Copiar código", color = Mint400)
                        }
                        TextButton(
                            onClick = {
                                code?.let {
                                    shareText(context, "Código familiar SportPro",
                                        "Mi código familiar de SportPro es $it. Escríbelo al crear tu cuenta de jugador para vincularte conmigo.")
                                }
                            },
                            enabled = code != null,
                        ) { Text("Compartir", color = Mint400) }
                    }
                }
            }
        }
    }
}

@Composable
fun GuardianRequestCard(request: GuardianRequest, onAccept: () -> Unit, onReject: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Crest(request.playerName, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${request.playerName} quiere vincularse contigo", style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(request.age?.let { "$it años" }, formatDayTime(request.requestedAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(onClick = onAccept) { Text("Aceptar") }
                    OutlinedButton(onClick = onReject) { Text("Rechazar") }
                }
            }
        }
    }
}
