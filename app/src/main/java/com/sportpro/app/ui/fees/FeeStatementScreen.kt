package com.sportpro.app.ui.fees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.data.FeeStatementRow
import com.sportpro.app.core.data.Fees
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.ReasonDialog
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.UiMessageEffect
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.LiveRed
import com.sportpro.app.ui.theme.SuccessGreen
import com.sportpro.app.ui.theme.WarnAmber

/** Qué diálogo está abierto y sobre qué cobro. Solo uno a la vez. */
private sealed interface FeeDialog {
    val row: FeeStatementRow
    data class Report(override val row: FeeStatementRow) : FeeDialog     // la familia informa su pago
    data class Register(override val row: FeeStatementRow) : FeeDialog   // el admin registra un pago recibido
    data class Reject(override val row: FeeStatementRow) : FeeDialog
    data class Void(override val row: FeeStatementRow) : FeeDialog
    data class Waive(override val row: FeeStatementRow) : FeeDialog
}

/**
 * Estado de cuenta: lista de cobros con todos los detalles de cada pago.
 * La misma pantalla sirve para tres personas, y los botones salen de lo que dice el servidor
 * en cada fila (`can_pay`, `can_admin`):
 *  - quien paga (apoderado o jugador adulto): «Informar pago»;
 *  - el admin: confirmar, rechazar, registrar, exonerar o anular;
 *  - el DT: solo ve los movimientos (no recibe ningún botón).
 *
 * [teamId] y [playerId] nulos = «Mis mensualidades».
 */
