package com.sportpro.app.ui.matches

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportpro.app.R
import com.sportpro.app.core.data.MatchFilter
import com.sportpro.app.core.data.MatchOrigin
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.MatchStatusFilter
import com.sportpro.app.core.data.lineupsPending
import com.sportpro.app.ui.components.BellAction
import com.sportpro.app.ui.components.CreateAction
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LiveStrip
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.MatchCard
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.rememberNowMs

/**
 * Pestaña «Partidos»: solo los partidos que le interesan al usuario (equipos y academias que
 * sigue, donde juega su hijo, los que dirige y sus favoritos; lo decide el servidor).
 * Arriba hay un buscador y dos filas de filtros; cada tarjeta tiene una estrella de favorito.
 * Orden: en vivo, próximos y finalizados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    vm: MatchesViewModel,
    onOpenMatch: (String) -> Unit,
    onCreate: (() -> Unit)?,
    onBell: () -> Unit,
    /** null = el usuario no gestiona equipos y no ve el acceso a «Desafíos». */
    onChallenges: (() -> Unit)? = null,
    /** Abre «Convocatoria y alineación» (para el entrenador de un partido con alineación pendiente). */
    onOpenSetup: (String) -> Unit = onOpenMatch,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    // La lista filtrada solo se recalcula cuando cambian los partidos, los motivos o los filtros
    val shown = remember(ui.all, ui.scopes, ui.filter) { ui.shown }
    val card: @Composable (MatchRow) -> Unit = { m ->
        // Partido acordado sin alineaciones confirmadas: para sus entrenadores la tarjeta dice
        // «Alineación pendiente» y lleva directo a armarla
        val pending = m.lineupsPending && ui.scopes[m.id]?.byStaff == true
        MatchCard(
            m, now,
            onClick = { if (pending) onOpenSetup(m.id) else onOpenMatch(m.id) },
            lineupPending = pending,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            favorite = ui.isFavorite(m.id),
            onToggleFavorite = { vm.toggleFavorite(m.id) },
        )
    }
    SpScaffold(
        title = stringResource(R.string.matches_partidos),
        isTab = true,
        navigationIcon = { CreateAction(onCreate) },
        actions = { BellAction(onBell) },
        snackbarHostState = snack,
    ) { pad ->
        when {
            ui.loading && ui.all.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && ui.all.isEmpty() -> ErrorBox(ui.error!!, onRetry = { vm.refresh() }, modifier = Modifier.padding(pad))
            else -> Column(Modifier.padding(pad).fillMaxSize()) {
                // Acceso a los desafíos entre equipos (solo DT y administradores)
                if (onChallenges != null) {
                    TextButton(onClick = onChallenges, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.matches_desafios))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
                // Los filtros solo tienen sentido si hay partidos que filtrar
                if (ui.all.isNotEmpty()) {
                    FilterBar(ui.filter, ui.origins, onQuery = vm::setQuery, onOrigin = vm::setOrigin, onStatus = vm::setStatus)
                }
                PullToRefreshBox(
                    isRefreshing = ui.loading,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        when {
                            // No hay ningún partido «mío» todavía
                            ui.all.isEmpty() -> item {
                                EmptyState(
                                    stringResource(R.string.matches_aun_no_hay_partidos),
                                    stringResource(R.string.matches_sigue_a_un_equipo_en),
                                    actionLabel = if (onCreate != null) stringResource(R.string.matches_crear_partido) else null,
                                    onAction = onCreate,
                                )
                            }
                            // Hay partidos, pero ninguno pasa los filtros
                            shown.all.isEmpty() -> item {
                                val onlyFavorites = ui.filter == MatchFilter(origin = MatchOrigin.FAVORITES)
                                EmptyState(
                                    stringResource(if (onlyFavorites) R.string.matches_sin_favoritos else R.string.matches_sin_resultados),
                                    stringResource(if (onlyFavorites) R.string.matches_sin_favoritos_ayuda else R.string.matches_sin_resultados_ayuda),
                                    actionLabel = stringResource(R.string.matches_quitar_filtros),
                                    onAction = vm::clearFilters,
                                )
                            }
                        }
                        if (shown.live.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.matches_en_vivo)) }
                            item { LiveStrip(shown.live, now, onClick = { onOpenMatch(it.id) }) }
                            items(shown.live, key = { "l" + it.id }) { card(it) }
                        }
                        if (shown.upcoming.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.matches_proximos)) }
                            items(shown.upcoming, key = { "u" + it.id }) { card(it) }
                        }
                        if (shown.finished.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.matches_finalizados)) }
                            items(shown.finished, key = { "f" + it.id }) { card(it) }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

/** Buscador + filtro «de quién es el partido» + filtro por estado. Se queda fijo arriba de la lista. */
@Composable
private fun FilterBar(
    filter: MatchFilter,
    origins: List<MatchOrigin>,
    onQuery: (String) -> Unit,
    onOrigin: (MatchOrigin) -> Unit,
    onStatus: (MatchStatusFilter) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.matches_borrar_busqueda))
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.matches_buscar)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // Fila 1: de quién es el partido (solo se ofrecen los motivos que aplican a este usuario)
        ChipRow {
            origins.forEach { o ->
                FilterChip(
                    selected = filter.origin == o,
                    onClick = { onOrigin(o) },
                    label = { Text(originLabel(o)) },
                    leadingIcon = if (o == MatchOrigin.FAVORITES) {
                        { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }
        // Fila 2: estado del partido
        ChipRow {
            MatchStatusFilter.entries.forEach { s ->
                FilterChip(selected = filter.status == s, onClick = { onStatus(s) }, label = { Text(statusLabel(s)) })
            }
        }
    }
}

/** Fila de chips que se desliza de lado si no entra en la pantalla. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun originLabel(o: MatchOrigin): String = stringResource(
    when (o) {
        MatchOrigin.ALL -> R.string.matches_filtro_todos
        MatchOrigin.FAVORITES -> R.string.matches_filtro_favoritos
        MatchOrigin.FOLLOWING -> R.string.matches_filtro_sigo
        MatchOrigin.CHILDREN -> R.string.matches_filtro_mis_hijos
        MatchOrigin.MY_TEAMS -> R.string.matches_filtro_mis_equipos
        MatchOrigin.PLAYING -> R.string.matches_filtro_donde_juego
    },
)

@Composable
private fun statusLabel(s: MatchStatusFilter): String = stringResource(
    when (s) {
        MatchStatusFilter.ALL -> R.string.matches_estado_todos
        MatchStatusFilter.LIVE -> R.string.matches_en_vivo
        MatchStatusFilter.UPCOMING -> R.string.matches_proximos
        MatchStatusFilter.FINISHED -> R.string.matches_finalizados
    },
)
