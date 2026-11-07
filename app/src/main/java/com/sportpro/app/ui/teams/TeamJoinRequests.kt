package com.sportpro.app.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.TeamJoinRequest
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.core.data.timeAgo
import com.sportpro.app.ui.components.QrImage
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.navigation.LocalAppContainer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class TeamRequestsUi(
    val requests: List<TeamJoinRequest> = emptyList(),
    val busyId: String? = null,
    val message: String? = null,
)

/**
 * Solicitudes para unirse a los equipos que gestiono (DT/admin). Se recarga sola cuando llega una
 * notificación (Realtime), así la solicitud aparece en el perfil del DT al instante.
 */
@OptIn(FlowPreview::class)
class TeamRequestsViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(TeamRequestsUi())
    val ui: StateFlow<TeamRequestsUi> = _ui.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            container.account.observeNotifications().debounce(300).catch { }.collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            runCatching { container.club.teamJoinRequests() }.onSuccess { list -> _ui.value = _ui.value.copy(requests = list) }
        }
    }

    fun respond(r: TeamJoinRequest, accept: Boolean, onChanged: () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busyId = r.id, message = null)
            try {
                container.club.respondTeamJoin(r.id, accept)
                _ui.value = _ui.value.copy(
                    requests = _ui.value.requests.filterNot { it.id == r.id },
                    message = if (accept) "${r.playerName} ya es parte de ${r.teamName}" else "Solicitud rechazada",
                )
                onChanged()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(message = e.friendlyMessage())
            } finally {
                _ui.value = _ui.value.copy(busyId = null)
            }
        }
    }
}

/** Bloque del perfil del DT: «Solicitudes para unirse» con Aceptar / Rechazar. No ocupa espacio si no hay. */
@Composable
fun TeamJoinRequestsBlock(onChanged: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm: TeamRequestsViewModel = viewModel { TeamRequestsViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (ui.requests.isEmpty() && ui.message == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Solicitudes para unirse (${ui.requests.size})")
        ui.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
        }
        ui.requests.forEach { r ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        r.playerName + (r.playerAge?.let { " · $it años" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Quiere unirse a ${r.teamName}${r.category?.let { " ($it)" } ?: ""} · ${r.academyName ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        (if (r.byGuardian) "Lo solicitó ${r.requestedByName} (apoderado/a)" else "Lo solicitó el propio jugador") +
                            " · " + timeAgo(r.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.respond(r, true, onChanged) }, enabled = ui.busyId == null) { Text("Aceptar") }
                        OutlinedButton(onClick = { vm.respond(r, false, onChanged) }, enabled = ui.busyId == null) { Text("Rechazar") }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta del DT en el plantel: código + QR del equipo para invitar jugadores.
 * Quien lo usa envía una SOLICITUD; nadie entra sin que el DT acepte. «Nuevo código» invalida el anterior.
 */
@Composable
fun TeamInviteCard(code: String?, onRegenerate: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Invitar jugadores", style = MaterialTheme.typography.titleMedium)
            Text(
                "Comparte este código o QR. El jugador (o su apoderado) lo usa en «Unirme a un equipo» y te llega la solicitud para aceptarla o rechazarla.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (code == null) {
                Text("No se pudo obtener el código del equipo.", style = MaterialTheme.typography.bodySmall)
            } else {
                QrImage(code, size = 180.dp)
                Text(code, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = onRegenerate) { Text("Generar nuevo código") }
        }
    }
}
