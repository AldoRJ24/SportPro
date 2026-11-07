package com.sportpro.app.ui.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportpro.app.core.data.formatDay
import com.sportpro.app.core.data.parseInstant
import com.sportpro.app.ui.components.BellAction
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.MatchCard
import com.sportpro.app.ui.components.PrototypeBanner
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.rememberNowMs
import com.sportpro.app.ui.matches.MatchesViewModel

/** Calendario (US-13): partidos visibles para el usuario agrupados por día. */
@Composable
fun CalendarScreen(vm: MatchesViewModel, onOpenMatch: (String) -> Unit, onBell: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs()
    val upcoming = (ui.live + ui.upcoming)
    val byDay = upcoming.groupBy { formatDay(it.scheduledAt) }
    val past = ui.finished.take(15).groupBy { formatDay(it.scheduledAt) }
    SpScaffold(title = "Calendario", isTab = true, actions = { BellAction(onBell) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                PrototypeBanner(
                    "Entrenamientos y reuniones se suman con la migración de entrenamientos. Los partidos ya son datos reales.",
                    Modifier.padding(16.dp),
                )
            }
            if (byDay.isEmpty()) item { EmptyState("Sin partidos próximos", "Cuando tu equipo programe un partido aparecerá aquí.") }
            byDay.forEach { (day, list) ->
                item { SectionHeader(day) }
                items(list.sortedBy { parseInstant(it.scheduledAt)?.toEpochMilli() ?: 0L }, key = { "c" + it.id }) { m ->
                    MatchCard(m, now, onClick = { onOpenMatch(m.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
            if (past.isNotEmpty()) {
                item { SectionHeader("Jugados recientemente") }
                past.forEach { (day, list) ->
                    item { SectionHeader(day) }
                    items(list, key = { "p" + it.id }) { m ->
                        MatchCard(m, now, onClick = { onOpenMatch(m.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}
