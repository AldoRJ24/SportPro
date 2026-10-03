package com.example.sporpro2.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sporpro2.viewmodel.PlayerProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileScreen(
    viewModel: PlayerProfileViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Lanzador para abrir la galería de fotos del dispositivo
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                // Se guarda la URI de la imagen seleccionada
                viewModel.onFotoUrlChange(uri.toString())
            }
        }
    )
    
    // Forzar el alto contraste del TextField en todas sus fases
    val customTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        focusedLabelColor = Color(0xFF1976D2),
        unfocusedLabelColor = Color.DarkGray,
        focusedBorderColor = Color(0xFF1976D2),
        unfocusedBorderColor = Color.Gray,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("Perfil del Jugador", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1976D2)),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Datos Deportivos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            
            OutlinedTextField(
                value = uiState.posicionPrincipal,
                onValueChange = viewModel::onPosicionPrincipalChange,
                label = { Text("Posición Principal") },
                modifier = Modifier.fillMaxWidth(),
                colors = customTextFieldColors,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = uiState.pieDominante,
                onValueChange = viewModel::onPieDominanteChange,
                label = { Text("Pie Dominante") },
                modifier = Modifier.fillMaxWidth(),
                colors = customTextFieldColors,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = uiState.fechaNacimiento,
                onValueChange = viewModel::onFechaNacimientoChange,
                label = { Text("Fecha de Nacimiento") },
                modifier = Modifier.fillMaxWidth(),
                colors = customTextFieldColors,
                shape = RoundedCornerShape(12.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = uiState.pesoKg,
                    onValueChange = viewModel::onPesoKgChange,
                    label = { Text("Peso (kg)") },
                    modifier = Modifier.weight(1f),
                    colors = customTextFieldColors,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = uiState.estaturaCm,
                    onValueChange = viewModel::onEstaturaCmChange,
                    label = { Text("Estatura (cm)") },
                    modifier = Modifier.weight(1f),
                    colors = customTextFieldColors,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            Button(
                // Al hacer clic, abre el selector nativo de imágenes
                onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cargar Fotografía (Galería)", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            if (uiState.fotoUrl.isNotEmpty()) {
                Text(text = "✓ Foto seleccionada: ...${uiState.fotoUrl.takeLast(20)}", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
            
            Text("Vinculación con Apoderado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            
            OutlinedTextField(
                value = uiState.padreId,
                onValueChange = viewModel::onPadreIdChange,
                label = { Text("ID del Padre") },
                modifier = Modifier.fillMaxWidth(),
                colors = customTextFieldColors,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = uiState.parentesco,
                onValueChange = viewModel::onParentescoChange,
                label = { Text("Parentesco") },
                modifier = Modifier.fillMaxWidth(),
                colors = customTextFieldColors,
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { viewModel.guardarPerfil() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Guardar Perfil", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            
            if (uiState.saveSuccess) {
                Text("Perfil guardado con éxito", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
