package com.sportpro.app.ui.explore

import androidx.compose.ui.res.stringResource
import com.sportpro.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.AcademyRef
import com.sportpro.app.core.data.FollowRow
import com.sportpro.app.core.data.TeamRef
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.BellAction
import com.sportpro.app.ui.components.Crest
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.tryouts.TryoutsEntryCard
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.BrandBlue
import com.sportpro.app.ui.theme.FavoriteGold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUi(
    val loading: Boolean = true,
    val error: String? = null,
    val academies: List<AcademyRef> = emptyList(),
    val teams: List<TeamRef> = emptyList(),
    val follows: List<FollowRow> = emptyList(),
    /** Equipos marcados con estrella (aparte de «Seguir»; migración 019). */
    val favorites: Set<String> = emptySet(),
    val message: String? = null,
)

class ExploreViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ExploreUi())
    val ui: StateFlow<ExploreUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val academies = container.club.visibleAcademies()
                val teams = container.club.visibleTeams()
                val follows = container.club.myFollows()
                // Si los favoritos fallan, Explorar se muestra igual (solo sin estrellas marcadas)
                val favorites = runCatching { container.club.favoriteTeamIds() }.getOrDefault(emptySet())
                _ui.update { ExploreUi(loading = false, academies = academies, teams = teams, follows = follows, favorites = favorites) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun toggleTeam(team: TeamRef, onChanged: () -> Unit) {
        viewModelScope.launch {
            try {
                val existing = _ui.value.follows.firstOrNull { it.teamId == team.id }
                if (existing != null) container.club.unfollow(existing.id) else container.club.followTeam(team.id)
                _ui.update { it.copy(follows = container.club.myFollows()) }
                onChanged()
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.friendlyMessage()) }
            }
        }
    }

    /**
     * Estrella de un equipo. Cambia en pantalla al instante y luego confirma con el servidor;
     * si falla, vuelve a como estaba y muestra el motivo.
     */
    fun toggleFavorite(team: TeamRef) {
        val before = _ui.value.favorites
        val wanted = team.id !in before
        _ui.update { it.copy(favorites = if (wanted) before + team.id else before - team.id) }
        viewModelScope.launch {
            try {
                val now = container.club.toggleTeamFavorite(team.id)
                _ui.update { it.copy(favorites = if (now) it.favorites + team.id else it.favorites - team.id) }
            } catch (e: Exception) {
                _ui.update { it.copy(favorites = before, message = e.friendlyMessage()) }
            }
        }
    }

    fun toggleAcademy(academy: AcademyRef, onChanged: () -> Unit) {
        viewModelScope.launch {
            try {
                val existing = _ui.value.follows.firstOrNull { it.academyId == academy.id }
                if (existing != null) container.club.unfollow(existing.id) else container.club.followAcademy(academy.id)
                _ui.update { it.copy(follows = container.club.myFollows()) }
                onChanged()
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.friendlyMessage()) }
            }
        }
    }
}

/**
 * Explorar: entrada a los avisos de pruebas (con filtros reales) y buscador de academias y
 * equipos verificados para seguirlos.
 */
@Composable
fun ExploreScreen(onBell: () -> Unit, onFollowChanged: () -> Unit, onTryouts: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val vm: ExploreViewModel = viewModel { ExploreViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    SpScaffold(title = stringResource(R.string.explore_explorar), isTab = true, actions = { BellAction(onBell) }) { pad ->
        when {
            ui.loading && ui.academies.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && ui.academies.isEmpty() -> ErrorBox(ui.error!!, onRetry = vm::load, modifier = Modifier.padding(pad))
            else -> {
                val q = query.trim().lowercase()
                val teams = ui.teams.filter {
                    q.isEmpty() || it.name.lowercase().contains(q) || (it.category ?: "").lowercase().contains(q)
                }
                val academies = ui.academies.filter { q.isEmpty() || it.name.lowercase().contains(q) || (it.city ?: "").lowercase().contains(q) }
                LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        OutlinedTextField(
                            query, { query = it },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            placeholder = { Text(stringResource(R.string.explore_buscar_academia_equipo_o_categoria)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    // Avisos de pruebas: pantalla propia con filtros por categoría, posición, fecha y distrito
                    item { TryoutsEntryCard(onClick = onTryouts) }
                    ui.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) } }
                    // Mis equipos favoritos, arriba y a mano (solo sin búsqueda, para no repetirlos)
                    val favoriteTeams = ui.teams.filter { it.id in ui.favorites }
                    if (q.isEmpty() && favoriteTeams.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.explore_favorite_teams)) }
                        items(favoriteTeams, key = { "f" + it.id }) { t ->
                            TeamRowCard(t, ui, vm, onFollowChanged)
                        }
                    }
                    item { SectionHeader(stringResource(R.string.explore_academias)) }
                    if (academies.isEmpty()) item { EmptyState(stringResource(R.string.explore_sin_resultados), stringResource(R.string.explore_prueba_con_otro_nombre)) }
                    items(academies, key = { "a" + it.id }) { a ->
                        val following = ui.follows.any { it.academyId == a.id }
                        FollowRowCard(
                            title = a.name,
                            subtitle = listOfNotNull(a.city, if (a.status == "verified") stringResource(R.string.explore_verificada) else null).joinToString(" · "),
                            verified = a.status == "verified",
                            following = following,
                            onToggle = { vm.toggleAcademy(a, onFollowChanged) },
                            crestPath = a.crestPath,
                        )
                    }
                    item { SectionHeader(stringResource(R.string.explore_equipos)) }
                    items(teams, key = { "t" + it.id }) { t ->
                        TeamRowCard(t, ui, vm, onFollowChanged)
                    }
                }
            }
        }
    }
}

/** Fila de un equipo: datos, estrella de favorito y «Seguir». */
@Composable
private fun TeamRowCard(t: TeamRef, ui: ExploreUi, vm: ExploreViewModel, onFollowChanged: () -> Unit) {
    FollowRowCard(
        title = t.name,
        subtitle = listOfNotNull(t.category, ui.academies.firstOrNull { it.id == t.academyId }?.name).joinToString(" · "),
        verified = false,
        following = ui.follows.any { it.teamId == t.id },
        onToggle = { vm.toggleTeam(t, onFollowChanged) },
        crestPath = t.crestPath,
        favorite = t.id in ui.favorites,
        onFavorite = { vm.toggleFavorite(t) },
    )
}

/**
 * Fila de una academia o equipo: escudo, nombre, datos y el botón «Seguir» / «Siguiendo».
 * Los equipos además llevan la estrella de favorito ([favorite] distinto de null).
 */
@Composable
private fun FollowRowCard(
    title: String,
    subtitle: String,
    verified: Boolean,
    following: Boolean,
    onToggle: () -> Unit,
    crestPath: String? = null,
    favorite: Boolean? = null,
    onFavorite: () -> Unit = {},
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamCrest(title, crestPath, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (verified) {
                        Spacer(Modifier.width(6.dp))
                        Pill("✓", BrandBlue)
                    }
                }
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (favorite != null) {
                IconButton(onClick = onFavorite) {
                    // Misma estrella que los partidos favoritos: dorada si está marcado, gris si no
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(if (favorite) R.string.explore_unfavorite_team else R.string.explore_favorite_team),
                        tint = if (favorite) FavoriteGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                    )
                }
            }
            if (following) OutlinedButton(onClick = onToggle) { Text(stringResource(R.string.explore_siguiendo)) }
            else Button(onClick = onToggle) { Text(stringResource(R.string.explore_seguir)) }
        }
    }
}
