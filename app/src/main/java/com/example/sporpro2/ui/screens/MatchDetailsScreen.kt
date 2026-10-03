package com.example.sporpro2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sporpro2.viewmodel.GoalScorer
import com.example.sporpro2.viewmodel.MatchDetailsViewModel
import com.example.sporpro2.viewmodel.MatchEventType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailsScreen(
    matchId: String = "",
    viewModel: MatchDetailsViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Detalles", "Estadísticas")

    LaunchedEffect(matchId) {
        if (matchId.isNotEmpty()) {
            viewModel.loadMatchDetails(matchId)
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("Detalles del Partido", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1976D2)),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. TARJETA OSCURA EN LA PARTE SUPERIOR (Marcador + Autores de Goles)
            ScoreHeaderCard(
                localTeam = uiState.localTeam,
                visitorTeam = uiState.visitorTeam,
                localScore = uiState.localScore,
                visitorScore = uiState.visitorScore,
                localScorers = uiState.localScorers,
                visitorScorers = uiState.visitorScorers
            )

            // 2. MENÚ DE PESTAÑAS (TabRow)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = Color(0xFF1976D2)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) Color(0xFF1976D2) else Color.Gray,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. CONTENIDO SEGÚN LA PESTAÑA SELECCIONADA
            if (selectedTabIndex == 0) {
                // PESTAÑA 'DETALLES': LÍNEA DE TIEMPO VERTICAL
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.events) { event ->
                        TimelineEventItem(
                            minute = event.minute,
                            player = event.player,
                            description = event.description,
                            type = event.type
                        )
                    }
                }
            } else {
                // PESTAÑA 'ESTADÍSTICAS': FILAS COMPARATIVAS
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                ComparativeStatRow("Tiros al arco", uiState.stats.shotsOnTargetLocal.toString(), uiState.stats.shotsOnTargetVisitor.toString())
                                HorizontalDivider(color = Color(0xFFE0E0E0))
                                ComparativeStatRow("Posesión", uiState.stats.possessionLocal, uiState.stats.possessionVisitor)
                                HorizontalDivider(color = Color(0xFFE0E0E0))
                                ComparativeStatRow("Faltas", uiState.stats.foulsLocal.toString(), uiState.stats.foulsVisitor.toString())
                                HorizontalDivider(color = Color(0xFFE0E0E0))
                                ComparativeStatRow("Tarjetas Amarillas", uiState.stats.yellowCardsLocal.toString(), uiState.stats.yellowCardsVisitor.toString())
                                HorizontalDivider(color = Color(0xFFE0E0E0))
                                ComparativeStatRow("Tarjetas Rojas", uiState.stats.redCardsLocal.toString(), uiState.stats.redCardsVisitor.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreHeaderCard(
    localTeam: String,
    visitorTeam: String,
    localScore: Int,
    visitorScore: Int,
    localScorers: List<GoalScorer>,
    visitorScorers: List<GoalScorer>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // Tarjeta oscura estilo marcador
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Marcador Principal
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = localTeam,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "$localScore - $visitorScore",
                    color = Color(0xFF4CAF50), // Verde brillante para el marcador
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Text(
                    text = visitorTeam,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF334155))
            Spacer(modifier = Modifier.height(12.dp))

            // Autores de los Goles con minutos exactos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Autores Local (Izquierda)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    localScorers.forEach { scorer ->
                        Text(
                            text = "⚽ ${scorer.player} ${scorer.minute}'",
                            color = Color(0xFFE2E8F0),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Autores Visitante (Derecha)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    visitorScorers.forEach { scorer ->
                        Text(
                            text = "⚽ ${scorer.player} ${scorer.minute}'",
                            color = Color(0xFFE2E8F0),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineEventItem(minute: Int, player: String, description: String, type: MatchEventType) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minuto al lado izquierdo
        Text(
            text = "$minute'",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1976D2),
            modifier = Modifier.width(48.dp)
        )

        // Ícono del Evento
        val iconData = getEventIconAndColor(type)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconData.second.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (type == MatchEventType.GOAL) {
                Text("⚽", style = MaterialTheme.typography.titleSmall)
            } else {
                Icon(
                    imageVector = iconData.first,
                    contentDescription = null,
                    tint = iconData.second,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Nombre del jugador y descripción del evento al otro lado
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = player,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
        }
    }
}

@Composable
fun ComparativeStatRow(label: String, localValue: String, visitorValue: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Valor Local (Izquierda)
        Text(
            text = localValue,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1976D2), // Azul
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Start
        )

        // Nombre de la Estadística (Centro)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        // Valor Visitante (Derecha)
        Text(
            text = visitorValue,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFD32F2F), // Rojo
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.End
        )
    }
}

fun getEventIconAndColor(type: MatchEventType): Pair<ImageVector, Color> {
    return when (type) {
        MatchEventType.GOAL -> Pair(Icons.Default.Warning, Color.Black)
        MatchEventType.YELLOW_CARD -> Pair(Icons.Default.Warning, Color(0xFFFBC02D))
        MatchEventType.RED_CARD -> Pair(Icons.Default.Warning, Color(0xFFD32F2F))
        MatchEventType.SUBSTITUTION -> Pair(Icons.Default.Sync, Color(0xFF1976D2))
        MatchEventType.SHOT_ON_TARGET -> Pair(Icons.Default.Warning, Color.DarkGray)
        MatchEventType.CORNER_KICK -> Pair(Icons.Default.Warning, Color(0xFF388E3C))
        MatchEventType.UNKNOWN -> Pair(Icons.Default.Warning, Color.Gray)
        else -> Pair(Icons.Default.Warning, Color.Gray)
    }
}
