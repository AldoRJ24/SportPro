package com.sportpro.app.ui.docs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.data.DocState
import com.sportpro.app.core.data.DocTeamRow
import com.sportpro.app.core.data.Docs
import com.sportpro.app.core.data.Fees
import com.sportpro.app.core.data.PlayerDoc
import com.sportpro.app.ui.components.DATE_MASK
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.MaskedDigitsField
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.ReasonDialog
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.UiMessageEffect
import com.sportpro.app.ui.components.parseDateDigits
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.LiveRed
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.WarnAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Nombre visible de cada tipo de documento a partir del código de la base de datos. */
@Composable
private fun docTypeLabel(type: String): String = when (type) {
    "dni" -> stringResource(R.string.docs_type_dni)
    "medical" -> stringResource(R.string.docs_type_medical)
    "insurance" -> stringResource(R.string.docs_type_insurance)
    "travel" -> stringResource(R.string.docs_type_travel)
    else -> type
}

/** Aviso fijo de privacidad: quién puede ver estos archivos. */
@Composable
private fun PrivacyNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            stringResource(R.string.docs_privacy_notice),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * Lee el archivo elegido. Devuelve (bytes, formato) o el id del texto de error.
 * Se comprueba formato y tamaño AQUÍ, antes de subir, para no gastar datos en un archivo
 * que Storage rechazaría. Se lee como máximo 5 MB + 1 byte: basta para saber si se pasa.
 */
private fun readPickedFile(context: Context, uri: Uri): Pair<Pair<ByteArray, String>?, Int?> {
    val mime = context.contentResolver.getType(uri)
    if (mime == null || mime !in Docs.MIME_TYPES) return null to R.string.docs_error_format
    // Las fotos del celular suelen pesar más de 5 MB: se reducen a 2000 px como máximo y se
    // guardan en JPEG. Así entran en el límite, se suben rápido y el texto sigue siendo legible.
    if (mime.startsWith("image/")) {
        val jpeg = try { shrinkImageToJpeg(context, uri) } catch (e: Exception) { null }
            ?: return null to R.string.docs_error_read
        return if (Docs.isSizeOk(jpeg.size)) (jpeg to "image/jpeg") to null else null to R.string.docs_error_size
    }
    val bytes = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(Docs.MAX_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            buffer.copyOf(total)
        }
    } catch (e: Exception) {
        null
    } ?: return null to R.string.docs_error_read
    if (!Docs.isSizeOk(bytes.size)) return null to (if (bytes.isEmpty()) R.string.docs_error_read else R.string.docs_error_size)
    return (bytes to mime) to null
}

/**
 * Abre la foto (ImageDecoder ya aplica la rotación EXIF), la reduce para que su lado más largo
 * no pase de [maxSide] píxeles y la comprime en JPEG.
 */
private fun shrinkImageToJpeg(context: Context, uri: Uri, maxSide: Int = 2000): ByteArray {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1),
            )
        }
        // Bitmap en memoria normal (no de GPU) para poder comprimirlo
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}

// ---------------------------------------------------------------------
// 1) Documentos de un jugador
// ---------------------------------------------------------------------

/**
 * Los 4 tipos de documento de un jugador. Los botones salen de lo que dice el servidor:
 * `can_upload` (apoderado, o jugador adulto) y `can_review` (DT del equipo o admin).
 */
