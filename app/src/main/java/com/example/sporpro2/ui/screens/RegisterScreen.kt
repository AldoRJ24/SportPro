package com.example.sporpro2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.sporpro2.viewmodel.AuthViewModel
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onNavigateToLogin: () -> Unit, viewModel: AuthViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("ADM") }
    var codigoApoderado by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val roles = listOf("ADM", "DT", "JUG", "PAD")

    var fechaNacimiento by remember { mutableStateOf("") }
    var edadCalculada by remember { mutableStateOf(18) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= System.currentTimeMillis()
            }
        }
    )

    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = millis
            val yearNac = calendar.get(Calendar.YEAR)
            val monthNac = calendar.get(Calendar.MONTH)
            val dayNac = calendar.get(Calendar.DAY_OF_MONTH)
            
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - yearNac
            if (today.get(Calendar.MONTH) < monthNac || 
                (today.get(Calendar.MONTH) == monthNac && today.get(Calendar.DAY_OF_MONTH) < dayNac)) {
                age--
            }
            edadCalculada = age
            val dayStr = dayNac.toString().padStart(2, '0')
            val monthStr = (monthNac + 1).toString().padStart(2, '0')
            fechaNacimiento = "$dayStr/$monthStr/$yearNac"
        }
    }
    
    val errorMessage by viewModel.errorMessage.collectAsState()
    val checkEmail by viewModel.isRegistrationSuccessCheckEmail.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (checkEmail) {
                Text(
                    text = "Registro Exitoso. Revisa tu correo electrónico para confirmar tu cuenta y luego regresa a la aplicación.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        viewModel.resetRegistrationState()
                        onNavigateToLogin() 
                    }, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volver a Inicio de Sesión")
                }
            } else {
                Text(text = "Registro en SportPro", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre Completo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        viewModel.clearError() 
                    },
                    label = { Text("Correo Electrónico") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        viewModel.clearError() 
                    },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedRole,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        roles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = {
                                    selectedRole = role
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                if (selectedRole == "JUG") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fechaNacimiento,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Fecha de Nacimiento") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha")
                            }
                        }
                    )
                    
                    if (showDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = { showDatePicker = false }) {
                                    Text("Aceptar")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) {
                                    Text("Cancelar")
                                }
                            }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }

                    if (edadCalculada < 18 && fechaNacimiento.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = codigoApoderado,
                            onValueChange = { codigoApoderado = it },
                            label = { Text("Código de Apoderado") },
                            leadingIcon = { Text("#", modifier = Modifier.padding(start = 16.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.signUp(email, password, name, selectedRole, edadCalculada.toString(), codigoApoderado) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Registrar")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volver")
                }
            }
        }
    }
}
