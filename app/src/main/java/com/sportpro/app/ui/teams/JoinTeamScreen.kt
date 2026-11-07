package com.sportpro.app.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.MyTeamJoinRequest
import com.sportpro.app.core.data.PlayerRef
import com.sportpro.app.core.data.TeamCodeCheck
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.core.data.timeAgo
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.components.scanQr
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel
import com.sportpro.app.ui.theme.LiveRed
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.WarnAmber
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Acepta «EQ-ABCD-EFGH», «eq abcd efgh» o solo los 8 caracteres; devuelve el código normalizado o null. */
fun normalizeTeamCode(raw: String?): String? {
    val clean = raw.orEmpty().uppercase().filter { it.isLetterOrDigit() }
    val body = if (clean.length == 10 && clean.startsWith("EQ")) clean.drop(2) else clean
    return if (body.length == 8) "EQ-${body.take(4)}-${body.drop(4)}" else null
}

data class JoinTeamUi(
    val checking: Boolean = false,
    val code: String? = null,
    val check: TeamCodeCheck? = null,
    val sending: Boolean = false,
    val mine: List<MyTeamJoinRequest> = emptyList(),
)

/**
 * Unirse a un equipo: 1) revisar el código (QR o escrito) → muestra equipo, categoría y academia;
 * 2) elegir quién se une (mi perfil de jugador o mi hijo); 3) enviar la solicitud al DT.
 */
class JoinTeamViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(JoinTeamUi())
    val ui: StateFlow<JoinTeamUi> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init { loadMine() }

    fun loadMine() {
        viewModelScope.launch {
            runCatching { container.club.myTeamJoinRequests() }.onSuccess { list -> _ui.update { it.copy(mine = list) } }
        }
    }

    fun check(raw: String?) {
        val code = normalizeTeamCode(raw)
        if (code == null) {
            _messages.tryEmit("El código tiene el formato EQ-XXXX-XXXX (8 letras/números).")
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(checking = true, code = code, check = null) }
            try {
                val c = container.club.checkTeamCode(code)
                _ui.update { it.copy(checking = false, check = c) }
                if (!c.valid) _messages.tryEmit(c.reason ?: "Código no válido")
            } catch (e: Exception) {
                _ui.update { it.copy(checking = false) }
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    fun send(player: PlayerRef, onSent: () -> Unit) {
        val code = _ui.value.code ?: return
        viewModelScope.launch {
            _ui.update { it.copy(sending = true) }
            try {
                container.club.requestTeamJoin(code, player.id)
                _messages.tryEmit("Solicitud enviada. El DT recibirá una notificación para aceptarla.")
                _ui.update { it.copy(check = null, code = null) }
                onSent()
                loadMine()
            } catch (e: Exception) {
                _messages.tryEmit(e.friendlyMessage())
            } finally {
                _ui.update { it.copy(sending = false) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JoinTeamScreen(session: SessionViewModel, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: JoinTeamViewModel = viewModel { JoinTeamViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx by session.context.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    var typed by rememberSaveable { mutableStateOf("") }
    // Quiénes pueden unirse: mi perfil de jugador (si tengo) y mis hijos vinculados
    val candidates: List<PlayerRef> = listOfNotNull(ctx?.ownPlayer) + ctx?.children.orEmpty()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    SpScaffold(title = "Unirme a un equipo", onBack = onBack, snackbarHostState = snack) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Pide al DT el código o el QR de su equipo. Tu solicitud le llega como notificación y él decide si acepta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.uppercase().take(14) },
                    label = { Text("Código del equipo (EQ-XXXX-XXXX)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.check(typed) }, enabled = !ui.checking) { Text("Buscar") }
            }
            OutlinedButton(
                onClick = {
                    scanQr(
                        context,
                        onResult = { raw ->
                            if (raw != null) {
                                typed = normalizeTeamCode(raw) ?: raw
                                vm.check(raw)
                            }
                        },
                        onError = { msg -> scope.launch { snack.showSnackbar(msg) } },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Escanear QR del equipo") }
            if (ui.checking) LinearProgressIndicator(Modifier.fillMaxWidth())

            val check = ui.check
            if (check != null && check.valid) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamCrest(check.teamName ?: "Equipo", check.crestPath, size = 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(check.teamName ?: "Equipo", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(check.category, check.academyName).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                check.maxAge?.let {
                                    Text("Hasta $it años", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Text("¿Quién se une?", style = MaterialTheme.typography.titleSmall)
                        if (candidates.isEmpty()) {
                            Text(
                                "Tu cuenta no tiene perfil de jugador. Si eres apoderado, crea primero el perfil de tu hijo en «Mis hijos».",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                candidates.forEach { p ->
                                    val label = if (p.id == ctx?.ownPlayer?.id) "Yo · ${p.fullName}" else p.fullName
                                    FilterChip(selected = selectedId == p.id, onClick = { selectedId = p.id }, label = { Text(label) })
                                }
                            }
                            val chosen = candidates.firstOrNull { it.id == selectedId }
                            Button(
                                onClick = { chosen?.let { vm.send(it) { typed = "" } } },
                                enabled = chosen != null && !ui.sending,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Enviar solicitud al DT") }
                        }
                    }
                }
            }

            if (ui.mine.isNotEmpty()) {
                SectionHeader("Mis solicitudes", Modifier.padding(top = 8.dp))
                ui.mine.forEach { r -> MyRequestRow(r) }
            }
        }
    }
}

@Composable
private fun MyRequestRow(r: MyTeamJoinRequest) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${r.playerName} → ${r.teamName}", style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(r.category, r.academyName, timeAgo(r.createdAt).ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (r.status) {
                "approved" -> Pill("Aceptada", Mint400)
                "rejected" -> Pill("Rechazada", LiveRed)
                else -> Pill("Pendiente", WarnAmber)
            }
        }
    }
}
