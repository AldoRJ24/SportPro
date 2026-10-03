package com.example.sporpro2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
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
import com.example.sporpro2.model.Documento
import com.example.sporpro2.viewmodel.DocumentosState
import com.example.sporpro2.viewmodel.DocumentosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentosScreen(
    onBackClick: () -> Unit,
    viewModel: DocumentosViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Documentos") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is DocumentosState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DocumentosState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Error:", color = MaterialTheme.colorScheme.error)
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.fetchDocumentos() }) {
                            Text("Reintentar")
                        }
                    }
                }
                is DocumentosState.Success -> {
                    if (state.documentos.isEmpty()) {
                        Text("No hay documentos registrados.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.documentos) { doc ->
                                DocumentoCard(doc)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentoCard(doc: Documento) {
    val isVigente = doc.estado?.lowercase() == "vigente"
    val icon = if (isVigente) Icons.Default.CheckCircle else Icons.Default.Warning
    val iconColor = if (isVigente) Color(0xFF4CAF50) else Color(0xFFE53935)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = doc.tipoDocumento ?: "Documento",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Vence: ${doc.fechaVencimiento ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = doc.estado,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
