package com.sportpro.app.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.core.data.parseInstant
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val all: List<MatchRow> = emptyList(),
) {
    val live: List<MatchRow> get() = all.filter { it.isLive }
    val upcoming: List<MatchRow> get() = all.filter { it.isScheduled }
        .sortedBy { parseInstant(it.scheduledAt)?.toEpochMilli() ?: Long.MAX_VALUE }
    val finished: List<MatchRow> get() = all.filter { it.isFinished }
        .sortedByDescending { parseInstant(it.finishedAt ?: it.scheduledAt)?.toEpochMilli() ?: 0L }
}

/** Shared by Inicio, Partidos and Calendario. Realtime keeps it fresh without manual reload (D-19). */
@OptIn(FlowPreview::class)
class MatchesViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(MatchesUiState())
    val ui: StateFlow<MatchesUiState> = _ui.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            container.matches.observeMatches()
                .debounce(400)
                .catch { /* realtime unavailable: the list still works with manual refresh */ }
                .collect { refresh(silent = true) }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _ui.update { it.copy(loading = true, error = null) }
            try {
                val list = container.matches.listMatches()
                _ui.update { MatchesUiState(loading = false, all = list) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }
}
