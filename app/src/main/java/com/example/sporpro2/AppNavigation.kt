package com.example.sporpro2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.sporpro2.ui.screens.LoginScreen
import com.example.sporpro2.ui.screens.RegisterScreen
import com.example.sporpro2.ui.screens.HomeScreen
import com.example.sporpro2.ui.screens.EquiposScreen
import com.example.sporpro2.ui.screens.EnConstruccionScreen
import com.example.sporpro2.viewmodel.AuthViewModel
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    
    val sessionStatus by authViewModel.sessionStatus.collectAsState()
    
    // Automatically navigate depending on authentication state
    LaunchedEffect(sessionStatus) {
        when (sessionStatus) {
            is SessionStatus.Authenticated -> {
                navController.navigate("home") {
                    popUpTo(0)
                }
            }
            is SessionStatus.NotAuthenticated -> {
                navController.navigate("login") {
                    popUpTo(0)
                }
            }
            else -> {
                // Initializing or RefreshFailure
            }
        }
    }

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onNavigateToRegister = { navController.navigate("register") },
                viewModel = authViewModel
            )
        }
        composable("register") {
            RegisterScreen(
                onNavigateToLogin = { navController.navigateUp() },
                viewModel = authViewModel
            )
        }
        composable("home") {
            HomeScreen(
                viewModel = authViewModel,
                onNavigateTo = { route -> navController.navigate(route) }
            )
        }
        // Modulo principal (Semana 7)
        composable("equipos") {
            EquiposScreen(onBackClick = { navController.navigateUp() })
        }
        
        // --- Placeholders para el 60% ---
        composable("pagos") {
            EnConstruccionScreen("Control de Pagos (US-10)", onBackClick = { navController.navigateUp() })
        }
        composable("documentos") {
            EnConstruccionScreen("Validar Documentos (US-16)", onBackClick = { navController.navigateUp() })
        }
        composable("partido_vivo") {
            EnConstruccionScreen("Partido en Vivo (US-01)", onBackClick = { navController.navigateUp() })
        }
        composable("alineacion") {
            EnConstruccionScreen("Armar Alineación (US-09)", onBackClick = { navController.navigateUp() })
        }
        composable("convocatorias") {
            EnConstruccionScreen("Convocatorias (US-04)", onBackClick = { navController.navigateUp() })
        }
        composable("calendario") {
            EnConstruccionScreen("Calendario (US-13)", onBackClick = { navController.navigateUp() })
        }
        composable("perfil_hijo") {
            EnConstruccionScreen("Perfil de mi Hijo (US-15)", onBackClick = { navController.navigateUp() })
        }
    }
}
