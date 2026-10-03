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

@Composable
fun LoginScreen(onNavigateToRegister: () -> Unit, viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val errorMessage by viewModel.errorMessage.collectAsState()

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
            Text(
                text = "SportPro - Login", 
                style = MaterialTheme.typography.headlineLarge, 
                fontWeight = FontWeight.ExtraBold, 
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(24.dp))
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
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { viewModel.login(email, password) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text("Iniciar Sesión", fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("¿No tienes cuenta? Regístrate", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
            }
        }
    }
}
