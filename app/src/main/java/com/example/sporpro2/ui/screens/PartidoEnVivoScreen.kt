package com.example.sporpro2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sporpro2.viewmodel.EventoPartidoUI
import com.example.sporpro2.viewmodel.PartidoEnVivoViewModel
import com.example.sporpro2.viewmodel.PartidoVivoState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartidoEnVivoScreen(
    onBackClick: () -> Unit,
    viewModel: PartidoEnVivoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val showDialog by viewModel.showEventDialog.collectAsState()
    val tipoEvento by viewModel.selectedEventType.collectAsState()
    val equipos by viewModel.equiposDelPartido.collectAsState()
    val jugadores by viewModel.jugadoresDelEquipoSeleccionado.collectAsState()
    val selectedTeamId by viewModel.selectedTeamId.collectAsState()
    val selectedPlayerId by viewModel.selectedPlayerId.collectAsState()

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cerrarDialogo() },
            title = { Text("Registrar $tipoEvento") },
            text = {
                Column {
                    Text("Selecciona el Equipo:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        equipos.forEach { equipo ->
                            FilterChip(
                                selected = selectedTeamId == equipo.id,
                                onClick = { viewModel.onTeamSelected(equipo.id) },
                                label = { Text(equipo.nombre ?: "Equipo") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedTeamId.isNotBlank()) {
                        Text("Selecciona el Jugador:")
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(jugadores) { jugador ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onPlayerSelected(jugador.id) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedPlayerId == jugador.id,
                                        onClick = { viewModel.onPlayerSelected(jugador.id) }
                                    )
                                    Text(text = jugador.nombreCompleto ?: "Sin nombre")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.registrarEventoConfirmado() },
                    enabled = selectedTeamId.isNotBlank() && selectedPlayerId.isNotBlank()
                ) {
                    Text("Confirmar Evento")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cerrarDialogo() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consola DT: Partido en Vivo") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Controles Rápidos del DT
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Registrar Evento (Realtime)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.abrirDialogo("GOL") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("⚽ Gol")
                        }
                        
                        Button(
                            onClick = { viewModel.abrirDialogo("AMARILLA") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107), contentColor = Color.Black)
                        ) {
                            Text("🟨 Amarilla")
                        }
                        
                        Button(
                            onClick = { viewModel.abrirDialogo("ROJA") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) {
                            Text("🟥 Roja")
                        }
                    }
                }
            }

            HorizontalDivider()

            // Línea de tiempo
            when (val state = uiState) {
                is PartidoVivoState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is PartidoVivoState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Error:", color = MaterialTheme.colorScheme.error)
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                is PartidoVivoState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val local by viewModel.nombreLocal.collectAsState()
                        val visita by viewModel.nombreVisita.collectAsState()
                        
                        Text(
                            text = "$local vs $visita", 
                            style = MaterialTheme.typography.headlineSmall, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (state.eventos.isEmpty()) {
                            Text("Sin eventos registrados aún.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.eventos) { evento ->
                                    EventoLineaTiempo(evento)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventoLineaTiempo(evento: EventoPartidoUI) {
    val iconoStr = when (evento.tipoEvento.uppercase()) {
        "GOL" -> "⚽"
        "AMARILLA" -> "🟨"
        "ROJA" -> "🟥"
        else -> "⏱️"
    }

    var horaFormat = ""
    try {
        val formatIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        formatIn.timeZone = TimeZone.getTimeZone("UTC")
        val formatOut = SimpleDateFormat("HH:mm", Locale.getDefault())
        val cleanDateStr = if (evento.createdAt.length >= 19) evento.createdAt.substring(0, 19) else evento.createdAt
        val date = formatIn.parse(cleanDateStr)
        if (date != null) {
            horaFormat = formatOut.format(date)
        }
    } catch (e: Exception) {
        horaFormat = "00:00"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = horaFormat, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = iconoStr, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = evento.tipoEvento, fontWeight = FontWeight.Bold)
                Text(text = "${evento.nombreJugador} (${evento.nombreEquipo})", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
