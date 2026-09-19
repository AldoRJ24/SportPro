package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.PlayerStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US11PlayerStatsScreen(
    onBack: () -> Unit = {}
) {
    val myStats = remember {
        PlayerStats(
            playerName = "Mateo Gómez (Delantero)",
            matchesPlayed = 12,
            goals = 9,
            assists = 4,
            yellowCards = 2,
            redCards = 0,
            minutesPlayed = 940
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-11: Mis Estadísticas Acumuladas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ficha Privada del Jugador
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(myStats.playerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("🔒 Estadísticas Privadas - Solo visibles para ti", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }

            Text("Rendimiento Temporada 2026", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Grid de Tarjetas de Estadísticas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Goles",
                    value = "${myStats.goals}",
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Asistencias",
                    value = "${myStats.assists}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Partidos Jugados",
                    value = "${myStats.matchesPlayed}",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Minutos en Cero",
                    value = "${myStats.minutesPlayed}'",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Tarjetas Amarillas",
                    value = "${myStats.yellowCards}",
                    color = Color(0xFFFBC02D),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Tarjetas Rojas",
                    value = "${myStats.redCards}",
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.weight(1f)
                )
            }

            // Barra de promedio
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Promedio de Gol por Partido", fontWeight = FontWeight.Bold)
                    val avg = String.format("%.2f", myStats.goals.toDouble() / myStats.matchesPlayed)
                    Text("$avg goles / partido", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { (myStats.goals.toFloat() / myStats.matchesPlayed).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun US11PlayerStatsScreenPreview() {
    US11PlayerStatsScreen()
}
