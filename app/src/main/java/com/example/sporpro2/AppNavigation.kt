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
import com.example.sporpro2.ui.screens.ComunidadScreen
import com.example.sporpro2.ui.screens.PartidosScreen
import com.example.sporpro2.ui.screens.PerfilScreen
import com.example.sporpro2.ui.screens.EntrenamientosScreen
import com.example.sporpro2.ui.screens.DirectorioScreen
import com.example.sporpro2.ui.screens.PagosScreen
import com.example.sporpro2.ui.screens.DocumentosScreen
import com.example.sporpro2.ui.screens.AnunciosScreen
import com.example.sporpro2.ui.screens.CalendarioScreen
import com.example.sporpro2.ui.screens.NotificacionesScreen
import com.example.sporpro2.ui.screens.PartidoEnVivoScreen
import com.example.sporpro2.ui.screens.PerfilPadreScreen
import com.example.sporpro2.ui.screens.DetallePartidoScreen
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
        // Modulos Reales (Avance al 80%)
        composable("equipos") { EquiposScreen(onBackClick = { navController.navigateUp() }) }
        composable("comunidad") { ComunidadScreen(onBackClick = { navController.navigateUp() }) }
        composable("historial_partidos") { 
            PartidosScreen(
                onBackClick = { navController.navigateUp() },
                onNavigateToDetail = { navController.navigate("detalle_partido") }
            ) 
        }
        composable("detalle_partido") { DetallePartidoScreen(onBackClick = { navController.navigateUp() }) }
        composable("perfil_jugador") { PerfilScreen(onBackClick = { navController.navigateUp() }) }
        composable("entrenamientos") { EntrenamientosScreen(onBackClick = { navController.navigateUp() }) }
        
        // --- 5 Nuevos Módulos Funcionales (Avance al 100%) ---
        composable("directorio") { DirectorioScreen(onBackClick = { navController.navigateUp() }) }
        composable("pagos") { PagosScreen(onBackClick = { navController.navigateUp() }) }
        composable("documentos") { DocumentosScreen(onBackClick = { navController.navigateUp() }) }
        composable("anuncios") { AnunciosScreen(onBackClick = { navController.navigateUp() }) }
        composable("calendario") { CalendarioScreen(onBackClick = { navController.navigateUp() }) }
        composable("notificaciones") { NotificacionesScreen(onBackClick = { navController.navigateUp() }) }
        composable("partido_vivo") { PartidoEnVivoScreen(onBackClick = { navController.navigateUp() }) }
        composable("perfil_padre") { 
            PerfilPadreScreen(
                onBackClick = { navController.navigateUp() },
                onNavigateToChildProfile = { navController.navigate("perfil_jugador") }
            ) 
        }

        // --- Placeholders (solo para los que faltan) ---
        composable("alineacion") {
            EnConstruccionScreen("Armar Alineación (US-09)", onBackClick = { navController.navigateUp() })
        }
        composable("convocatorias") {
            EnConstruccionScreen("Convocatorias (US-04)", onBackClick = { navController.navigateUp() })
        }
    }
}
