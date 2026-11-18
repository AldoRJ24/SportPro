package com.sportpro.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportpro.app.ui.theme.Coral300
import com.sportpro.app.ui.theme.Crimson400
import com.sportpro.app.ui.theme.Crimson800
import com.sportpro.app.ui.theme.Crimson950

/** Encabezado del inicio: saludo y resumen rápido de partidos en vivo, próximos y resultados. */
@Composable
fun HomeHero(
    name: String?,
    liveCount: Int,
    upcomingCount: Int,
    finishedCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Crimson950, Crimson800, Crimson400, Coral300)))
            .padding(20.dp),
    ) {
        Text(
            "¡A JUGAR!",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
        )
        Text(
            if (name.isNullOrBlank()) "Hola" else "Hola, $name",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat("EN VIVO", liveCount, Modifier.weight(1f))
            HeroStat("PRÓXIMOS", upcomingCount, Modifier.weight(1f))
            HeroStat("RESULTADOS", finishedCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}