@Composable
fun PlayerDocsScreen(playerId: String, name: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: PlayerDocsViewModel = viewModel(key = "docs-player-$playerId") { PlayerDocsViewModel(container, playerId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    UiMessageEffect(ui.message, snack, vm::consumeMessage)

    // Tipo que se está subiendo y su vencimiento. Se guardan con rememberSaveable porque al abrir
    // el selector de archivos Android puede recrear la pantalla, y no se debe perder qué se subía.
    var uploadType by rememberSaveable { mutableStateOf<String?>(null) }
    var uploadExpiry by rememberSaveable { mutableStateOf<String?>(null) }
    var askExpiryFor by rememberSaveable { mutableStateOf<String?>(null) }
    var rejecting by remember { mutableStateOf<PlayerDoc?>(null) }

    // Selector de archivos del sistema: no necesita permiso de almacenamiento, la app solo
    // recibe el archivo que la persona eligió.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val type = uploadType
        val expiry = uploadExpiry
        uploadType = null
        uploadExpiry = null
        if (uri == null || type == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Leer el archivo puede tardar: se hace fuera del hilo de la interfaz
            val (file, error) = withContext(Dispatchers.IO) { readPickedFile(context, uri) }
            if (file == null) vm.notify(error ?: R.string.docs_error_read)
            else vm.upload(type, file.first, file.second, expiry)
        }
    }

    // Abrir el archivo con la URL firmada (en el navegador o en el visor de PDF / fotos)
    LaunchedEffect(ui.openUrl) {
        val url = ui.openUrl ?: return@LaunchedEffect
        vm.consumeUrl()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            vm.notify(R.string.docs_error_no_viewer)
        }
    }

    SpScaffold(title = stringResource(R.string.docs_title, name), onBack = onBack, snackbarHostState = snack) { pad ->
        val data = ui.data
        when {
            ui.loading && data == null -> LoadingBox(Modifier.padding(pad))
            data == null -> ErrorBox(ui.error.orEmpty(), onRetry = { vm.load() }, modifier = Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { PrivacyNotice(Modifier.padding(16.dp)) }
                if (ui.busy) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.docs_uploading), style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
                items(Docs.TYPES, key = { it }) { type ->
                    val doc = data.docOf(type)
                    DocCard(
                        type = type,
                        doc = doc,
                        state = Docs.stateOf(doc, required = type in data.required),
                        canUpload = data.canUpload,
                        canReview = data.canReview,
                        busy = ui.busy,
                        onView = { doc?.let(vm::open) },
                        onUpload = { askExpiryFor = type },
                        onApprove = { doc?.let(vm::approve) },
                        onReject = { rejecting = doc },
                    )
                }
            }
        }
    }

    askExpiryFor?.let { type ->
        UploadDialog(
            typeLabel = docTypeLabel(type),
            onDismiss = { askExpiryFor = null },
            onChoose = { expiryIso ->
                askExpiryFor = null
                uploadType = type
                uploadExpiry = expiryIso
                picker.launch(Docs.MIME_TYPES.toTypedArray())
            },
        )
    }
    rejecting?.let { doc ->
        ReasonDialog(
            title = stringResource(R.string.docs_reject_title),
            onDismiss = { rejecting = null },
            onSubmit = { reason -> rejecting = null; vm.reject(doc, reason) },
        )
    }
}

