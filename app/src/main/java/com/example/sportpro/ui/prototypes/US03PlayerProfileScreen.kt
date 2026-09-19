package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US03PlayerProfileScreen(
    onBack: () -> Unit = {}
) {
    var fullName by remember { mutableStateOf("Lucas Silva") }
    var birthDate by remember { mutableStateOf("2009-08-15") }
    var heightCm by remember { mutableStateOf("172") }
    var weightKg by remember { mutableStateOf("64.5") }
    var position by remember { mutableStateOf("Mediocampista") }
    var dominantFoot by remember { mutableStateOf("Derecho") }
    var isDataProtected by remember { mutableStateOf(true) }
    var parentName by remember { mutableStateOf("Carolina Silva") }
    var parentPhone by remember { mutableStateOf("+56 9 1234 5678") }
    var isSaved by remember { mutableStateOf(false) }

    // Cálculo simple de edad simulada (ej. 2026 - 2009 = 17 años)
    val age = 17
    val isMinor = age < 18

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-03: Perfil de Jugador") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Foto de Perfil y Avatar
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {}) {
                        Text("Cambiar Foto de Perfil")
                    }
                }
            }

            // Datos Personales
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Nombre Completo") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("Fecha Nacimiento (AAAA-MM-DD)") },
                    modifier = Modifier.weight(1f)
                )
                
                // Card Verificación de Edad
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMinor) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isMinor) "Menor ($age años)" else "Mayor (+18)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Datos Físicos y Deportivos
            Text(
                "Datos Físicos y Deportivos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = heightCm,
                    onValueChange = { heightCm = it },
                    label = { Text("Estatura (cm)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = weightKg,
                    onValueChange = { weightKg = it },
                    label = { Text("Peso (kg)") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    label = { Text("Posición Principal") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = dominantFoot,
                    onValueChange = { dominantFoot = it },
                    label = { Text("Pie Dominante") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Si es menor de edad, pedir apoderado
            if (isMinor) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Datos del Padre / Apoderado (Verificación requerida)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = parentName,
                            onValueChange = { parentName = it },
                            label = { Text("Nombre del Apoderado") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = parentPhone,
                            onValueChange = { parentPhone = it },
                            label = { Text("Teléfono de Contacto Apoderado") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Protección de datos
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Protección de Datos Privados", fontWeight = FontWeight.Bold)
                            Text(
                                "Mantiene ocultos teléfono, dirección y ficha médica para usuarios no autorizados",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Switch(
                        checked = isDataProtected,
                        onCheckedChange = { isDataProtected = it }
                    )
                }
            }

            Button(
                onClick = { isSaved = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Perfil de Jugador")
            }

            if (isSaved) {
                Text(
                    "✓ Perfil actualizado y verificado exitosamente.",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun US03PlayerProfileScreenPreview() {
    US03PlayerProfileScreen()
}
