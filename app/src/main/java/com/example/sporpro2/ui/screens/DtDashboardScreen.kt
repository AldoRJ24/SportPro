package com.example.sporpro2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sporpro2.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtDashboardScreen(
    viewModel: AuthViewModel,
    onNavigateTo: (String) -> Unit
) {
    Scaffold(
        containerColor = Color.White, // Fondo blanco para mayor contraste
        topBar = {
            TopAppBar(
                title = { Text("Panel del Entrenador", color = Color.Black, fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    DtMenuCard("Partido en Vivo", Icons.Default.SportsScore) { onNavigateTo("partido_vivo") }
                }
                item {
                    DtMenuCard("Alineación", Icons.Default.FormatListNumbered) { onNavigateTo("alineacion") }
                }
                item {
                    DtMenuCard("Resumen con IA", Icons.Default.SmartToy) { onNavigateTo("resumen_ia") }
                }
                item {
                    DtMenuCard("Entrenamientos", Icons.Default.FitnessCenter) { onNavigateTo("entrenamientos") }
                }
                item {
                    DtMenuCard("Convocatorias", Icons.Default.Campaign) { onNavigateTo("convocatorias") }
                }
            }

            // Botón de cerrar sesión en la parte inferior
            Box(modifier = Modifier.padding(24.dp)) {
                Button(
                    onClick = { viewModel.logout() },
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
}

@Composable
fun DtMenuCard(title: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = Color(0xFFF0F4F8), // Fondo gris/azulado súper claro
            contentColor = Color.Black // Texto negro de alto contraste
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2E7D32), // Verde oscuro vibrante
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
