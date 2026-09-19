package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.AvailabilityStatus
import com.example.sportpro.model.PlayerAvailability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US04CallUpScreen(
    onBack: () -> Unit = {}
) {
    var callUpList by remember {
        mutableStateOf(
            listOf(
                PlayerAvailability("1", "Mateo Gómez", "Delantero", AvailabilityStatus.CONFIRMED),
                PlayerAvailability("2", "Lucas Silva", "Mediocampista", AvailabilityStatus.CONFIRMED),
                PlayerAvailability("3", "Carlos Pérez", "Defensa Central", AvailabilityStatus.CONFIRMED),
                PlayerAvailability("4", "Gabriel Torres", "Portero", AvailabilityStatus.PENDING),
                PlayerAvailability("5", "Joaquín Ríos", "Extremo Izquierdo", AvailabilityStatus.PENDING),
                PlayerAvailability("6", "Nicolás Castro", "Lateral Derecho", AvailabilityStatus.DECLINED)
            )
        )
    }

    var myStatus by remember { mutableStateOf(AvailabilityStatus.CONFIRMED) }
    var notificationSent by remember { mutableStateOf(false) }

    val confirmedCount = callUpList.count { it.status == AvailabilityStatus.CONFIRMED }
    val pendingCount = callUpList.count { it.status == AvailabilityStatus.PENDING }
    val declinedCount = callUpList.count { it.status == AvailabilityStatus.DECLINED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-04: Convocatoria y Disponibilidad") },
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
            // Ficha del Partido Convocado
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PRÓXIMO PARTIDO", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("SportPro Sub-15 vs. Club Atlético", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("📅 Sábado 2 Febrero - 10:00 AM", color = Color.White)
                    Text("📍 Estadio Municipal de Deportes, Campo 2", color = Color.White)
                }
            }

            // Vista del Jugador/Padre (Mi Disponibilidad)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Respuesta de Asistencia (Jugador / Apoderado)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("Favor confirmar tu presencia antes del Viernes 18:00 hrs.")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { myStatus = AvailabilityStatus.CONFIRMED },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (myStatus == AvailabilityStatus.CONFIRMED) Color(0xFF2E7D32) else Color.Gray
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Asistiré")
                        }

                        Button(
                            onClick = { myStatus = AvailabilityStatus.DECLINED },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (myStatus == AvailabilityStatus.DECLINED) Color(0xFFD32F2F) else Color.Gray
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("No iré")
                        }
                    }
                }
            }

            // Panel del Entrenador: Envío de Notificaciones y Resumen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Resumen Convocatoria ($confirmedCount Conf. / $pendingCount Pend. / $declinedCount Aus.)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = { notificationSent = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reenviar Alertas", fontSize = 12.sp)
                }
            }

            if (notificationSent) {
                Text(
                    "🔔 Notificaciones push enviadas a los apoderados con respuesta pendiente.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Lista de convocados
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(callUpList) { player ->
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(player.playerName, fontWeight = FontWeight.Bold)
                                Text(player.position, style = MaterialTheme.typography.bodySmall)
                            }

                            Badge(
                                containerColor = when (player.status) {
                                    AvailabilityStatus.CONFIRMED -> Color(0xFF2E7D32)
                                    AvailabilityStatus.PENDING -> Color(0xFFFBC02D)
                                    AvailabilityStatus.DECLINED -> Color(0xFFD32F2F)
                                }
                            ) {
                                Text(
                                    when (player.status) {
                                        AvailabilityStatus.CONFIRMED -> "CONFIRMADO"
                                        AvailabilityStatus.PENDING -> "PENDIENTE"
                                        AvailabilityStatus.DECLINED -> "DECLINADO"
                                    },
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun US04CallUpScreenPreview() {
    US04CallUpScreen()
}
