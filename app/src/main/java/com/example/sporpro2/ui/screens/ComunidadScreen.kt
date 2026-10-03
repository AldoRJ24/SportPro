package com.example.sporpro2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sporpro2.model.Publicacion
import com.example.sporpro2.viewmodel.ComunidadState
import com.example.sporpro2.viewmodel.ComunidadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComunidadScreen(
    onBackClick: () -> Unit,
    viewModel: ComunidadViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var nuevaPublicacion by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comunidad") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Muro de publicaciones
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = nuevaPublicacion,
                    onValueChange = { nuevaPublicacion = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe algo...") },
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (nuevaPublicacion.isNotBlank()) {
                            viewModel.insertPublicacion(nuevaPublicacion)
                            nuevaPublicacion = ""
                        }
                    }
                ) {
                    Text("Publicar")
                }
            }
            
            HorizontalDivider()

            when (val state = uiState) {
                is ComunidadState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ComunidadState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Error al cargar muro:", color = MaterialTheme.colorScheme.error)
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.fetchPublicaciones() }) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
                is ComunidadState.Success -> {
                    if (state.publicaciones.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay publicaciones en la comunidad.")
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.publicaciones) { pub ->
                                PublicacionCard(pub)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PublicacionCard(publicacion: Publicacion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = publicacion.contenido ?: "",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Fecha: ${publicacion.fechaPublicacion ?: "Reciente"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
