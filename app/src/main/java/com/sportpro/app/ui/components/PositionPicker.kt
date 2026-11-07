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
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.WarnAmber

fun positionColor(p: Position?): Color = when (p) {
    Position.POR -> Color(0xFFFFB020)
    Position.DEF -> Color(0xFF4FC3F7)
    Position.MED -> Color(0xFF3DDC97)
    Position.DEL -> Color(0xFFFF7A59)
    null -> Color(0xFF8AA19C)
}

/** Small colored badge: ARQ / DEF / MED / DEL. */
@Composable
fun PositionBadge(p: Position?, modifier: Modifier = Modifier) {
    Pill(p?.short ?: "—", positionColor(p), modifier)
}

/** Four chips to choose a position. */
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

/** «ARQ 1/1 · DEF 2/2 · MED 2/3 · DEL 1/1» counter for a formation. */
@Composable
fun FormationCounter(formation: Formation, positions: List<Position?>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Position.entries.forEach { p ->
            val have = positions.count { it == p }
            val need = formation.required(p)
            Pill("${p.short} $have/$need", if (have == need) Mint400 else WarnAmber)
        }
    }
}

@Composable
fun FormationChips(options: List<Formation>, selected: Formation?, onSelect: (Formation) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { f ->
            FilterChip(selected = selected == f, onClick = { onSelect(f) }, label = { Text(f.name) })
        }
    }
}
