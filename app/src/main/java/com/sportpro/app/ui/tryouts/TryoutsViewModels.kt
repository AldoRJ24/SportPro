package com.sportpro.app.ui.tryouts

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.R
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.MyTryoutApplication
import com.sportpro.app.core.data.Tryout
import com.sportpro.app.core.data.TryoutApplicant
import com.sportpro.app.core.data.TryoutDateFilter
import com.sportpro.app.core.data.Tryouts
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
import java.time.LocalDate

// ---------------------------------------------------------------------
// 1) Búsqueda de avisos con filtros
// ---------------------------------------------------------------------

data class TryoutsUi(
    val loading: Boolean = true,
    val error: String? = null,
    /** Todos los avisos visibles SIN filtrar: de aquí salen las opciones de los chips. */
    val all: List<Tryout> = emptyList(),
    /** Resultado de la búsqueda con los filtros elegidos (lo calcula el servidor). */
    val results: List<Tryout> = emptyList(),
    val mine: List<MyTryoutApplication> = emptyList(),
    val category: String? = null,
    val position: String? = null,
    val district: String? = null,
    val dateFilter: TryoutDateFilter = TryoutDateFilter.ALL,
) {
    val hasFilters: Boolean
        get() = category != null || position != null || district != null || dateFilter != TryoutDateFilter.ALL
    val categories: List<String> get() = Tryouts.options(all) { it.category }
    val districts: List<String> get() = Tryouts.options(all) { it.district }
}

class TryoutsViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(TryoutsUi())
    val ui: StateFlow<TryoutsUi> = _ui.asStateFlow()

    init { load() }

    /**
     * Carga la lista. Con filtros hace dos consultas: una sin filtrar (para saber qué categorías
     * y distritos existen) y otra filtrada en el servidor. Sin filtros basta la primera.
     */
    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _ui.update { it.copy(loading = true, error = null) }
            try {
                val s = _ui.value
                val all = container.tryouts.search()
                val results = if (!s.hasFilters) all else {
                    val (from, to) = Tryouts.dateRange(s.dateFilter, LocalDate.now(Tryouts.ZONE))
                    container.tryouts.search(s.category, s.position, from?.toString(), to?.toString(), s.district)
                }
                // Si falla «mis inscripciones» no se pierde la búsqueda: se deja la lista anterior
                val mine = runCatching { container.tryouts.mine() }.getOrDefault(s.mine)
                _ui.update { it.copy(loading = false, error = null, all = all, results = results, mine = mine) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    // Tocar el chip que ya está elegido lo quita (vuelve a «todas»)
    fun setCategory(value: String) { _ui.update { it.copy(category = value.takeIf { v -> v != it.category }) }; load(silent = true) }
    fun setPosition(value: String) { _ui.update { it.copy(position = value.takeIf { v -> v != it.position }) }; load(silent = true) }
    fun setDistrict(value: String) { _ui.update { it.copy(district = value.takeIf { v -> v != it.district }) }; load(silent = true) }
    fun setDateFilter(value: TryoutDateFilter) { _ui.update { it.copy(dateFilter = value) }; load(silent = true) }

    fun clearFilters() {
        _ui.update { it.copy(category = null, position = null, district = null, dateFilter = TryoutDateFilter.ALL) }
        load(silent = true)
    }
}

// ---------------------------------------------------------------------
// 2) Detalle de un aviso: inscribir, retirar y (quien gestiona) responder
// ---------------------------------------------------------------------

data class TryoutDetailUi(
    val loading: Boolean = true,
    val error: String? = null,
    /** null después de cargar = el aviso ya no está disponible (cerrado, cancelado o pasado). */
    val tryout: Tryout? = null,
    val applicants: List<TryoutApplicant> = emptyList(),
    val mine: List<MyTryoutApplication> = emptyList(),
    val busy: Boolean = false,
    val message: UiMessage? = null,
)

/** Se recarga sola al llegar una notificación (nueva inscripción, o la respuesta a la mía). */
@OptIn(FlowPreview::class)
class TryoutDetailViewModel(private val container: AppContainer, private val tryoutId: String) : ViewModel() {
    private val _ui = MutableStateFlow(TryoutDetailUi())
    val ui: StateFlow<TryoutDetailUi> = _ui.asStateFlow()

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
                val tryout = container.tryouts.search().firstOrNull { it.id == tryoutId }
                // Mis inscripciones a ESTE aviso (también si el aviso ya no aparece en la búsqueda)
                val mine = container.tryouts.mine().filter { it.tryoutId == tryoutId }
                // La lista de inscritos solo se pide si el servidor dice que gestiono el aviso
                val applicants = if (tryout?.canManage == true) container.tryouts.applicants(tryoutId) else emptyList()
                _ui.update { it.copy(loading = false, error = null, tryout = tryout, mine = mine, applicants = applicants) }
            } catch (e: Exception) {
                if (!silent) _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    private fun act(@StringRes done: Int, block: suspend () -> Unit) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val message = try {
                block()
                UiMessage(res = done)
            } catch (e: Exception) {
                UiMessage(text = e.friendlyMessage())
            }
            _ui.update { it.copy(busy = false, message = message) }
            load(silent = true)
        }
    }

    fun apply(playerId: String) = act(R.string.tryouts_done_applied) { container.tryouts.apply(tryoutId, playerId) }
    fun withdraw(applicationId: String) = act(R.string.tryouts_done_withdrawn) { container.tryouts.withdraw(applicationId) }
    fun accept(applicationId: String) = act(R.string.tryouts_done_accepted) { container.tryouts.decide(applicationId, true, null) }
    fun reject(applicationId: String, reason: String) =
        act(R.string.tryouts_done_rejected) { container.tryouts.decide(applicationId, false, reason) }
    fun setStatus(status: String) = act(R.string.tryouts_done_status) { container.tryouts.setStatus(tryoutId, status) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
}

// ---------------------------------------------------------------------
// 3) Publicar un aviso
// ---------------------------------------------------------------------

data class CreateTryoutUi(
    val busy: Boolean = false,
    val message: UiMessage? = null,
    /** id del aviso recién creado: la pantalla navega a su detalle y lo limpia. */
    val createdId: String? = null,
)

class CreateTryoutViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(CreateTryoutUi())
    val ui: StateFlow<CreateTryoutUi> = _ui.asStateFlow()

    fun create(
        academyId: String, title: String, category: String, minAge: Int, maxAge: Int, positions: List<String>,
        eventAtIso: String, venue: String, district: String, slots: Int, notes: String,
    ) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val id = container.tryouts.create(
                    academyId, title.trim(), category.trim(), minAge, maxAge, positions,
                    eventAtIso, venue.trim(), district.trim(), slots, notes.trim().ifBlank { null },
                )
                _ui.update { it.copy(busy = false, createdId = id) }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = UiMessage(text = e.friendlyMessage())) }
            }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
}
