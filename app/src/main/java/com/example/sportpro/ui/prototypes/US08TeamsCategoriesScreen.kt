package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.TeamCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US08TeamsCategoriesScreen(
    onBack: () -> Unit = {}
) {
    var teamName by remember { mutableStateOf("") }
    var ageRange by remember { mutableStateOf("") }
    var coachName by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    var teamsList by remember {
        mutableStateOf(
            listOf(
                TeamCategory("1", "SportPro Sub-12", "Edad 10 - 11 años", "DT Roberto Gómez", playerCapacity = 20, registeredPlayers = 18),
                TeamCategory("2", "SportPro Sub-15", "Edad 13 - 14 años", "DT Carlos Pérez", playerCapacity = 22, registeredPlayers = 20),
                TeamCategory("3", "SportPro Sub-18", "Edad 16 - 17 años", "DT Andrés Silva", playerCapacity = 22, registeredPlayers = 15)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-08: Equipos y Categorías") },
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
                text = { Text("Nuevo Equipo") }
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Panel Administrativo de Equipos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Crea grupos organizados por rango de edad y asigna entrenadores responsables para cada categoría.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                "Categorías Registradas (${teamsList.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(teamsList) { team ->
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(team.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                AssistChip(
                                    onClick = {},
                                    label = { Text(team.ageRange) }
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Entrenador Asignado: ", fontWeight = FontWeight.SemiBold)
                                Text(team.coachName)
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Inscritos: ${team.registeredPlayers} / ${team.playerCapacity} jugadores",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("Estado: Activo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
            title = { Text("Registrar Nueva Categoría / Equipo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = teamName,
                        onValueChange = { teamName = it },
                        label = { Text("Nombre del Equipo (Ej. Sub-14)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ageRange,
                        onValueChange = { ageRange = it },
                        label = { Text("Rango de Edad (Ej. 12-13 años)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = coachName,
                        onValueChange = { coachName = it },
                        label = { Text("Entrenador Asignado") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (teamName.isNotBlank() && coachName.isNotBlank()) {
                        val newTeam = TeamCategory(
                            id = System.currentTimeMillis().toString(),
                            name = teamName,
                            ageRange = if (ageRange.isNotBlank()) ageRange else "Sin rango específico",
                            coachName = coachName,
                            playerCapacity = 20,
                            registeredPlayers = 0
                        )
                        teamsList = teamsList + newTeam
                        teamName = ""
                        ageRange = ""
                        coachName = ""
                        showAddDialog = false
                    }
                }) {
                    Text("Crear Equipo")
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
fun US08TeamsCategoriesScreenPreview() {
    US08TeamsCategoriesScreen()
}
