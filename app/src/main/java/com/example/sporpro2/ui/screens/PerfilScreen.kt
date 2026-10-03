package com.example.sporpro2.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.compose.foundation.clickable
import com.example.sporpro2.viewmodel.PerfilState
import com.example.sporpro2.viewmodel.PerfilViewModel
import com.example.sporpro2.ui.components.EstadisticasTemporadaComponent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(
    onBackClick: () -> Unit,
    viewModel: PerfilViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var posicion by remember { mutableStateOf("") }
    var pieDominante by remember { mutableStateOf("") }
    var estatura by remember { mutableStateOf("") }
    var peso by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    val imageCropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                if (bytes != null) viewModel.subirAvatar(bytes)
            }
        }
    }
    
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageCropLauncher.launch(
                CropImageContractOptions(it, CropImageOptions(
                    cropShape = CropImageView.CropShape.OVAL,
                    fixAspectRatio = true
                ))
            )
        }
    }

    // Rellenar campos iniciales y escuchar actualizaciones
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is PerfilState.Success -> {
                if (!initialized) {
                    state.jugador?.let { jug ->
                        posicion = jug.posicionPrincipal ?: ""
                        pieDominante = jug.pieDominante ?: ""
                        estatura = jug.estaturaCm?.toString() ?: ""
                        peso = jug.pesoKg?.toString() ?: ""
                    }
                    initialized = true
                }
            }
            is PerfilState.Saved -> {
                Toast.makeText(context, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                // Una vez guardado, volvemos al estado success con los nuevos datos
                viewModel.cargarPerfil()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil") },
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
                is PerfilState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PerfilState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Error:", color = MaterialTheme.colorScheme.error)
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.cargarPerfil() }) {
                            Text("Reintentar")
                        }
                    }
                }
                is PerfilState.Success, is PerfilState.Saved -> {
                    val jugador = if (state is PerfilState.Success) state.jugador else (state as PerfilState.Saved).jugador
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!jugador?.avatarUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = "${jugador.avatarUrl}?t=${System.currentTimeMillis()}",
                                contentDescription = "Foto Perfil",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .clickable { imagePicker.launch("image/*") },
                                loading = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    }
                                },
                                error = {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Foto Perfil",
                                        modifier = Modifier.fillMaxSize(),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Foto Perfil",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .clickable { imagePicker.launch("image/*") },
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        OutlinedTextField(
                            value = posicion,
                            onValueChange = { posicion = it },
                            label = { Text("Posición Principal (ej. Delantero)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pieDominante,
                            onValueChange = { pieDominante = it },
                            label = { Text("Pie Dominante (ej. Derecho)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = estatura,
                            onValueChange = { estatura = it },
                            label = { Text("Estatura (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = peso,
                            onValueChange = { peso = it },
                            label = { Text("Peso (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val pesoDouble = peso.toDoubleOrNull() ?: 0.0
                                val estaturaDouble = estatura.toDoubleOrNull() ?: 0.0
                                viewModel.guardarPerfil(
                                    posicion = posicion,
                                    peso = pesoDouble,
                                    estatura = estaturaDouble,
                                    pieDominante = pieDominante
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Guardar Cambios", style = MaterialTheme.typography.titleMedium)
                        }

                        // --- Componente de Estadísticas de Temporada (Mock) ---
                        EstadisticasTemporadaComponent()
                        
                        // Añadir un espacio final al scroll
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
