package com.sportpro.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.InviteLinks
import com.sportpro.app.core.data.CreatedInvitation
import com.sportpro.app.core.data.InvitationRow
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.QrImage
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.family.shareText
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.ErrorRed
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.WarnAmber
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvitesUi(
    val busy: Boolean = false,
    val created: CreatedInvitation? = null,
    val history: List<InvitationRow> = emptyList(),
)

/**
 * Invitaciones. kind = "adm" (el superadmin invita administradores de academia) o "dt" (el admin
 * invita entrenadores a SU academia). Crea el código, muestra el QR y el historial con su estado.
 */
class InvitesViewModel(
    private val container: AppContainer,
    private val kind: String,
    private val academyId: String?,
) : ViewModel() {
    private val _ui = MutableStateFlow(InvitesUi())
    val ui: StateFlow<InvitesUi> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init { loadHistory() }

    fun loadHistory() {
        viewModelScope.launch {
            runCatching { container.account.myInvitations() }.onSuccess { list ->
                _ui.update { s -> s.copy(history = list.filter { it.kind == kind }) }
            }
        }
    }

    fun create() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val inv = if (kind == "adm") container.account.createAdminInvitation()
                else container.account.createCoachInvitation(academyId ?: error("Falta la academia"))
                _ui.update { it.copy(busy = false, created = inv) }
                loadHistory()
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false) }
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    // Anular una invitación aún no usada (por ejemplo, si el QR se compartió por error).
    fun revoke(row: InvitationRow) {
        viewModelScope.launch {
            try {
                container.account.revokeInvitation(row.id)
                if (_ui.value.created?.code == row.code) _ui.update { it.copy(created = null) }
                _messages.tryEmit("Invitación anulada")
                loadHistory()
            } catch (e: Exception) {
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }
}

@Composable
fun InvitesScreen(kind: String, academyId: String?, academyName: String?, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: InvitesViewModel = viewModel(key = "inv-$kind-$academyId") { InvitesViewModel(container, kind, academyId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    val title = if (kind == "adm") "Invitar administrador" else "Invitar entrenador"
    val who = if (kind == "adm") "administrador de academias" else "entrenador (DT) de ${academyName ?: "tu academia"}"

    SpScaffold(title = title, onBack = onBack, snackbarHostState = snack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Genera un código QR de un solo uso (vence en 7 días). Quien lo escanee abre SportPro y se registra como $who." +
                        if (kind == "adm") " Deberá registrar su academia para completar el registro." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                Button(
                    onClick = vm::create,
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                ) { Text(if (ui.created == null) "Generar código QR" else "Generar otro código") }
            }
            if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
            ui.created?.let { inv ->
                item {
                    val link = InviteLinks.linkFor(inv.code)
                    Card(
                        Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QrImage(link)
                            Text(inv.code, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text(
                                "Escanéalo con la cámara del teléfono o con «Me invitaron: escanear QR» en el inicio de sesión. Vence: ${formatDayTime(inv.expiresAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(inv.code)) }) { Text("Copiar código") }
                                Button(onClick = {
                                    shareText(
                                        context, "Invitación a SportPro",
                                        "Te invito a SportPro como $who. Abre este enlace en tu celular con la app instalada: $link " +
                                            "(o ingresa el código ${inv.code} en «Regístrate → ¿Te invitaron?»).",
                                    )
                                }) { Text("Compartir") }
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Invitaciones generadas") }
            if (ui.history.isEmpty()) {
                item { Text("Aún no generaste invitaciones.", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }
            }
            items(ui.history, key = { it.id }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.code, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(row.academyName, row.usedByName?.let { "usada por $it" }, "vence ${formatDayTime(row.expiresAt)}")
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (row.status) {
                        "active" -> {
                            Pill("Activa", Mint400)
                            TextButton(onClick = { vm.revoke(row) }) { Text("Anular") }
                        }
                        "used" -> Pill("Usada", MaterialTheme.colorScheme.secondary)
                        "expired" -> Pill("Vencida", WarnAmber)
                        else -> Pill("Anulada", ErrorRed)
                    }
                }
            }
        }
    }
}
