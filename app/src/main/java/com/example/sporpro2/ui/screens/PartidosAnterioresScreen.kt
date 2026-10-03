package com.example.sporpro2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sporpro2.viewmodel.PartidosAnterioresState
import com.example.sporpro2.viewmodel.PartidosAnterioresViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartidosAnterioresScreen(
    viewModel: PartidosAnterioresViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onMatchClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color.White, // Fondo blanco moderno
        topBar = {
            TopAppBar(
                title = { Text("Partidos Anteriores", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1976D2)), // Azul institucional
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {
                is PartidosAnterioresState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PartidosAnterioresState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.partidos) { partido ->
                            ElevatedCard(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF8F9FA)), // Gris muy claro
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMatchClick(partido.id) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Header: Competición y Fecha
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = partido.competicion, style = MaterialTheme.typography.labelLarge, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                                        Text(text = partido.fecha, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Marcador Central
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        // Equipo Local
                                        Text(
                                            text = partido.equipoLocal,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.Black,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        // Score Box
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "${partido.marcadorLocal} - ${partido.marcadorVisitante}",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                        
                                        // Equipo Visitante
                                        Text(
                                            text = partido.equipoVisitante,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.Black,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Etiqueta de "Finalizado"
                                    Surface(
                                        color = Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = partido.estado,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2E7D32), // Verde oscuro
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is PartidosAnterioresState.Error -> {
                    Text(text = state.message, color = Color.Red, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
