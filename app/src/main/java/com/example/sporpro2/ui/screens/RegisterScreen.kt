package com.example.sporpro2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.sporpro2.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onNavigateToLogin: () -> Unit, viewModel: AuthViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("ADM") }
    var expanded by remember { mutableStateOf(false) }
    val roles = listOf("ADM", "DT", "JUG", "PAD")
    
    val errorMessage by viewModel.errorMessage.collectAsState()
    val checkEmail by viewModel.isRegistrationSuccessCheckEmail.collectAsState()

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
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (checkEmail) {
                Text(
                    text = "Registro Exitoso. Revisa tu correo electrónico para confirmar tu cuenta y luego regresa a la aplicación.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { 
                        viewModel.resetRegistrationState()
                        onNavigateToLogin() 
                    }, 
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("Volver a Inicio de Sesión", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "Registro en SportPro", 
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre Completo") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        viewModel.clearError() 
                    },
                    label = { Text("Correo Electrónico") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        viewModel.clearError() 
                    },
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
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
                        colors = customTextFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = Color.White
                    ) {
                        roles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role, color = Color.Black) },
                                onClick = {
                                    selectedRole = role
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { viewModel.signUp(email, password, name, selectedRole) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("Registrar", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volver", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
