package com.sportpro.app.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.MatchFilter
import com.sportpro.app.core.data.MatchFilters
import com.sportpro.app.core.data.MatchOrigin
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.MatchScope
import com.sportpro.app.core.data.MatchStatusFilter
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.core.data.parseInstant
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una lista de partidos separada por estado y ya ordenada para mostrar. */
class MatchGroups(val all: List<MatchRow>) {
    val live: List<MatchRow> get() = all.filter { it.isLive }
    val upcoming: List<MatchRow> get() = all.filter { it.isScheduled }
        .sortedBy { parseInstant(it.scheduledAt)?.toEpochMilli() ?: Long.MAX_VALUE }
    val finished: List<MatchRow> get() = all.filter { it.isFinished }
        .sortedByDescending { parseInstant(it.finishedAt ?: it.scheduledAt)?.toEpochMilli() ?: 0L }
}

data class MatchesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** «Mis partidos»: los que el servidor dice que le corresponden a este usuario. */
    val all: List<MatchRow> = emptyList(),
    /** Motivo de cada partido (favorito, sigo al equipo, juega mi hijo…), por id de partido. */
    val scopes: Map<String, MatchScope> = emptyMap(),
    /** Filtros elegidos en la pestaña «Partidos». */
    val filter: MatchFilter = MatchFilter(),
) {
    // Sin filtrar: lo usa el Calendario
    val live: List<MatchRow> get() = MatchGroups(all).live
    val upcoming: List<MatchRow> get() = MatchGroups(all).upcoming
    val finished: List<MatchRow> get() = MatchGroups(all).finished

    /** Con los filtros aplicados: lo usa la pestaña «Partidos». */
    val shown: MatchGroups get() = MatchGroups(MatchFilters.apply(all, scopes, filter))
    val origins: List<MatchOrigin> get() = MatchFilters.availableOrigins(scopes.values)
    fun isFavorite(matchId: String): Boolean = scopes[matchId]?.isFavorite == true
}

/**
 * Lo comparten Partidos y Calendario. Realtime lo mantiene al día sin recargar a mano (D-19).
 *
 * Qué partidos se muestran lo decide el servidor (RPC my_matches): equipos y academias que sigo,
 * equipos donde juega mi hijo, equipos que dirijo, donde juego yo y mis favoritos. Aquí solo se
 * guardan, se filtran en memoria y se marca/desmarca el favorito.
 */
@OptIn(FlowPreview::class)
class MatchesViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(MatchesUiState())
    val ui: StateFlow<MatchesUiState> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
        viewModelScope.launch {
            container.matches.observeMatches()
                .debounce(400)
                .catch { /* sin Realtime la lista sigue funcionando con recarga manual */ }
                .collect { refresh(silent = true) }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _ui.update { it.copy(loading = true, error = null) }
            try {
                // 1) el servidor dice qué partidos son míos y por qué; 2) se traen esos partidos
                val scopes = container.matches.myMatchScopes()
                val list = container.matches.listMatchesByIds(scopes.map { it.matchId })
                _ui.update {
                    it.copy(loading = false, error = null, all = list, scopes = scopes.associateBy { s -> s.matchId })
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    // ---------------- Filtros (solo en memoria: no vuelven a consultar al servidor) ----------------
    fun setOrigin(origin: MatchOrigin) = _ui.update { it.copy(filter = it.filter.copy(origin = origin)) }
    fun setStatus(status: MatchStatusFilter) = _ui.update { it.copy(filter = it.filter.copy(status = status)) }
    fun setQuery(query: String) = _ui.update { it.copy(filter = it.filter.copy(query = query.take(40))) }
    fun clearFilters() = _ui.update { it.copy(filter = MatchFilter()) }

    /**
     * Marca o desmarca la estrella. Es «optimista»: se pinta al instante y, si el servidor
     * rechaza el cambio, se devuelve a como estaba y se avisa.
     */
    fun toggleFavorite(matchId: String) {
        val before = _ui.value.scopes[matchId] ?: MatchScope(matchId)
        setFavorite(matchId, before, !before.isFavorite)
        viewModelScope.launch {
            try {
                val now = container.matches.toggleFavorite(matchId)
                setFavorite(matchId, before, now)
                // Si el partido solo estaba en la lista por ser favorito, al desmarcarlo debe salir
                if (!now) refresh(silent = true)
            } catch (e: Exception) {
                setFavorite(matchId, before, before.isFavorite)
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    private fun setFavorite(matchId: String, base: MatchScope, value: Boolean) = _ui.update {
        it.copy(scopes = it.scopes + (matchId to (it.scopes[matchId] ?: base).copy(isFavorite = value)))
    }
}
