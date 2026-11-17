package com.sportpro.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.Formation
import com.sportpro.app.core.data.Position
import com.sportpro.app.ui.theme.SuccessGreen
import com.sportpro.app.ui.theme.WarnAmber

fun positionColor(p: Position?): Color = when (p) {
    Position.POR -> Color(0xFFB45309)
    Position.DEF -> Color(0xFF0369A1)
    Position.MED -> Color(0xFF15803D)
    Position.DEL -> Color(0xFFC2410C)
    null -> Color(0xFF64748B)
}

/** Small colored badge: ARQ / DEF / MED / DEL. */
@Composable
fun PositionBadge(p: Position?, modifier: Modifier = Modifier) {
    Pill(p?.short ?: "—", positionColor(p), modifier)
}

/** Cuatro chips para elegir una posición. */
@Composable
fun PositionChips(selected: Position?, onSelect: (Position) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Position.entries.forEach { p ->
            FilterChip(
                selected = selected == p,
                onClick = { onSelect(p) },
                enabled = enabled,
                label = { Text(p.short, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/** «ARQ 1/1 · DEF 2/2 · MED 2/3 · DEL 1/1» : contador de cuántos jugadores hay por posición frente a lo que pide la formación. */
@Composable
fun FormationCounter(formation: Formation, positions: List<Position?>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Position.entries.forEach { p ->
            val have = positions.count { it == p }
            val need = formation.required(p)
            Pill("${p.short} $have/$need", if (have == need) SuccessGreen else WarnAmber)
        }
    }
}

/** Chips para elegir la formación entre las disponibles para el formato del partido (por ejemplo 1-2-3-1). */
@Composable
fun FormationChips(options: List<Formation>, selected: Formation?, onSelect: (Formation) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { f ->
            FilterChip(selected = selected == f, onClick = { onSelect(f) }, label = { Text(f.name) })
        }
    }
}
