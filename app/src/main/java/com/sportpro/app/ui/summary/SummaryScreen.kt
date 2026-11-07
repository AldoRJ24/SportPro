package com.sportpro.app.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.AppJson
import com.sportpro.app.core.data.SummaryRow
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.WarnAmber
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class SummaryUi(
    val loading: Boolean = true,
    val generating: Boolean = false,
    val summary: SummaryRow? = null,
    val text: String = "",
    val visibility: String = "team",
) {
    val approved: Boolean get() = summary?.status == "approved"
    val remaining: Int get() = 3 - (summary?.generationCount ?: 0)
}

/**
 * Resumen con IA (US-02 / §9.5): generar borrador → editar → «Aprobar y publicar».
 * A Gemini solo se envían los eventos con seudónimos; nada se publica sin la aprobación del DT.
 */
class SummaryViewModel(private val container: AppContainer, private val matchId: String) : ViewModel() {
    private val _ui = MutableStateFlow(SummaryUi())
    val ui: StateFlow<SummaryUi> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val s = container.matches.summary(matchId)
                _ui.update { it.copy(loading = false, summary = s, text = s?.finalText ?: s?.aiText ?: "", visibility = s?.visibility ?: it.visibility) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false) }
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    fun onText(t: String) = _ui.update { it.copy(text = t) }
    fun onVisibility(v: String) = _ui.update { it.copy(visibility = v) }

    // Pide el borrador a la Edge Function; si responde {"error": ...} se muestra el motivo
    // (por ejemplo, el límite de regeneraciones).
    fun generate() {
        viewModelScope.launch {
            _ui.update { it.copy(generating = true) }
            try {
                val raw = container.matches.generateSummary(matchId)
                val obj = runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                val err = (obj?.get("error") as? JsonPrimitive)?.contentOrNull
                if (err != null) _messages.tryEmit(err)
                load()
            } catch (e: Exception) {
                _messages.tryEmit("No se pudo generar: ${e.friendlyMessage()}")
            } finally {
                _ui.update { it.copy(generating = false) }
            }
        }
    }

    fun saveEdit() {
        viewModelScope.launch {
            try {
                container.matches.saveSummaryEdit(matchId, _ui.value.text)
                _messages.tryEmit("Edición guardada")
                load()
            } catch (e: Exception) {
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    // Aprobar: guarda la última edición (si cambió) y publica con la visibilidad elegida.
    fun approve() {
        viewModelScope.launch {
            try {
                val s = _ui.value
                if (s.text != (s.summary?.finalText ?: "")) container.matches.saveSummaryEdit(matchId, s.text)
                container.matches.approveSummary(matchId, s.visibility)
                _messages.tryEmit("Resumen publicado: ya aparece en Inicio")
                load()
            } catch (e: Exception) {
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }
}

@Composable
fun SummaryScreen(matchId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SummaryViewModel = viewModel(key = "summary-$matchId") { SummaryViewModel(container, matchId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }

    SpScaffold(title = "Resumen con IA", onBack = onBack, snackbarHostState = snack) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "La IA recibe solo la lista de eventos registrados, con seudónimos (sin nombres ni edades). No puede inventar jugadas: si cita a alguien inexistente, el borrador se rechaza. Revísalo y edítalo antes de publicar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (ui.summary?.status) {
                    "approved" -> Pill("APROBADO", Mint400)
                    "edited" -> Pill("EDITADO", WarnAmber)
                    "draft" -> Pill("BORRADOR", WarnAmber)
                    else -> Pill("SIN GENERAR", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill("Regeneraciones restantes: ${ui.remaining.coerceAtLeast(0)}", MaterialTheme.colorScheme.secondary)
            }
            if (ui.generating || ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(
                onClick = vm::generate,
                enabled = !ui.generating && !ui.approved && ui.remaining > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ui.summary == null) "Generar borrador" else "Regenerar") }
            OutlinedTextField(
                value = ui.text,
                onValueChange = vm::onText,
                enabled = ui.summary != null && !ui.approved,
                label = { Text("Texto del resumen (editable)") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            if (ui.summary != null && !ui.approved) {
                OutlinedButton(onClick = vm::saveEdit, modifier = Modifier.fillMaxWidth()) { Text("Guardar edición") }
                Text("Visibilidad de la publicación", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("team" to "Solo mi equipo", "academy" to "Mi academia", "public" to "Todos").forEach { (k, label) ->
                        FilterChip(selected = ui.visibility == k, onClick = { vm.onVisibility(k) }, label = { Text(label) })
                    }
                }
                Button(onClick = vm::approve, enabled = ui.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Aprobar y publicar") }
            }
            Text(
                "Etiqueta al publicar: «Resumen generado con IA y revisado por el entrenador».",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
