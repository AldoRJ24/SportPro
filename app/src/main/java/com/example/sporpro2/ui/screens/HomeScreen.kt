package com.example.sporpro2.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        topBar = {
            TopAppBar(
                title = { Text("SportPro Hub - $role") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { onNavigateTo(item.route) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
