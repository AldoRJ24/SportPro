package com.sportpro.app.ui.explore

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.sportpro.app.ui.components.PrototypeBanner
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.Mint400
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
                _ui.update { ExploreUi(loading = false, academies = academies, teams = teams, follows = follows) }
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

/** Explorar (US-27/US-18 parcial): buscar academias y equipos verificados y seguirlos (D-19). */
@Composable
fun ExploreScreen(onBell: () -> Unit, onFollowChanged: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ExploreViewModel = viewModel { ExploreViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    SpScaffold(title = "Explorar", isTab = true, actions = { BellAction(onBell) }) { pad ->
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
                            placeholder = { Text("Buscar academia, equipo o categoría") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    ui.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) } }
                    item { SectionHeader("Academias") }
                    if (academies.isEmpty()) item { EmptyState("Sin resultados", "Prueba con otro nombre.") }
                    items(academies, key = { "a" + it.id }) { a ->
                        val following = ui.follows.any { it.academyId == a.id }
                        FollowRowCard(
                            title = a.name,
                            subtitle = listOfNotNull(a.city, if (a.status == "verified") "Verificada" else null).joinToString(" · "),
                            verified = a.status == "verified",
                            following = following,
                            onToggle = { vm.toggleAcademy(a, onFollowChanged) },
                            crestPath = a.crestPath,
                        )
                    }
                    item { SectionHeader("Equipos") }
                    items(teams, key = { "t" + it.id }) { t ->
                        val following = ui.follows.any { it.teamId == t.id }
                        FollowRowCard(
                            title = t.name,
                            subtitle = listOfNotNull(t.category, ui.academies.firstOrNull { it.id == t.academyId }?.name).joinToString(" · "),
                            verified = false,
                            following = following,
                            onToggle = { vm.toggleTeam(t, onFollowChanged) },
                            crestPath = t.crestPath,
                        )
                    }
                    item {
                        PrototypeBanner(
                            "Avisos de pruebas con filtros (categoría, posición, fecha, ubicación) llegan con la migración de comunidad.",
                            Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowRowCard(
    title: String,
    subtitle: String,
    verified: Boolean,
    following: Boolean,
    onToggle: () -> Unit,
    crestPath: String? = null,
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
                        Pill("✓", Mint400)
                    }
                }
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (following) OutlinedButton(onClick = onToggle) { Text("Siguiendo") }
            else Button(onClick = onToggle) { Text("Seguir") }
        }
    }
}
