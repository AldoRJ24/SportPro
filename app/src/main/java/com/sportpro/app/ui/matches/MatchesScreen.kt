package com.sportpro.app.ui.matches
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** Sección «Partidos» (§9.8): en vivo arriba, luego próximos y finalizados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    vm: MatchesViewModel,
    onOpenMatch: (String) -> Unit,
    onCreate: (() -> Unit)?,
    onBell: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs()
    SpScaffold(
        title = stringResource(R.string.matches_partidos),
        isTab = true,
        navigationIcon = { CreateAction(onCreate) },
        actions = { BellAction(onBell) },
    ) { pad ->
        when {
            ui.loading && ui.all.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && ui.all.isEmpty() -> ErrorBox(ui.error!!, onRetry = { vm.refresh() }, modifier = Modifier.padding(pad))
            else -> PullToRefreshBox(
                isRefreshing = ui.loading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.padding(pad),
            ) {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (ui.all.isEmpty()) {
                        item {
                            EmptyState(
                                stringResource(R.string.matches_aun_no_hay_partidos),
                                if (onCreate != null) stringResource(R.string.matches_crea_el_primero_con_el) else stringResource(R.string.matches_sigue_a_un_equipo_en),
                                actionLabel = if (onCreate != null) stringResource(R.string.matches_crear_partido) else null,
                                onAction = onCreate,
                            )
                        }
                    }
                    if (ui.live.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.matches_en_vivo)) }
                        item { LiveStrip(ui.live, now, onClick = { onOpenMatch(it.id) }) }
                        items(ui.live, key = { "l" + it.id }) { m ->
                            MatchCard(m, now, onClick = { onOpenMatch(m.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                    if (ui.upcoming.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.matches_proximos)) }
                        items(ui.upcoming, key = { "u" + it.id }) { m ->
                            MatchCard(m, now, onClick = { onOpenMatch(m.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                    if (ui.finished.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.matches_finalizados)) }
                        items(ui.finished, key = { "f" + it.id }) { m ->
                            MatchCard(m, now, onClick = { onOpenMatch(m.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
