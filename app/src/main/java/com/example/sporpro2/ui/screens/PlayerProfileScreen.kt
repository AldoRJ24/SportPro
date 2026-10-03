package com.example.sporpro2.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.sporpro2.viewmodel.PlayerProfileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileScreen(
    viewModel: PlayerProfileViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 1. Selector de fotos de perfil desde galería
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.onFotoUrlChange(uri.toString())
            }
        }
    )
    
    // 2. Estado para el Dropdown de "Pie Dominante"
    var pieExpanded by remember { mutableStateOf(false) }
    val pieOptions = listOf("Izquierda", "Derecha", "Ambidiestro")

    // 3. Estado para el DatePickerDialog de Material 3
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                            val formattedDate = dateFormat.format(Date(millis))
                            viewModel.onFechaNacimientoChange(formattedDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar", color = Color.DarkGray)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // Estilo personalizado de alto contraste para inputs
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
            
            // Renderizado con AsyncImage de Coil para cargar la foto inmediatamente
            if (uiState.fotoUrl.isNotEmpty()) {
                AsyncImage(
                    model = uiState.fotoUrl,
                    contentDescription = "Foto de perfil del jugador",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .align(Alignment.CenterHorizontally),
                    contentScale = ContentScale.Crop
                )
            }

            Button(
                onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isUploadingPhoto
            ) {
                if (uiState.isUploadingPhoto) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Subiendo imagen...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Text("Subir Foto de Perfil", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

            Text("Datos Deportivos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            
            // Campo: Posición Principal
            OutlinedTextField(
                value = uiState.posicionPrincipal,
                onValueChange = viewModel::onPosicionPrincipalChange,
                label = { Text("Posición Principal") },
                modifier = Modifier.fillMaxWidth(),
                colors = customTextFieldColors,
                shape = RoundedCornerShape(12.dp)
            )

            // Campo con ExposedDropdownMenuBox: Pie Dominante
            ExposedDropdownMenuBox(
                expanded = pieExpanded,
                onExpandedChange = { pieExpanded = !pieExpanded }
            ) {
                OutlinedTextField(
                    value = uiState.pieDominante,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pie Dominante") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pieExpanded) },
                    colors = customTextFieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = pieExpanded,
                    onDismissRequest = { pieExpanded = false },
                    containerColor = Color.White
                ) {
                    pieOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = Color.Black) },
                            onClick = {
                                viewModel.onPieDominanteChange(option)
                                pieExpanded = false
                            }
                        )
                    }
                }
            }

            // Campo con DatePickerDialog: Fecha de Nacimiento
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.fechaNacimiento,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha de Nacimiento") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Elegir Fecha",
                                tint = Color(0xFF1976D2)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors,
                    shape = RoundedCornerShape(12.dp)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true }
                )
            }

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

            HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
            
            Text("Vinculación con Apoderado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            
            OutlinedTextField(
                value = uiState.padreId,
                onValueChange = viewModel::onPadreIdChange,
                label = { Text("ID del Padre (padre_id)") },
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
