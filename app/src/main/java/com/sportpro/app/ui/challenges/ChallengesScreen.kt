package com.sportpro.app.ui.challenges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.ChallengeRow
import com.sportpro.app.core.data.Challenges
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.ReasonDialog
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.UiMessage
import com.sportpro.app.ui.components.UiMessageEffect
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.ErrorRed
import com.sportpro.app.ui.theme.SuccessGreen
import com.sportpro.app.ui.theme.WarnAmber
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChallengesUi(
    val loading: Boolean = true,
    val error: String? = null,
    val rows: List<ChallengeRow> = emptyList(),
    val busy: Boolean = false,
    val message: UiMessage? = null,
) {
    /** Los que esperan MI respuesta. */
    val toAnswer: List<ChallengeRow> get() = rows.filter { it.canRespond }
    /** Los que envié y siguen sin respuesta. */
    val waiting: List<ChallengeRow> get() = rows.filter { it.isPending && !it.canRespond }
    /** Aceptados, rechazados, cancelados y vencidos: se conservan como historial. */
    val history: List<ChallengeRow> get() = rows.filter { !it.isPending }
}

/**
 * Desafíos recibidos y enviados. Aceptar, rechazar y cancelar los valida el servidor.
 * Se recarga sola cuando llega una notificación (por ejemplo, un desafío nuevo).
 */
@OptIn(FlowPreview::class)
class ChallengesViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ChallengesUi())
    val ui: StateFlow<ChallengesUi> = _ui.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            container.account.observeNotifications().debounce(300).catch { }.collect { load(silent = true) }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _ui.update { it.copy(loading = true, error = null) }
            try {
                val rows = Challenges.sorted(container.challenges.mine())
                _ui.update { it.copy(loading = false, error = null, rows = rows) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Acepta: el servidor crea el partido y [onAccepted] recibe su id para abrirlo. */
    fun accept(row: ChallengeRow, onAccepted: (String) -> Unit) = run(R.string.challenges_msg_accepted) {
        container.challenges.respond(row.id, accept = true)?.let(onAccepted)
    }

    fun reject(row: ChallengeRow, reason: String) = run(R.string.challenges_msg_rejected) {
        container.challenges.respond(row.id, accept = false, reason = reason)
    }

    fun cancel(row: ChallengeRow) = run(R.string.challenges_msg_cancelled) {
        container.challenges.cancel(row.id)
    }

    fun messageShown() = _ui.update { it.copy(message = null) }

    // Ejecuta una acción, avisa del resultado y recarga la lista (también si falló, por si cambió el estado).
    private fun run(okMessage: Int, action: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val msg = try {
                action()
                UiMessage(res = okMessage)
            } catch (e: Exception) {
                UiMessage(text = e.friendlyMessage())
            }
            _ui.update { it.copy(busy = false, message = msg) }
            load(silent = true)
        }
    }
}

