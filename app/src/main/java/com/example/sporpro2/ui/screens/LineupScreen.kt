package com.example.sporpro2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sporpro2.viewmodel.LineupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineupScreen(
    viewModel: LineupViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color(0xFFF5F7FA), // Fondo azul/grisáceo claro 
        topBar = {
            TopAppBar(
                title = { Text("Alineación", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D47A1)), // Azul muy oscuro
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.convocatorias) { conv ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = conv.jugadorNombre,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                            style = MaterialTheme.typography.titleLarge
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Estado de respuesta: ", color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = conv.respuesta,
                                color = if(conv.respuesta == "Confirmado") Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFE0E0E0))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(text = "Alineación (es_titular):", color = Color.Black, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.setEsTitular(conv.id, true) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (conv.esTitular) Color(0xFF2E7D32) else Color(0xFFE0E0E0),
                                    contentColor = if (conv.esTitular) Color.White else Color.Black
                                )
                            ) {
                                Text("Titular", fontWeight = FontWeight.Bold)
                            }
                            
                            Button(
                                onClick = { viewModel.setEsTitular(conv.id, false) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!conv.esTitular) Color(0xFFD32F2F) else Color(0xFFE0E0E0),
                                    contentColor = if (!conv.esTitular) Color.White else Color.Black
                                )
                            ) {
                                Text("Suplente", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
