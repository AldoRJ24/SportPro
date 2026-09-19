package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.TryoutNotice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US12TryoutsScreen(
    onBack: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTryoutForModal by remember { mutableStateOf<TryoutNotice?>(null) }
    var isRegistered by remember { mutableStateOf(false) }

    val tryoutsList = remember {
        listOf(
            TryoutNotice(
                "1", "Prueba Abierta de Arqueros y Defensas", "Categoría Sub-15 (2011-2012)",
                "Portero / Defensa Central", "Sábado 07 de Marzo 2026", "10:00 AM",
                "Complejo Deportivo Municipal, Cancha 1", "Asistir con ropa deportiva blanca, canilleras y botella de agua personal."
            ),
            TryoutNotice(
                "2", "Captación de Delanteros y Extremos", "Categoría Sub-17 (2009-2010)",
                "Delantero Centro / Extremo Izquierdo", "Domingo 08 de Marzo 2026", "11:30 AM",
                "Estadio Central SportPro", "Certificado médico de aptitud física no mayor a 3 meses."
            ),
            TryoutNotice(
                "3", "Prueba General de Mediocampistas", "Categoría Sub-13 (2013-2014)",
                "Mediocampista de Contención / Volante", "Sábado 14 de Marzo 2026", "09:00 AM",
                "Cancha Sintética de la Academia", "Estar acompañado de padre o apoderado legal."
            )
        )
    }

    val filteredList = tryoutsList.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.positionRequired.contains(searchQuery, ignoreCase = true) ||
                it.categoryAge.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-12: Pruebas y Talento Externo") },
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
            // Cartelera Informativa
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Convocatorias Abiertas 2026", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Búsqueda de nuevos talentos deportivos para las divisiones inferiores.", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                }
            }

            // Buscador
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar por posición o categoría (ej. Portero, Sub-15)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Pruebas Disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredList) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(item.categoryAge) }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Posición: ${item.positionRequired}") },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${item.location} • ${item.date} (${item.time})", style = MaterialTheme.typography.bodySmall)
                            }

                            Text("Requisitos: ${item.requirements}", style = MaterialTheme.typography.bodyMedium)

                            Button(
                                onClick = {
                                    selectedTryoutForModal = item
                                    isRegistered = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Inscribirme a esta Prueba")
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTryoutForModal?.let { notice ->
        AlertDialog(
            onDismissRequest = { selectedTryoutForModal = null },
            title = { Text("Postulación a Prueba Abierta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(notice.title, fontWeight = FontWeight.Bold)
                    Text("Categoría: ${notice.categoryAge}")
                    Text("Lugar: ${notice.location}")
                    Text("Fecha y Hora: ${notice.date} - ${notice.time}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    if (isRegistered) {
                        Text(
                            "✓ ¡Inscripción confirmada exitosamente! Se ha enviado tu pase de prueba al correo electrónico.",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text("Confirma tus datos para generar tu ficha de inscripción gratuita.")
                    }
                }
            },
            confirmButton = {
                if (!isRegistered) {
                    Button(onClick = { isRegistered = true }) {
                        Text("Confirmar Inscripción")
                    }
                } else {
                    Button(onClick = { selectedTryoutForModal = null }) {
                        Text("Cerrar")
                    }
                }
            },
            dismissButton = {
                if (!isRegistered) {
                    TextButton(onClick = { selectedTryoutForModal = null }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun US12TryoutsScreenPreview() {
    US12TryoutsScreen()
}
