package com.sportpro.app.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.NotificationPref
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.UiMessage
import com.sportpro.app.ui.components.UiMessageEffect
import com.sportpro.app.ui.navigation.LocalAppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationSettingsUi(
    val loading: Boolean = true,
    val error: String? = null,
    val prefs: List<NotificationPref> = emptyList(),
    val message: UiMessage? = null,
)

/**
 * Ajustes de notificaciones: un interruptor por categoría.
 * El ajuste se guarda en el SERVIDOR (tabla notification_prefs): al apagar una categoría, la
 * función _notify deja de crear esos avisos para este usuario. No es un filtro del celular,
 * así que vale en cualquier dispositivo donde inicie sesión.
 */
class NotificationSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(NotificationSettingsUi())
    val ui: StateFlow<NotificationSettingsUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val prefs = container.account.notificationPrefs()
                _ui.update { it.copy(loading = false, prefs = prefs) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    /**
     * Cambia el interruptor en pantalla de inmediato y luego lo guarda. Si el servidor falla
     * (por ejemplo, sin internet), el interruptor vuelve a como estaba y se muestra el error.
     */
    fun toggle(category: String, enabled: Boolean) {
        val before = _ui.value.prefs
        _ui.update { s -> s.copy(prefs = s.prefs.map { if (it.category == category) it.copy(enabled = enabled) else it }) }
        viewModelScope.launch {
            try {
                container.account.setNotificationPref(category, enabled)
            } catch (e: Exception) {
                _ui.update { it.copy(prefs = before, message = UiMessage(text = e.friendlyMessage())) }
            }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: NotificationSettingsViewModel = viewModel { NotificationSettingsViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    UiMessageEffect(ui.message, snack, vm::consumeMessage)

    SpScaffold(title = stringResource(R.string.notif_settings_title), onBack = onBack, snackbarHostState = snack) { pad ->
        when {
            ui.loading && ui.prefs.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && ui.prefs.isEmpty() -> ErrorBox(ui.error!!, onRetry = vm::load, modifier = Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        stringResource(R.string.notif_settings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(ui.prefs, key = { it.category }) { pref ->
                    PrefRow(pref, onToggle = { vm.toggle(pref.category, it) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** Título y explicación de cada categoría (el código viene de la base de datos). */
@Composable
private fun categoryTexts(category: String): Pair<String, String> = when (category) {
    "callups" -> stringResource(R.string.notif_cat_callups) to stringResource(R.string.notif_cat_callups_desc)
    "live" -> stringResource(R.string.notif_cat_live) to stringResource(R.string.notif_cat_live_desc)
    "comments" -> stringResource(R.string.notif_cat_comments) to stringResource(R.string.notif_cat_comments_desc)
    "requests" -> stringResource(R.string.notif_cat_requests) to stringResource(R.string.notif_cat_requests_desc)
    "payments" -> stringResource(R.string.notif_cat_payments) to stringResource(R.string.notif_cat_payments_desc)
    else -> category to ""
}

@Composable
private fun PrefRow(pref: NotificationPref, onToggle: (Boolean) -> Unit) {
    val (title, description) = categoryTexts(pref.category)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = pref.enabled,
            onCheckedChange = onToggle,
            // Para lectores de pantalla: el interruptor se anuncia con el nombre de su categoría
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}
