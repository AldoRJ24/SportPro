package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.AvailabilityStatus
import com.example.sportpro.model.PlayerAvailability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US09LineupBuilderScreen(
    onBack: () -> Unit = {}
) {
    // Lista de jugadores con estado de disponibilidad de US-04
    var playersPool by remember {
        mutableStateOf(
            listOf(
                PlayerAvailability("1", "Mateo Gómez", "Delantero Centro", AvailabilityStatus.CONFIRMED),
                PlayerAvailability("2", "Lucas Silva", "Mediocampista Central", AvailabilityStatus.CONFIRMED),
                PlayerAvailability("3", "Carlos Pérez", "Defensa Central", AvailabilityStatus.CONFIRMED),
                PlayerAvailability("4", "Gabriel Torres", "Portero Titular", AvailabilityStatus.CONFIRMED),
                PlayerAvailability("5", "Joaquín Ríos", "Extremo Izquierdo", AvailabilityStatus.CONFIRMED),
                PlayerAvailability("6", "Nicolás Castro", "Lateral Derecho", AvailabilityStatus.DECLINED), // NO DISPONIBLE
                PlayerAvailability("7", "Esteban Marín", "Mediocampista", AvailabilityStatus.PENDING) // PENDIENTE
            )
        )
    }

    var startingStarters by remember { mutableStateOf(setOf("1", "2", "3", "4")) }
    var startingSubstitutes by remember { mutableStateOf(setOf("5")) }
    var selectedFormation by remember { mutableStateOf("4-3-3") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-09: Armado de Alineación") },
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
            // Tarjeta Informativa de Regla
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Regla de Convocatoria (US-09)", fontWeight = FontWeight.Bold)
                        Text(
                            "Solo puedes seleccionar como titulares o suplentes a jugadores que CONFIRMARON disponibilidad en la convocatoria (US-04).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Selector de Esquema Táctico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Esquema Táctico:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFormation == "4-3-3",
                        onClick = { selectedFormation = "4-3-3" },
                        label = { Text("4-3-3") }
                    )
                    FilterChip(
                        selected = selectedFormation == "4-4-2",
                        onClick = { selectedFormation = "4-4-2" },
                        label = { Text("4-4-2") }
                    )
                    FilterChip(
                        selected = selectedFormation == "3-5-2",
                        onClick = { selectedFormation = "3-5-2" },
                        label = { Text("3-5-2") }
                    )
                }
            }

            Text(
                "Nómina de Jugadores (Filtrado por Disponibilidad)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playersPool) { player ->
                    val isConfirmed = player.status == AvailabilityStatus.CONFIRMED
                    val isStarter = startingStarters.contains(player.playerId)
                    val isSub = startingSubstitutes.contains(player.playerId)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isStarter -> MaterialTheme.colorScheme.primaryContainer
                                isSub -> MaterialTheme.colorScheme.secondaryContainer
                                !isConfirmed -> Color.LightGray.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(player.playerName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    if (isStarter) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Star, contentDescription = "Titular", tint = Color(0xFFFBC02D))
                                    }
                                }
                                Text("${player.position} • Status: ${player.status.name}", style = MaterialTheme.typography.bodySmall)
                            }

                            if (!isConfirmed) {
                                Badge(containerColor = Color.Red) {
                                    Text("NO DISPONIBLE", color = Color.White, modifier = Modifier.padding(horizontal = 4.dp), fontSize = 10.sp)
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = isStarter,
                                        onClick = {
                                            if (isStarter) {
                                                startingStarters = startingStarters - player.playerId
                                            } else {
                                                startingStarters = startingStarters + player.playerId
                                                startingSubstitutes = startingSubstitutes - player.playerId
                                            }
                                        },
                                        label = { Text("Titular") }
                                    )

                                    FilterChip(
                                        selected = isSub,
                                        onClick = {
                                            if (isSub) {
                                                startingSubstitutes = startingSubstitutes - player.playerId
                                            } else {
                                                startingSubstitutes = startingSubstitutes + player.playerId
                                                startingStarters = startingStarters - player.playerId
                                            }
                                        },
                                        label = { Text("Suplente") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirmar y Publicar Alineación (${startingStarters.size} Titulares / ${startingSubstitutes.size} Suplentes)")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun US09LineupBuilderScreenPreview() {
    US09LineupBuilderScreen()
}
