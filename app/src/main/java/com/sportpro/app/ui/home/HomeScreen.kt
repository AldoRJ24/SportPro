package com.sportpro.app.ui.home
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.data.UserContext
import com.sportpro.app.ui.components.BellAction
import com.sportpro.app.ui.components.CreateAction
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.LiveStrip
import com.sportpro.app.ui.components.MatchCard
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.rememberNowMs
import com.sportpro.app.ui.feed.CommentsSheet
import com.sportpro.app.ui.feed.FeedViewModel
import com.sportpro.app.ui.feed.PostCard
import com.sportpro.app.ui.matches.MatchesViewModel
import com.sportpro.app.ui.navigation.LocalAppContainer
import kotlinx.coroutines.delay

/**
 * Inicio: franja «En vivo» + PUBLICACIONES (resúmenes IA aprobados, estilo Instagram, con me gusta
 * y comentarios) + resultados y próximos partidos. Deslizar hacia abajo recarga todo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MatchesViewModel,
    ctx: UserContext?,
    onOpenMatch: (String) -> Unit,
    onCreate: (() -> Unit)?,
    onBell: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs()
    val container = LocalAppContainer.current
    val feedVm: FeedViewModel = viewModel { FeedViewModel(container) }
    val feed by feedVm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(feedVm) { feedVm.messages.collect { snack.showSnackbar(it) } }
    // Al volver a Inicio se recargan las publicaciones, y luego cada 20 s mientras esté visible
    // (así una publicación nueva del DT aparece sola en el otro celular).
    LaunchedEffect(Unit) {
        while (true) {
            feedVm.load()
            delay(20_000)
        }
    }
    SpScaffold(
        title = stringResource(R.string.home_sportpro),
        isTab = true,
        navigationIcon = { CreateAction(onCreate) },
        actions = { BellAction(onBell) },
        snackbarHostState = snack,
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = ui.loading,
            onRefresh = {
                vm.refresh()
                feedVm.load()
            },
            modifier = Modifier.padding(pad),
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        stringResource(R.string.home_hola_x, ctx?.displayName ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (ui.live.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.home_en_vivo_ahora)) }
                    item { LiveStrip(ui.live, now, onClick = { onOpenMatch(it.id) }) }
                }
                // ---- Publicaciones (como el inicio de Instagram) ----
                item { SectionHeader(stringResource(R.string.home_publicaciones)) }
                if (feed.posts.isEmpty() && !feed.loading) {
                    item {
                        EmptyState(
                            if (feed.error != null) stringResource(R.string.home_no_se_pudieron_cargar_las) else stringResource(R.string.home_aun_no_hay_publicaciones),
                            feed.error ?: stringResource(R.string.home_cuando_un_dt_apruebe_el),
                        )
                    }
                }
                items(feed.posts, key = { "p" + it.matchId }) { p ->
                    PostCard(
                        post = p,
                        nowMs = now,
                        onLike = { feedVm.toggleLike(p) },
                        onComments = { feedVm.openComments(p) },
                        onOpenMatch = { onOpenMatch(p.matchId) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                item { SectionHeader(stringResource(R.string.home_resultados_y_proximos_partidos)) }
                val matches = ui.live + ui.finished.take(10) + ui.upcoming.take(5)
                if (matches.isEmpty() && !ui.loading) {
                    item {
                        EmptyState(
                            stringResource(R.string.home_tu_inicio_esta_vacio),
                            stringResource(R.string.home_sigue_academias_y_equipos_en),
                        )
                    }
                }
                items(matches, key = { "h" + it.id }) { m ->
                    MatchCard(m, now, onClick = { onOpenMatch(m.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
        // Hoja de comentarios de la publicación elegida
        feed.openPost?.let { p ->
            CommentsSheet(
                post = p,
                comments = feed.comments,
                loading = feed.commentsLoading,
                sending = feed.sending,
                nowMs = now,
                onDismiss = feedVm::closeComments,
                onSend = feedVm::send,
                onDelete = feedVm::delete,
                onToggleEnabled = { feedVm.setCommentsEnabled(p, it) },
            )
        }
    }
}
