package com.sportpro.app.ui.tryouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.data.MyTryoutApplication
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.Tryout
import com.sportpro.app.core.data.TryoutDateFilter
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.theme.LiveRed
import com.sportpro.app.ui.theme.SuccessGreen
import com.sportpro.app.ui.theme.WarnAmber

/** «Arquero, Delantero» a partir de los códigos; lista vacía = «Todas las posiciones». */
@Composable
fun positionsLabel(codes: List<String>): String =
    if (codes.isEmpty()) stringResource(R.string.tryouts_positions_all)
    else codes.mapNotNull { Position.of(it)?.label }.joinToString(", ")

/** Etiqueta y color del estado de una inscripción. */
@Composable
fun applicationStatus(status: String): Pair<String, Color> = when (status) {
    "approved" -> stringResource(R.string.tryouts_app_approved) to SuccessGreen
    "rejected" -> stringResource(R.string.tryouts_app_rejected) to LiveRed
    "withdrawn" -> stringResource(R.string.tryouts_app_withdrawn) to MaterialTheme.colorScheme.onSurfaceVariant
    else -> stringResource(R.string.tryouts_app_pending) to WarnAmber
}

/**
 * Avisos de pruebas: búsqueda con filtros por categoría, posición, fecha y distrito.
 * Los filtros se envían al servidor (función tryout_search); la app no filtra una lista local.
 * [canPublish] = el usuario es admin o DT de alguna academia verificada.
 */
@Composable
fun TryoutsScreen(
    canPublish: Boolean,
    onBack: () -> Unit,
    onOpen: (tryoutId: String) -> Unit,
    onCreate: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: TryoutsViewModel = viewModel { TryoutsViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Al volver del detalle (o de publicar) puede haber cambiado algo
    LaunchedEffect(Unit) { vm.load(silent = true) }

    SpScaffold(
        title = stringResource(R.string.tryouts_title),
        onBack = onBack,
        actions = {
            if (canPublish) TextButton(onClick = onCreate) { Text(stringResource(R.string.tryouts_publish)) }
        },
    ) { pad ->
        when {
            ui.loading && ui.all.isEmpty() -> LoadingBox(Modifier.padding(pad))
            ui.error != null && ui.all.isEmpty() -> ErrorBox(ui.error!!, onRetry = { vm.load() }, modifier = Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                // ---- Filtros ----
                item {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FilterRow(stringResource(R.string.tryouts_filter_date)) {
                            TryoutDateFilter.entries.forEach { f ->
                                FilterChip(
                                    selected = ui.dateFilter == f,
                                    onClick = { vm.setDateFilter(f) },
                                    label = { Text(dateFilterLabel(f)) },
                                )
                            }
                        }
                        FilterRow(stringResource(R.string.tryouts_filter_position)) {
                            Position.entries.forEach { p ->
                                FilterChip(
                                    selected = ui.position == p.code,
                                    onClick = { vm.setPosition(p.code) },
                                    label = { Text(p.label) },
                                )
                            }
                        }
                        // Categoría y distrito solo aparecen si hay avisos de donde sacar opciones
                        if (ui.categories.isNotEmpty()) {
                            FilterRow(stringResource(R.string.tryouts_filter_category)) {
                                ui.categories.forEach { c ->
                                    FilterChip(selected = ui.category.equals(c, ignoreCase = true), onClick = { vm.setCategory(c) }, label = { Text(c) })
                                }
                            }
                        }
                        if (ui.districts.isNotEmpty()) {
                            FilterRow(stringResource(R.string.tryouts_filter_district)) {
                                ui.districts.forEach { d ->
                                    FilterChip(selected = ui.district.equals(d, ignoreCase = true), onClick = { vm.setDistrict(d) }, label = { Text(d) })
                                }
                            }
                        }
                        if (ui.hasFilters) {
                            TextButton(onClick = vm::clearFilters, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(stringResource(R.string.tryouts_filter_clear))
                            }
                        }
                    }
                }

                // ---- Resultados ----
                item {
                    SectionHeader(pluralStringResource(R.plurals.tryouts_results, ui.results.size, ui.results.size))
                }
                if (ui.results.isEmpty()) {
                    item {
                        EmptyState(
                            stringResource(R.string.tryouts_empty_title),
                            stringResource(if (ui.hasFilters) R.string.tryouts_empty_filtered else R.string.tryouts_empty_body),
                        )
                    }
                }
                items(ui.results, key = { "t" + it.id }) { t -> TryoutCard(t, onClick = { onOpen(t.id) }) }

                // ---- Mis inscripciones ----
                if (ui.mine.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.tryouts_mine_header)) }
                    items(ui.mine, key = { "m" + it.id }) { a -> MyApplicationRow(a, onClick = { onOpen(a.tryoutId) }) }
                }
            }
        }
    }
}

@Composable
private fun dateFilterLabel(filter: TryoutDateFilter): String = when (filter) {
    TryoutDateFilter.ALL -> stringResource(R.string.tryouts_date_all)
    TryoutDateFilter.WEEK -> stringResource(R.string.tryouts_date_week)
    TryoutDateFilter.MONTH -> stringResource(R.string.tryouts_date_month)
}

/** Una fila de chips que se desliza a los lados si no cabe en la pantalla. */
@Composable
private fun FilterRow(label: String, chips: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        chips()
    }
}

@Composable
private fun TryoutCard(t: Tryout, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            TeamCrest(t.academyName, t.crestPath, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    TryoutStatusPill(t)
                }
                Text("${t.academyName} · ${t.category}", style = MaterialTheme.typography.bodySmall, color = muted)
                Text(formatDayTime(t.eventAt), style = MaterialTheme.typography.bodyMedium)
                Text("${t.venue} · ${t.district}", style = MaterialTheme.typography.bodySmall, color = muted)
                Text(
                    stringResource(R.string.tryouts_ages, t.minAge, t.maxAge) + " · " + positionsLabel(t.positions),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                Text(
                    pluralStringResource(R.plurals.tryouts_slots_left, t.slotsLeft, t.slotsLeft, t.slots),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (t.slotsLeft == 0) LiveRed else SuccessGreen,
                )
                // Solo quien gestiona recibe este número del servidor
                if (t.canManage && t.pendingCount > 0) {
                    Text(
                        pluralStringResource(R.plurals.tryouts_pending, t.pendingCount, t.pendingCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = WarnAmber,
                    )
                }
            }
        }
    }
}

/** Etiqueta del aviso: cerrado/cancelado (solo lo ve quien gestiona) o «Inscrito» si ya tengo una inscripción. */
@Composable
fun TryoutStatusPill(t: Tryout) {
    when {
        t.status == "cancelled" -> Pill(stringResource(R.string.tryouts_status_cancelled), LiveRed)
        t.status == "closed" -> Pill(stringResource(R.string.tryouts_status_closed), WarnAmber)
        t.myApplications > 0 -> Pill(stringResource(R.string.tryouts_status_applied), SuccessGreen)
    }
}

@Composable
private fun MyApplicationRow(a: MyTryoutApplication, onClick: () -> Unit) {
    val (statusText, statusColor) = applicationStatus(a.status)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${a.playerName} → ${a.title}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${a.academyName} · ${formatDayTime(a.eventAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Pill(statusText, statusColor)
        }
    }
}

/** Tarjeta de entrada a los avisos de pruebas (se muestra en Explorar). */
@Composable
fun TryoutsEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.tryouts_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.tryouts_entry_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClick) { Text(stringResource(R.string.tryouts_entry_action)) }
        }
    }
}