/** Pantalla «Desafíos»: por responder, enviados en espera e historial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesScreen(
    onBack: () -> Unit,
    onOpenMatch: (String) -> Unit,
    /** Se aceptó un desafío y ya existe el partido: MainShell recarga «Partidos» y lo abre. */
    onAccepted: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: ChallengesViewModel = viewModel { ChallengesViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var rejecting by remember { mutableStateOf<ChallengeRow?>(null) }
    UiMessageEffect(ui.message, snack, vm::messageShown)
    LaunchedEffect(Unit) { vm.load(silent = true) }

    SpScaffold(
        title = stringResource(R.string.challenges_title),
        onBack = onBack,
        snackbarHostState = snack,
        actions = { TextButton(onClick = onCreate) { Text(stringResource(R.string.challenges_new)) } },
    ) { pad ->
        when {
            ui.loading && ui.rows.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && ui.rows.isEmpty() -> ErrorBox(ui.error!!, onRetry = { vm.load() }, modifier = Modifier.padding(pad))
            else -> PullToRefreshBox(isRefreshing = ui.loading, onRefresh = { vm.load() }, modifier = Modifier.padding(pad)) {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (ui.rows.isEmpty()) {
                        item {
                            EmptyState(
                                stringResource(R.string.challenges_empty_title),
                                stringResource(R.string.challenges_empty_body),
                                actionLabel = stringResource(R.string.challenges_new),
                                onAction = onCreate,
                            )
                        }
                    }
                    section(R.string.challenges_to_answer, ui.toAnswer) { row ->
                        ChallengeCard(row, enabled = !ui.busy, onOpenMatch = onOpenMatch,
                            onAccept = { vm.accept(row, onAccepted) }, onReject = { rejecting = row }, onCancel = {})
                    }
                    section(R.string.challenges_waiting, ui.waiting) { row ->
                        ChallengeCard(row, enabled = !ui.busy, onOpenMatch = onOpenMatch,
                            onAccept = {}, onReject = {}, onCancel = { vm.cancel(row) })
                    }
                    section(R.string.challenges_history, ui.history) { row ->
                        ChallengeCard(row, enabled = !ui.busy, onOpenMatch = onOpenMatch, onAccept = {}, onReject = {}, onCancel = {})
                    }
                }
            }
        }
    }

    // Rechazar pide el motivo: le llega al entrenador que desafió
    rejecting?.let { row ->
        ReasonDialog(
            title = stringResource(R.string.challenges_reject_title),
            onDismiss = { rejecting = null },
            onSubmit = { reason -> vm.reject(row, reason); rejecting = null },
        )
    }
}

/** Agrega una sección con título solo si tiene filas. */
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int,
    rows: List<ChallengeRow>,
    card: @Composable (ChallengeRow) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "h$title") { SectionHeader(stringResource(title)) }
    items(rows, key = { it.id }) { card(it) }
}

@Composable
private fun statusPill(row: ChallengeRow) {
    val (text, color) = when (row.status) {
        "accepted" -> R.string.challenges_status_accepted to SuccessGreen
        "rejected" -> R.string.challenges_status_rejected to ErrorRed
        "cancelled" -> R.string.challenges_status_cancelled to MaterialTheme.colorScheme.onSurfaceVariant
        "expired" -> R.string.challenges_status_expired to MaterialTheme.colorScheme.onSurfaceVariant
        else -> R.string.challenges_status_pending to WarnAmber
    }
    Pill(stringResource(text), color)
}

/**
 * Tarjeta de un desafío: quién desafía a quién (con academia y entrenador), los ajustes del
 * partido y las acciones que corresponden a quien la mira.
 */
@Composable
private fun ChallengeCard(
    row: ChallengeRow,
    enabled: Boolean,
    onOpenMatch: (String) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
) {
    val small = MaterialTheme.typography.bodySmall
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    val unknown = stringResource(R.string.challenges_no_coach)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row { statusPill(row) }
            Spacer(Modifier.height(2.dp))
            // Local (quien desafía)
            Text(
                stringResource(R.string.challenges_home_line, row.homeTeamName, row.homeCategory ?: ""),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(R.string.challenges_team_detail, row.homeAcademyName ?: "", row.homeCoachName ?: unknown), style = small, color = soft)
            // Visitante (el desafiado)
            Text(
                stringResource(R.string.challenges_away_line, row.awayTeamName, row.awayCategory ?: ""),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(R.string.challenges_team_detail, row.awayAcademyName ?: "", row.awayCoachName ?: unknown), style = small, color = soft)
            Spacer(Modifier.height(4.dp))
            // Ajustes del partido propuestos
            Text(
                listOfNotNull(
                    stringResource(R.string.challenges_format, row.formatPlayers, row.formatPlayers),
                    stringResource(R.string.challenges_half, row.halfMinutes),
                    formatDayTime(row.scheduledAt),
                    row.venue?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            row.decisionReason?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.challenges_reason, it), style = small, color = soft)
            }
            when {
                row.canRespond -> Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReject, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.challenges_reject)) }
                    Button(onClick = onAccept, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.challenges_accept)) }
                }
                row.canCancel -> OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.challenges_cancel))
                }
                row.status == "accepted" && row.matchId != null -> TextButton(onClick = { onOpenMatch(row.matchId) }) {
                    Text(stringResource(R.string.challenges_open_match))
                }
            }
        }
    }
}
