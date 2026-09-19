package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US02AiSummaryScreen(
    onBack: () -> Unit = {}
) {
    var isGenerating by remember { mutableStateOf(false) }
    var summaryText by remember {
        mutableStateOf(
            "Resumen del Partido:\n\nSportPro Sub-15 logró una victoria electrizante por 3-2 sobre Rival FC. Mateo Gómez destacó con un doblete decisivo en los minutos 12 y 78. El equipo demostró solidez defensiva en el segundo tiempo tras la expulsión temporal. Gran rendimiento colectivo del plantel bajo la dirección técnica."
        )
    }
    var isEditing by remember { mutableStateOf(false) }
    var isApproved by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-02: Resumen con IA") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta de Resultado del Partido
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "PARTIDO FINALIZADO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "SportPro Sub-15  3 - 2  Rival FC",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Sábado 24 Febrero • Torneo Apertura",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Botón Generar con IA
            Button(
                onClick = {
                    isGenerating = true
                    // Simulación de respuesta de IA
                    summaryText = "Resumen Generado por Inteligencia Artificial SportPro:\n\n" +
                            "En un encuentro de alta intensidad, SportPro Sub-15 superó 3-2 a Rival FC. " +
                            "Destacó la efectividad táctica en balon parado y la presión alta. " +
                            "Mateo Gómez fue la figura del partido anotando 2 goles. " +
                            "El DT resaltó la disciplina y entrega del grupo durante los 80 minutos."
                    isGenerating = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isGenerating) "Generando Resumen..." else "Generar Resumen Automatizado (IA)")
            }

            if (isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Edición y Revisión del Entrenador
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isApproved) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Revisión del Entrenador",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Editar"
                            )
                        }
                    }

                    if (isEditing) {
                        OutlinedTextField(
                            value = summaryText,
                            onValueChange = { summaryText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            label = { Text("Editar texto del resumen antes de publicar") }
                        )
                    } else {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }

                    if (isApproved) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Aprobado por el Entrenador") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                        )
                    }
                }
            }

            // Acciones Finales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { isApproved = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aprobar")
                }

                Button(
                    onClick = {
                        isApproved = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isApproved
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compartir")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun US02AiSummaryScreenPreview() {
    US02AiSummaryScreen()
}
