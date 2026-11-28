package com.sportpro.app.ui.home

import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.data.UserContext
import com.sportpro.app.ui.components.BellAction
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.rememberNowMs
import com.sportpro.app.ui.feed.CommentsSheet
import com.sportpro.app.ui.feed.FeedViewModel
import com.sportpro.app.ui.feed.PostCard
import com.sportpro.app.ui.navigation.LocalAppContainer
import kotlinx.coroutines.delay

/**
 * Inicio: SOLO publicaciones (resúmenes con IA aprobados por el DT, estilo Instagram, con me gusta
 * y comentarios). Los partidos tienen su propia pestaña. Deslizar hacia abajo recarga.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    ctx: UserContext?,
    onOpenMatch: (String) -> Unit,
    onBell: () -> Unit,
) {
    val now = rememberNowMs()
    val container = LocalAppContainer.current
    val feedVm: FeedViewModel = viewModel { FeedViewModel(container) }
    val feed by feedVm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    // El indicador de recarga solo se muestra cuando la persona desliza, no en la recarga automática
    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(feedVm) { feedVm.messages.collect { snack.showSnackbar(it) } }
    // Al volver a Inicio se recargan las publicaciones, y luego cada 20 s mientras esté visible
    // (así una publicación nueva del DT aparece sola en el otro celular).
    LaunchedEffect(Unit) {
        while (true) {
            feedVm.load()
            delay(20_000)
        }
    }
    LaunchedEffect(pulling) {
        if (pulling) {
            delay(800)
            pulling = false
        }
    }
    SpScaffold(
        title = stringResource(R.string.home_sportpro),
        isTab = true,
        actions = { BellAction(onBell) },
        snackbarHostState = snack,
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = pulling,
            onRefresh = {
                pulling = true
                feedVm.load()
            },
            // El indicador de recarga baja desde debajo de la barra superior, no desde el borde
            modifier = Modifier.padding(top = pad.calculateTopPadding()),
        ) {
            // La lista empieza debajo de la barra superior y termina sobre la inferior, pero pasa
            // por detrás de ambas al deslizar: así se ve el efecto de vidrio
            LazyColumn(contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
                item {
                    Text(
                        stringResource(R.string.home_hola_x, ctx?.displayName ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
                    )
                }
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
                onReport = feedVm::report,
                bannedUntil = feed.bannedUntil,
            )
        }
    }
}
