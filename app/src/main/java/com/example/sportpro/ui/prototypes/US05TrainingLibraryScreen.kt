package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.AttendanceRecord
import com.example.sportpro.model.Exercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US05TrainingLibraryScreen(
    onBack: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Ejercicios, 1: Asistencia Cancha

    var exercises by remember {
        mutableStateOf(
            listOf(
                Exercise("1", "Rondó 4v2 con transición rápida", "Posesión", 20, "Mantener la posesión en espacio reducido con 2 toques máximo.", isFavorite = true),
                Exercise("2", "Tiro a puerta tras pared táctica", "Finalización", 25, "Coordinación entre mediocampista y delantero para remate de primera.", isFavorite = true),
                Exercise("3", "Presión alta tras pérdida de balón", "Táctica Defensiva", 15, "Reacción inmediata de los atacantes al perder el balón.", isFavorite = false)
            )
        )
    }

    var attendanceList by remember {
        mutableStateOf(
            listOf(
                AttendanceRecord("1", "Mateo Gómez", isPresent = true),
                AttendanceRecord("2", "Lucas Silva", isPresent = true),
                AttendanceRecord("3", "Carlos Pérez", isPresent = false, note = "Licencia Médica"),
                AttendanceRecord("4", "Gabriel Torres", isPresent = true),
                AttendanceRecord("5", "Joaquín Ríos", isPresent = true)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-05: Entrenamientos y Asistencia") },
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
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Biblioteca Ejercicios") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Asistencia en Campo") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // Vista de Ejercicios
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Biblioteca de Prácticas Favoritas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Add, contentDescription = "Nuevo Ejercicio")
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(exercises) { exercise ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(exercise.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        IconButton(onClick = {
                                            exercises = exercises.map {
                                                if (it.id == exercise.id) it.copy(isFavorite = !it.isFavorite) else it
                                            }
                                        }) {
                                            Icon(
                                                if (exercise.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Favorito",
                                                tint = if (exercise.isFavorite) Color.Red else Color.Gray
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("${exercise.durationMinutes} min") }
                                        )
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(exercise.category) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(exercise.description, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            } else {
                // Vista de Asistencia en Cancha
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Lista de Asistencia - Entrenamiento Sub-15",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Marca los presentes durante el entrenamiento en terreno:")

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(attendanceList) { record ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (record.isPresent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(record.playerName, fontWeight = FontWeight.Bold)
                                        if (record.note.isNotEmpty()) {
                                            Text("Nota: ${record.note}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (record.isPresent) "Presente" else "Ausente")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Checkbox(
                                            checked = record.isPresent,
                                            onCheckedChange = { checked ->
                                                attendanceList = attendanceList.map {
                                                    if (it.playerId == record.playerId) it.copy(isPresent = checked) else it
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Asistencia del Día")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun US05TrainingLibraryScreenPreview() {
    US05TrainingLibraryScreen()
}
