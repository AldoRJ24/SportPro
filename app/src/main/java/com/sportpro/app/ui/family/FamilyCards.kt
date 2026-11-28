package com.sportpro.app.ui.family
import com.sportpro.app.core.AppText
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportpro.app.core.data.GuardianRequest
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.ui.components.copyToClipboard
import com.sportpro.app.ui.components.Crest

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
    val context = LocalContext.current
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.family_cards_mi_codigo_familiar), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.family_cards_compartelo_unicamente_con_tus_hijos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.family_cards_codigo), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(code ?: "…", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { code?.let { copyToClipboard(context, it) } }, enabled = code != null) {
                            Text(stringResource(R.string.family_cards_copiar_codigo), color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(
                            onClick = {
                                code?.let {
                                    shareText(context, AppText.get(R.string.family_cards_codigo_familiar_sportpro),
                                        AppText.get(R.string.family_cards_mi_codigo_familiar_de_sportpro, it))
                                }
                            },
                            enabled = code != null,
                        ) { Text(stringResource(R.string.family_cards_compartir), color = MaterialTheme.colorScheme.primary) }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Crest(request.playerName, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.family_cards_x_quiere_vincularse_contigo, request.playerName), style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(request.age?.let { pluralStringResource(R.plurals.common_years, it, it) }, formatDayTime(request.requestedAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(onClick = onAccept) { Text(stringResource(R.string.family_cards_aceptar)) }
                    OutlinedButton(onClick = onReject) { Text(stringResource(R.string.family_cards_rechazar)) }
                }
            }
        }
    }
}
