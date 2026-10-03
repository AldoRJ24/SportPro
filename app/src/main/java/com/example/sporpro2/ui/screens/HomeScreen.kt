package com.example.sporpro2.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sporpro2.viewmodel.AuthViewModel

data class HubMenu(
    val title: String,
    val route: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AuthViewModel,
    onNavigateTo: (String) -> Unit
) {
    val role by viewModel.userRole.collectAsState()

    val menuItems = when (role) {
        "ADM" -> listOf(
            HubMenu("Gestión de Equipos", "equipos", Icons.Default.Group),
            HubMenu("Control de Pagos", "pagos", Icons.Default.AttachMoney),
            HubMenu("Validar Documentos", "documentos", Icons.Default.FactCheck)
        )
        "DT" -> listOf(
            HubMenu("Partido en Vivo", "partido_vivo", Icons.Default.SportsScore),
            HubMenu("Armar Alineación", "alineacion", Icons.Default.FormatListNumbered),
            HubMenu("Convocatorias", "convocatorias", Icons.Default.Campaign),
            HubMenu("Calendario", "calendario", Icons.Default.CalendarMonth)
        )
        "PAD" -> listOf(
            HubMenu("Perfil de mi Hijo", "perfil_hijo", Icons.Default.ChildCare),
            HubMenu("Mensualidades", "pagos", Icons.Default.AttachMoney),
            HubMenu("Calendario", "calendario", Icons.Default.CalendarMonth)
        )
        else -> emptyList() // Fallback
    }

    Scaffold(
        containerColor = Color(0xFFF5F7FA), // Fondo claro
        topBar = {
            TopAppBar(
                title = { Text("SportPro Hub - $role", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1976D2) // TopBar azul oscuro
                )
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), // Rojo intenso
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Cerrar Sesión", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar Sesión", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(menuItems) { item ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { onNavigateTo(item.route) },
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color.White // Tarjetas blancas
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF1976D2) // Iconos azules
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color.Black // Texto negro de alto contraste
                        )
                    }
                }
            }
        }
    }
}