@Composable
private fun StatePill(state: DocState) {
    val (text, color) = when (state) {
        DocState.MISSING -> stringResource(R.string.docs_state_missing) to LiveRed
        DocState.OPTIONAL -> stringResource(R.string.docs_state_optional) to MaterialTheme.colorScheme.onSurfaceVariant
        DocState.PENDING -> stringResource(R.string.docs_state_pending) to WarnAmber
        DocState.APPROVED -> stringResource(R.string.docs_state_approved) to Mint400
        DocState.REJECTED -> stringResource(R.string.docs_state_rejected) to LiveRed
        DocState.EXPIRED -> stringResource(R.string.docs_state_expired) to LiveRed
    }
    Pill(text, color)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocCard(
    type: String,
    doc: PlayerDoc?,
    state: DocState,
    canUpload: Boolean,
    canReview: Boolean,
    busy: Boolean,
    onView: () -> Unit,
    onUpload: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.bodySmall
    val someone = stringResource(R.string.docs_someone)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(docTypeLabel(type), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatePill(state)
            }
            if (doc == null) {
                Text(stringResource(R.string.docs_not_uploaded), style = small, color = muted)
            } else {
                Text(
                    doc.expiresOn?.let { stringResource(R.string.docs_expires_on, Fees.dateLabel(it)) }
                        ?: stringResource(R.string.docs_no_expiry),
                    style = small,
                    color = if (doc.isExpired) LiveRed else muted,
                )
                Text(stringResource(R.string.docs_uploaded_by, doc.uploadedByName ?: someone), style = small, color = muted)
                if (doc.status == "approved") {
                    Text(stringResource(R.string.docs_approved_by, doc.reviewedByName ?: someone), style = small, color = muted)
                }
                if (doc.status == "rejected") {
                    Text(stringResource(R.string.docs_rejected_reason, doc.reviewReason.orEmpty()), style = small, color = LiveRed)
                }
            }
            // Solo se revisa lo que está «en revisión»: lo ya aprobado o rechazado no se toca
            val canDecide = canReview && doc != null && doc.status == "pending"
            if (doc != null || canUpload) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (doc != null) OutlinedButton(onClick = onView) { Text(stringResource(R.string.docs_action_view)) }
                    if (canDecide) {
                        Button(onClick = onApprove, enabled = !busy) { Text(stringResource(R.string.docs_action_approve)) }
                        OutlinedButton(onClick = onReject, enabled = !busy) { Text(stringResource(R.string.docs_action_reject)) }
                    }
                    if (canUpload) {
                        TextButton(onClick = onUpload, enabled = !busy) {
                            Text(stringResource(if (doc == null) R.string.docs_action_upload else R.string.docs_action_replace))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Antes de elegir el archivo se pregunta el vencimiento (opcional).
 * [onChoose] recibe la fecha en formato ISO (2027-03-15) o null si no vence.
 */
@Composable
private fun UploadDialog(typeLabel: String, onDismiss: () -> Unit, onChoose: (String?) -> Unit) {
    var digits by rememberSaveable { mutableStateOf("") }
    val date: LocalDate? = if (digits.length == 8) parseDateDigits(digits) else null
    // Vacío = sin vencimiento (válido). Con 8 dígitos debe ser una fecha real y no pasada.
    val error: Int? = when {
        digits.isEmpty() -> null
        digits.length < 8 -> R.string.docs_upload_expiry_invalid
        date == null -> R.string.docs_upload_expiry_invalid
        date.isBefore(LocalDate.now()) -> R.string.docs_upload_expiry_past
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.docs_upload_title, typeLabel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.docs_upload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MaskedDigitsField(
                    value = digits,
                    onValueChange = { digits = it },
                    mask = DATE_MASK,
                    label = stringResource(R.string.docs_upload_expiry_label),
                    // Mientras escribe no se marca error; solo cuando ya puso los 8 dígitos
                    isError = digits.length == 8 && error != null,
                    supportingText = if (digits.length == 8 && error != null) stringResource(error) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(date?.toString()) }, enabled = error == null) {
                Text(stringResource(R.string.docs_upload_choose))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// ---------------------------------------------------------------------
// 2) Plantel de un equipo (DT / admin)
// ---------------------------------------------------------------------

/** Lista del plantel con lo que le falta a cada jugador; primero los incompletos. */
@Composable
fun TeamDocsScreen(teamId: String, name: String, onBack: () -> Unit, onOpenPlayer: (playerId: String, name: String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: TeamDocsViewModel = viewModel(key = "docs-team-$teamId") { TeamDocsViewModel(container, teamId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Al volver de revisar a un jugador, su estado puede haber cambiado
    LaunchedEffect(Unit) { vm.load(silent = true) }

    SpScaffold(title = stringResource(R.string.docs_team_title, name), onBack = onBack) { pad ->
        when {
            ui.loading && ui.rows.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && ui.rows.isEmpty() -> ErrorBox(ui.error!!, onRetry = { vm.load() }, modifier = Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { PrivacyNotice(Modifier.padding(16.dp)) }
                item {
                    Text(
                        stringResource(R.string.docs_team_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (ui.rows.isEmpty()) {
                    item {
                        EmptyState(
                            stringResource(R.string.docs_team_empty_title),
                            stringResource(R.string.docs_team_empty_body),
                        )
                    }
                }
                items(ui.rows, key = { it.playerId }) { row ->
                    TeamDocRow(row, onClick = { onOpenPlayer(row.playerId, row.playerName) })
                }
            }
        }
    }
}

@Composable
private fun TeamDocRow(row: DocTeamRow, onClick: () -> Unit) {
    val small = MaterialTheme.typography.bodySmall
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${row.jerseyNumber ?: "-"}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(row.playerName, style = MaterialTheme.typography.bodyLarge)
                if (row.complete) {
                    Text(stringResource(R.string.docs_team_complete), style = small, color = Mint400)
                } else {
                    // Los códigos (dni, medical…) se traducen a su nombre visible
                    val names = row.missing.map { docTypeLabel(it) }.joinToString(", ")
                    Text(stringResource(R.string.docs_team_missing, names), style = small, color = LiveRed)
                }
                // Detalles que explican POR QUÉ falta: está en revisión, lo rechazaron o venció
                val details = listOfNotNull(
                    row.pendingCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.docs_team_pending, it, it) },
                    row.rejectedCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.docs_team_rejected, it, it) },
                    row.expiredCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.docs_team_expired, it, it) },
                )
                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = small, color = WarnAmber)
            }
        }
    }
}
