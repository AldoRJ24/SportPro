package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.EventType
import com.example.sportpro.model.LiveMatchState
import com.example.sportpro.model.MatchEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US01LiveMatchScreen(
    onBack: () -> Unit = {}
) {
    var matchState by remember { mutableStateOf(LiveMatchState()) }
    var selectedPlayer by remember { mutableStateOf("Mateo Gómez") }
    var eventTypeSelected by remember { mutableStateOf(EventType.GOAL) }
    var eventMinute by remember { mutableStateOf("72") }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-01: Marcador en Vivo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Anotar Evento") }
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
            // Marcador Principal
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "• EN VIVO (${matchState.period} - ${matchState.minute}')",
                        color = Color.Green,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                matchState.homeTeam,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "${matchState.homeScore}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 48.sp
                            )
                        }
                        Text(
                            "VS",
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                matchState.awayTeam,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "${matchState.awayScore}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 48.sp
                            )
                        }
                    }
                }
            }

            Text(
                text = "Cronología de Eventos en Tiempo Real",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matchState.events.reversed()) { event ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Badge(
                                containerColor = when (event.type) {
                                    EventType.GOAL -> Color(0xFF2E7D32)
                                    EventType.YELLOW_CARD -> Color(0xFFFBC02D)
                                    EventType.RED_CARD -> Color(0xFFD32F2F)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            ) {
                                Text(
                                    "${event.minute}'",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${event.type.name} - ${event.player}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = event.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Anotar Evento de Partido") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = selectedPlayer,
                        onValueChange = { selectedPlayer = it },
                        label = { Text("Jugador") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = eventMinute,
                        onValueChange = { eventMinute = it },
                        label = { Text("Minuto") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Tipo de Evento:")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = eventTypeSelected == EventType.GOAL,
                            onClick = { eventTypeSelected = EventType.GOAL },
                            label = { Text("Gol") }
                        )
                        FilterChip(
                            selected = eventTypeSelected == EventType.YELLOW_CARD,
                            onClick = { eventTypeSelected = EventType.YELLOW_CARD },
                            label = { Text("Tarjeta A.") }
                        )
                        FilterChip(
                            selected = eventTypeSelected == EventType.RED_CARD,
                            onClick = { eventTypeSelected = EventType.RED_CARD },
                            label = { Text("Tarjeta R.") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val min = eventMinute.toIntOrNull() ?: matchState.minute
                    val isGoal = eventTypeSelected == EventType.GOAL
                    val newEvent = MatchEvent(
                        id = System.currentTimeMillis().toString(),
                        minute = min,
                        type = eventTypeSelected,
                        player = selectedPlayer,
                        description = if (isGoal) "Anotación de gol en vivo" else "Amonestación registrada por árbitro"
                    )
                    matchState = matchState.copy(
                        homeScore = if (isGoal) matchState.homeScore + 1 else matchState.homeScore,
                        minute = min,
                        events = matchState.events + newEvent
                    )
                    showAddDialog = false
                }) {
                    Text("Guardar Evento")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun US01LiveMatchScreenPreview() {
    US01LiveMatchScreen()
}
