package com.sportpro.app.ui.docs

import android.util.Log
import androidx.annotation.StringRes
import com.sportpro.app.core.AppText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.R
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.DocList
import com.sportpro.app.core.data.DocTeamRow
import com.sportpro.app.core.data.PlayerDoc
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.UiMessage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------
// 1) Documentos de un jugador (lo usan el apoderado, el DT y el admin)
// ---------------------------------------------------------------------

data class PlayerDocsUi(
    val loading: Boolean = true,
    val error: String? = null,
    val data: DocList? = null,
    /** true mientras se sube o se revisa: desactiva los botones para no repetir la acción. */
    val busy: Boolean = false,
    val message: UiMessage? = null,
    /** URL firmada lista para abrir; la pantalla la abre una vez y la limpia. */
    val openUrl: String? = null,
)

/**
 * Se recarga sola al llegar una notificación: el apoderado ve «APROBADO» sin salir de la
 * pantalla y el DT ve aparecer el documento que le acaban de subir.
 */
@OptIn(FlowPreview::class)
class PlayerDocsViewModel(private val container: AppContainer, private val playerId: String) : ViewModel() {
    private val _ui = MutableStateFlow(PlayerDocsUi())
    val ui: StateFlow<PlayerDocsUi> = _ui.asStateFlow()

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
                val data = container.documents.list(playerId)
                _ui.update { it.copy(loading = false, error = null, data = data) }
            } catch (e: Exception) {
                if (!silent) _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    /** Ejecuta una acción del servidor, muestra el resultado y recarga la lista. */
    private fun act(@StringRes done: Int, block: suspend () -> Unit) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val message = try {
                block()
                UiMessage(res = done)
            } catch (e: Exception) {
                // El detalle técnico va al log (Logcat) para poder diagnosticar; la persona ve el mensaje amable
                Log.w(TAG, "Falló la acción de documentos", e)
                UiMessage(text = e.friendlyMessage())
            }
            _ui.update { it.copy(busy = false, message = message) }
            load(silent = true)
        }
    }

    fun upload(docType: String, bytes: ByteArray, mime: String, expiresIso: String?) =
        act(R.string.docs_done_uploaded) { container.documents.upload(playerId, docType, bytes, mime, expiresIso) }

    fun approve(doc: PlayerDoc) =
        act(R.string.docs_done_approved) { container.documents.review(doc.id, accept = true, reason = null) }

    fun reject(doc: PlayerDoc, reason: String) =
        act(R.string.docs_done_rejected) { container.documents.review(doc.id, accept = false, reason = reason) }

    /** Pide la URL firmada (vence en 5 minutos). Si no tengo permiso, Storage la niega. */
    fun open(doc: PlayerDoc) {
        viewModelScope.launch {
            try {
                val url = container.documents.signedUrl(doc.filePath)
                _ui.update { it.copy(openUrl = url) }
            } catch (e: Exception) {
                _ui.update { it.copy(message = UiMessage(text = e.friendlyMessage())) }
            }
        }
    }

    /** Mensaje local de la pantalla (por ejemplo «el archivo pesa más de 5 MB»). */
    fun notify(@StringRes res: Int) {
        Log.w(TAG, "Aviso local de documentos: ${AppText.get(res)}")
        _ui.update { it.copy(message = UiMessage(res = res)) }
    }

    private companion object {
        const val TAG = "SportProDocs"
    }

    fun consumeUrl() = _ui.update { it.copy(openUrl = null) }
    fun consumeMessage() = _ui.update { it.copy(message = null) }
}

// ---------------------------------------------------------------------
// 2) Plantel de un equipo: qué le falta a cada jugador (DT / admin)
// ---------------------------------------------------------------------

data class TeamDocsUi(
    val loading: Boolean = true,
    val error: String? = null,
    val rows: List<DocTeamRow> = emptyList(),
)

class TeamDocsViewModel(private val container: AppContainer, private val teamId: String) : ViewModel() {
    private val _ui = MutableStateFlow(TeamDocsUi())
    val ui: StateFlow<TeamDocsUi> = _ui.asStateFlow()

    init { load() }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _ui.update { it.copy(loading = true, error = null) }
            try {
                val rows = container.documents.teamStatus(teamId)
                _ui.update { it.copy(loading = false, error = null, rows = rows) }
            } catch (e: Exception) {
                if (!silent) _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }
}