@Composable
fun FeeStatementScreen(teamId: String?, playerId: String?, title: String?, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: FeeStatementViewModel = viewModel(key = "fees-statement-$teamId-$playerId") {
        FeeStatementViewModel(container, teamId, playerId)
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<FeeDialog?>(null) }
    UiMessageEffect(ui.message, snack, vm::consumeMessage)

    val isMine = teamId == null && playerId == null
    // Sin nombre (se llegó desde una notificación) el título es solo «Mensualidades»
    val screenTitle = if (isMine) stringResource(R.string.fees_my_title)
    else if (title == null) stringResource(R.string.fees_title)
    else stringResource(R.string.fees_movements_title, title)

    SpScaffold(title = screenTitle, onBack = onBack, snackbarHostState = snack) { pad ->
        when {
            ui.loading && ui.rows.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && ui.rows.isEmpty() -> ErrorBox(ui.error!!, onRetry = { vm.load() }, modifier = Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SimulatedNotice(Modifier.padding(16.dp)) }
                if (ui.rows.isEmpty()) {
                    item {
                        EmptyState(
                            stringResource(R.string.fees_statement_empty_title),
                            stringResource(if (isMine) R.string.fees_statement_empty_payer else R.string.fees_statement_empty_staff),
                        )
                    }
                } else {
                    item {
                        Text(
                            if (ui.totalDue > 0.0) stringResource(R.string.fees_total_due, Fees.money(ui.totalDue))
                            else stringResource(if (isMine) R.string.fees_total_none else R.string.fees_up_to_date),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                items(ui.rows, key = { it.chargeId }) { row ->
                    ChargeCard(
                        row = row,
                        // Si la pantalla es de un solo jugador, repetir su nombre en cada tarjeta sobra
                        showPlayer = playerId == null,
                        busy = ui.busy,
                        onReport = { dialog = FeeDialog.Report(row) },
                        onRegister = { dialog = FeeDialog.Register(row) },
                        onConfirm = { vm.confirm(row) },
                        onReject = { dialog = FeeDialog.Reject(row) },
                        onVoid = { dialog = FeeDialog.Void(row) },
                        onWaive = { dialog = FeeDialog.Waive(row) },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is FeeDialog.Report -> PaymentDialog(
            title = stringResource(R.string.fees_pay_dialog_report_title, Fees.periodLabel(d.row.period)),
            hint = stringResource(R.string.fees_pay_dialog_report_hint),
            amount = d.row.amount,
            onDismiss = { dialog = null },
            onSubmit = { method, reference, note -> dialog = null; vm.report(d.row, method, reference, note) },
        )
        is FeeDialog.Register -> PaymentDialog(
            title = stringResource(R.string.fees_pay_dialog_register_title, Fees.periodLabel(d.row.period)),
            hint = stringResource(R.string.fees_pay_dialog_register_hint),
            amount = d.row.amount,
            onDismiss = { dialog = null },
            onSubmit = { method, reference, note -> dialog = null; vm.register(d.row, method, reference, note) },
        )
        is FeeDialog.Reject -> ReasonDialog(
            title = stringResource(R.string.fees_reason_reject_title),
            onDismiss = { dialog = null },
            onSubmit = { reason -> dialog = null; vm.reject(d.row, reason) },
        )
        is FeeDialog.Void -> ReasonDialog(
            title = stringResource(R.string.fees_reason_void_title),
            onDismiss = { dialog = null },
            onSubmit = { reason -> dialog = null; vm.void(d.row, reason) },
        )
        is FeeDialog.Waive -> ReasonDialog(
            title = stringResource(R.string.fees_reason_waive_title),
            onDismiss = { dialog = null },
            onSubmit = { reason -> dialog = null; vm.waive(d.row, reason) },
        )
    }
}

/** Nombre visible del método de pago a partir del código de la base de datos. */
@Composable
private fun methodLabel(method: String?): String = when (method) {
    "yape" -> stringResource(R.string.fees_method_yape)
    "plin" -> stringResource(R.string.fees_method_plin)
    "transfer" -> stringResource(R.string.fees_method_transfer)
    "cash" -> stringResource(R.string.fees_method_cash)
    else -> method.orEmpty()
}

/** Etiqueta de estado. «Por confirmar» gana a pendiente/vencido: la familia ya hizo su parte. */
@Composable
private fun StatusPill(row: FeeStatementRow) {
    val (text, color) = when {
        row.isOpen && row.isAwaitingReview -> stringResource(R.string.fees_status_reported) to WarnAmber
        row.status == "overdue" -> stringResource(R.string.fees_status_overdue) to LiveRed
        row.status == "paid" -> stringResource(R.string.fees_status_paid) to SuccessGreen
        row.status == "waived" -> stringResource(R.string.fees_status_waived) to MaterialTheme.colorScheme.secondary
        else -> stringResource(R.string.fees_status_pending) to WarnAmber
    }
    Pill(text, color)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChargeCard(
    row: FeeStatementRow,
    showPlayer: Boolean,
    busy: Boolean,
    onReport: () -> Unit,
    onRegister: () -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onVoid: () -> Unit,
    onWaive: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // ---- El cobro ----
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Fees.periodLabel(row.period), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusPill(row)
            }
            Text(Fees.money(row.amount), style = MaterialTheme.typography.titleLarge)
            if (showPlayer) {
                Text("${row.playerName} · ${row.teamName}", style = MaterialTheme.typography.bodyMedium)
            }
            if (row.isOpen) {
                Text(
                    stringResource(R.string.fees_due_on, Fees.dateLabel(row.dueDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.status == "overdue") LiveRed else muted,
                )
            }
            row.waivedReason?.let {
                Text(stringResource(R.string.fees_waived_reason, it), style = MaterialTheme.typography.bodySmall, color = muted)
            }

            // ---- El último movimiento de pago, con todos sus detalles ----
            if (row.paymentId != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                PaymentDetails(row, muted)
            }

            // ---- Acciones: solo las que el servidor permite a este usuario ----
            val canReport = row.canPay && row.isOpen && !row.isAwaitingReview
            val canReview = row.canAdmin && row.isAwaitingReview
            val canSettle = row.canAdmin && row.isOpen && !row.isAwaitingReview
            val canVoid = row.canAdmin && row.status == "paid" && row.paymentStatus == "confirmed"
            if (canReport || canReview || canSettle || canVoid) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canReport) Button(onClick = onReport, enabled = !busy) { Text(stringResource(R.string.fees_action_report)) }
                    if (canReview) {
                        Button(onClick = onConfirm, enabled = !busy) { Text(stringResource(R.string.fees_action_confirm)) }
                        OutlinedButton(onClick = onReject, enabled = !busy) { Text(stringResource(R.string.fees_action_reject)) }
                    }
                    if (canSettle) {
                        OutlinedButton(onClick = onRegister, enabled = !busy) { Text(stringResource(R.string.fees_action_register)) }
                        TextButton(onClick = onWaive, enabled = !busy) { Text(stringResource(R.string.fees_action_waive)) }
                    }
                    if (canVoid) TextButton(onClick = onVoid, enabled = !busy) { Text(stringResource(R.string.fees_action_void)) }
                }
            }
        }
    }
}

/** Detalle de la transacción: método, fecha, número de operación, quién la informó y qué decidió el admin. */
@Composable
private fun PaymentDetails(row: FeeStatementRow, muted: Color) {
    val small = MaterialTheme.typography.bodySmall
    val unknown = stringResource(R.string.fees_unknown_person)
    Text(stringResource(R.string.fees_payment_line, methodLabel(row.method), Fees.dateLabel(row.paidOn)), style = MaterialTheme.typography.bodyMedium)
    row.reference?.let { Text(stringResource(R.string.fees_payment_reference, it), style = small, color = muted) }
    row.note?.let { Text(stringResource(R.string.fees_payment_note, it), style = small, color = muted) }
    Text(stringResource(R.string.fees_payment_reported_by, row.reportedByName ?: unknown), style = small, color = muted)
    when (row.paymentStatus) {
        "reported" -> Text(stringResource(R.string.fees_payment_awaiting), style = small, color = WarnAmber)
        "confirmed" -> Text(stringResource(R.string.fees_payment_confirmed_by, row.decidedByName ?: unknown), style = small, color = SuccessGreen)
        "rejected" -> Text(stringResource(R.string.fees_payment_rejected, row.decisionReason.orEmpty()), style = small, color = LiveRed)
        "voided" -> Text(stringResource(R.string.fees_payment_voided, row.decisionReason.orEmpty()), style = small, color = LiveRed)
    }
}

/**
 * Diálogo de pago (lo usan la familia para informar y el admin para registrar):
 * método, número de operación y nota. En efectivo no se pide número de operación.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentDialog(
    title: String,
    hint: String,
    amount: Double,
    onDismiss: () -> Unit,
    onSubmit: (method: String, reference: String, note: String) -> Unit,
) {
    var method by rememberSaveable { mutableStateOf(Fees.METHODS.first()) }
    var reference by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val needsReference = Fees.needsReference(method)
    val valid = Fees.isReferenceValid(method, reference)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Fees.money(amount), style = MaterialTheme.typography.headlineSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.fees_pay_dialog_method), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fees.METHODS.forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, label = { Text(methodLabel(m)) })
                    }
                }
                if (needsReference) {
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it.take(40) },
                        label = { Text(stringResource(R.string.fees_pay_dialog_reference)) },
                        singleLine = true,
                        isError = reference.isNotBlank() && !valid,
                        supportingText = {
                            if (reference.isNotBlank() && !valid) Text(stringResource(R.string.fees_pay_dialog_reference_error))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text(stringResource(R.string.fees_pay_dialog_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                // En efectivo se envía la referencia vacía aunque hubiera texto escrito antes de cambiar de método
                onClick = { onSubmit(method, if (needsReference) reference.trim() else "", note.trim()) },
                enabled = valid,
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
