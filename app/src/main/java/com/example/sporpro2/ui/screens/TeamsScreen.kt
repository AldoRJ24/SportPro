package com.example.sporpro2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sporpro2.viewmodel.TeamsState
import com.example.sporpro2.viewmodel.TeamsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(
    viewModel: TeamsViewModel = viewModel(),
    onAddTeamClick: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color(0xFFF0F0F0), // Fondo claro
        topBar = {
            TopAppBar(
                title = { Text("Equipos (tabla equipos)", color = Color.Black) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.Black)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTeamClick, containerColor = Color(0xFF1976D2)) {
                Icon(Icons.Default.Add, contentDescription = "Crear equipo", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is TeamsState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
                is TeamsState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.teams) { team ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "nombre: ${team.nombre}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black // Texto oscuro
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "categoria: ${team.categoria}", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "dt_asignado_id: ${team.dtAsignadoId}", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "estado: ${team.estado}", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
                is TeamsState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                }
            }
        }
    }
}
