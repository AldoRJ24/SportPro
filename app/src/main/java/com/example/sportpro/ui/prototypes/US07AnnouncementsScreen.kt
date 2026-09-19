package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.InternalAnnouncement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US07AnnouncementsScreen(
    onBack: () -> Unit = {}
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var targetGroup by remember { mutableStateOf("Padres y Jugadores Sub-15") }
    var isUrgent by remember { mutableStateOf(false) }

    var announcementsHistory by remember {
        mutableStateOf(
            listOf(
                InternalAnnouncement(
                    "1", "⚠ Cambio de Horario - Entrenamiento Viernes", "Entrenador Carlos", "Sub-15",
                    "23 Feb 2026", "Atención: El entrenamiento de este viernes cambia de 17:00 a 18:30 hrs por disponibilidad de cancha principal.", isUrgent = true
                ),
                InternalAnnouncement(
                    "2", "Recordatorio: Traer Carnet de Identidad", "Administración", "Todas las categorías",
                    "20 Feb 2026", "Para el partido oficial del sábado es obligatorio llevar documento de identidad físico para el fichaje en cancha.", isUrgent = false
                )
            )
        )
    }

    var showSentSnackbar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-07: Anuncios e Historial") },
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
            // Formulario de Envío
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Enviar Comunicado Directo",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título del Aviso") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = targetGroup,
                        onValueChange = { targetGroup = it },
                        label = { Text("Grupo Destinatario") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Mensaje detallado / Cambio de horario") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isUrgent, onCheckedChange = { isUrgent = it })
                            Text("Aviso Urgente (Push Notif)")
                        }

                        Button(
                            onClick = {
                                if (title.isNotBlank() && message.isNotBlank()) {
                                    val newNotice = InternalAnnouncement(
                                        id = System.currentTimeMillis().toString(),
                                        title = title,
                                        sender = "DT / Administración",
                                        targetGroup = targetGroup,
                                        date = "Hoy",
                                        message = message,
                                        isUrgent = isUrgent
                                    )
                                    announcementsHistory = listOf(newNotice) + announcementsHistory
                                    title = ""
                                    message = ""
                                    showSentSnackbar = true
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enviar Alerta")
                        }
                    }
                }
            }

            if (showSentSnackbar) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2E7D32)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "📲 Alerta enviada directamente a los teléfonos registrados.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Text(
                "Historial de Anuncios Enviados y Recibidos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(announcementsHistory) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isUrgent) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (item.isUrgent) {
                                    Badge(containerColor = Color.Red) {
                                        Text("URGENTE", color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                            }

                            Text(
                                "De: ${item.sender} • Destino: ${item.targetGroup} • ${item.date}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text(item.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun US07AnnouncementsScreenPreview() {
    US07AnnouncementsScreen()
}
