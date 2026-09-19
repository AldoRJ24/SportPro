package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PrototypeItem(
    val code: String,
    val title: String,
    val description: String,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeMenuScreen(
    onSelectPrototype: (String) -> Unit = {}
) {
    val prototypeList = rememberPrototypeItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SportPro - Entrega Semana 5 (Prototipos)") },
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
            // Header del proyecto
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
                    Icon(
                        Icons.Default.SportsSoccer,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Prototipos de Historias de Usuario",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Selecciona cualquiera de los 12 prototipos para explorar la UI interactiva.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(prototypeList) { item ->
                    Card(
                        onClick = { onSelectPrototype(item.code) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            item.code,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Ver Prototipo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberPrototypeItems(): List<PrototypeItem> {
    return listOf(
        PrototypeItem("US-01", "Registro dinámico de partido en vivo", "Marcador en tiempo real y registro inmediato de goles, tarjetas y faltas.", "Partidos"),
        PrototypeItem("US-02", "Generación de resumen con IA", "Redacción automatizada del resumen del partido con edición previa del entrenador.", "IA & Reportes"),
        PrototypeItem("US-03", "Creación de perfil de jugador", "Formulario con foto, datos físicos, verificación de edad y protección privada.", "Perfil"),
        PrototypeItem("US-04", "Convocatoria y disponibilidad", "Envío de invitaciones a partidos y respuesta de asistencia por apoderado.", "Convocatorias"),
        PrototypeItem("US-05", "Planificación y biblioteca", "Biblioteca de ejercicios favoritos y control de asistencia en campo.", "Entrenamiento"),
        PrototypeItem("US-06", "Muro y moderación comunitaria", "Publicaciones, comentarios y moderación automatizada al acumular 3 reportes.", "Comunidad"),
        PrototypeItem("US-07", "Anuncios e historial de avisos", "Envío de alertas urgentes, cambios de horario y consulta de historial.", "Comunicación"),
        PrototypeItem("US-08", "Registro de equipos y categorías", "Creación de categorías por rango de edad y asignación de directores técnicos.", "Administración"),
        PrototypeItem("US-09", "Armado de la alineación titular", "Selección táctica de titulares filtrando solo a quienes confirmaron asistencia.", "Partidos"),
        PrototypeItem("US-10", "Registro simulado de mensualidades", "Control manual de cuotas sin transacciones de dinero real.", "Finanzas"),
        PrototypeItem("US-11", "Estadísticas acumuladas individuales", "Conteo automático privado de goles, asistencias y tarjetas por jugador.", "Estadísticas"),
        PrototypeItem("US-12", "Avisos de pruebas y talento externo", "Cartelera pública con requerimientos por posición y formulario de inscripción.", "Captación")
    )
}

@Preview(showBackground = true)
@Composable
fun PrototypeMenuScreenPreview() {
    PrototypeMenuScreen()
}
