package com.sportpro.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.Position

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

// Los chips de formación y el contador por línea se reemplazaron por el editor de la cancha
// (ui/lineup/LineupEditorScreen.kt, migración 018).
