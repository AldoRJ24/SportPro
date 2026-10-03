package com.example.sporpro2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.sporpro2.viewmodel.AuthViewModel
import com.example.sporpro2.viewmodel.JugadorDashboardViewModel

@Composable
fun JugadorDashboardScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: JugadorDashboardViewModel = viewModel(),
    onNavigateTo: (String) -> Unit
) {
    val uiState by dashboardViewModel.uiState.collectAsState()

    // Cargar/actualizar los datos reales de Supabase automáticamente al entrar a la pantalla
    androidx.compose.runtime.LaunchedEffect(Unit) {
        dashboardViewModel.loadJugadorProfile()
    }

    Scaffold(
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // 1. Foto de perfil circular
            Surface(
                shape = CircleShape,
                color = Color(0xFFE3F2FD), // Azul muy claro por defecto
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (uiState.fotoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = uiState.fotoUrl,
                            contentDescription = "Foto de perfil real",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Foto de perfil por defecto",
                            modifier = Modifier.size(60.dp),
                            tint = Color(0xFF1976D2)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 2. Nombre y Posición Traídos desde Supabase
            Text(
                text = uiState.nombre,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black
            )
            Text(
                text = uiState.posicionPrincipal,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            ModernMenuButton(title = "Mi Perfil", icon = Icons.Default.Person) { onNavigateTo("perfil_jugador") }
            Spacer(modifier = Modifier.height(16.dp))
            ModernMenuButton(title = "Mis Estadísticas", icon = Icons.Default.BarChart) { onNavigateTo("estadisticas") }
            Spacer(modifier = Modifier.height(16.dp))
            ModernMenuButton(title = "Comunidad", icon = Icons.Default.Forum) { onNavigateTo("muro") }
            Spacer(modifier = Modifier.height(16.dp))
            ModernMenuButton(title = "Convocatorias", icon = Icons.Default.Campaign) { onNavigateTo("convocatorias") }
            Spacer(modifier = Modifier.height(16.dp))
            ModernMenuButton(title = "Partidos Anteriores", icon = Icons.Default.Star) { onNavigateTo("partidos_anteriores") }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { authViewModel.logout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar Sesión", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar Sesión", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ModernMenuButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = Color(0xFFF0F4F8), // Fondo gris/azulado súper claro
            contentColor = Color.Black // Texto negro de alto contraste
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().height(72.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF1976D2), // Iconos en color primario azul
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
