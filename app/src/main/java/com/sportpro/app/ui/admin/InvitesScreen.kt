package com.sportpro.app.ui.admin
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R
import com.sportpro.app.core.AppText

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
import androidx.compose.ui.platform.LocalContext
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
import com.sportpro.app.ui.components.copyToClipboard
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.QrImage
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.family.shareText
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.ErrorRed
import com.sportpro.app.ui.theme.SuccessGreen
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
                _messages.tryEmit(AppText.get(R.string.invites_invitacion_anulada))
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
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    val title = if (kind == "adm") stringResource(R.string.invites_invitar_administrador) else stringResource(R.string.invites_invitar_entrenador)
    val who = if (kind == "adm") stringResource(R.string.invites_administrador_de_academias) else stringResource(R.string.invites_entrenador_dt_de_x, academyName ?: stringResource(R.string.invites_tu_academia))

    SpScaffold(title = title, onBack = onBack, snackbarHostState = snack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    stringResource(R.string.invites_genera_un_codigo_qr_de, who) +
                        if (kind == "adm") stringResource(R.string.invites_debera_registrar_su_academia_para) else "",
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
                ) { Text(if (ui.created == null) stringResource(R.string.invites_generar_codigo_qr) else stringResource(R.string.invites_generar_otro_codigo)) }
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
                                stringResource(R.string.invites_escanealo_con_la_camara_del, formatDayTime(inv.expiresAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { copyToClipboard(context, inv.code) }) { Text(stringResource(R.string.invites_copiar_codigo)) }
                                Button(onClick = {
                                    shareText(
                                        context, AppText.get(R.string.invites_invitacion_a_sportpro),
                                        AppText.get(R.string.invites_te_invito_a_sportpro_como, who, link) +
                                            AppText.get(R.string.invites_o_ingresa_el_codigo_x, inv.code),
                                    )
                                }) { Text(stringResource(R.string.invites_compartir)) }
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.invites_invitaciones_generadas)) }
            if (ui.history.isEmpty()) {
                item { Text(stringResource(R.string.invites_aun_no_generaste_invitaciones), modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }
            }
            items(ui.history, key = { it.id }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.code, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(row.academyName, row.usedByName?.let { stringResource(R.string.invites_usada_por_x, it) }, stringResource(R.string.invites_vence_x, formatDayTime(row.expiresAt)))
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (row.status) {
                        "active" -> {
                            Pill(stringResource(R.string.invites_activa), SuccessGreen)
                            TextButton(onClick = { vm.revoke(row) }) { Text(stringResource(R.string.invites_anular)) }
                        }
                        "used" -> Pill(stringResource(R.string.invites_usada), MaterialTheme.colorScheme.secondary)
                        "expired" -> Pill(stringResource(R.string.invites_vencida), WarnAmber)
                        else -> Pill(stringResource(R.string.invites_anulada), ErrorRed)
                    }
                }
            }
        }
    }
}
