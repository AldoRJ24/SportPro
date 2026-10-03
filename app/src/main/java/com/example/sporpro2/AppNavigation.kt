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
import com.example.sporpro2.ui.screens.AdminDashboardScreen
import com.example.sporpro2.ui.screens.DtDashboardScreen
import com.example.sporpro2.ui.screens.JugadorDashboardScreen
import com.example.sporpro2.ui.screens.PadreDashboardScreen
import com.example.sporpro2.ui.screens.TeamsScreen
import com.example.sporpro2.ui.screens.LineupScreen
import com.example.sporpro2.ui.screens.PlayerProfileScreen
import com.example.sporpro2.ui.screens.EnConstruccionScreen
import com.example.sporpro2.viewmodel.AuthViewModel
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    
    val sessionStatus by authViewModel.sessionStatus.collectAsState()
    val userRole by authViewModel.userRole.collectAsState()

    // Automatically navigate depending on authentication state
    LaunchedEffect(sessionStatus, userRole) {
        when (sessionStatus) {
            is SessionStatus.Authenticated -> {
                // Esperar a que el rol se cargue antes de navegar
                if (userRole.isNotEmpty()) {
                    val destination = when (userRole.uppercase()) {
                        "ADM" -> "dashboard_admin"
                        "DT" -> "dashboard_dt"
                        "PAD" -> "dashboard_padre"
                        "JUG" -> "dashboard_jugador"
                        else -> "home" // Fallback general
                    }
                    // Solo navegamos si no estamos ya en el destino (evita recomposiciones infinitas)
                    if (navController.currentDestination?.route != destination) {
                        navController.navigate(destination) {
                            popUpTo(0)
                        }
                    }
                }
            }
            is SessionStatus.NotAuthenticated -> {
                if (navController.currentDestination?.route != "login" && navController.currentDestination?.route != "register") {
                    navController.navigate("login") {
                        popUpTo(0)
                    }
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
        // Fallback genérico (se conservará por si acaso, aunque los de rol son los principales ahora)
        composable("home") {
            HomeScreen(
                viewModel = authViewModel,
                onNavigateTo = { route -> navController.navigate(route) }
            )
        }
        
        // --- Dashboards Específicos por Rol ---
        composable("dashboard_admin") {
            AdminDashboardScreen(
                viewModel = authViewModel,
                onNavigateTo = { route -> navController.navigate(route) }
            )
        }
        composable("dashboard_dt") {
            DtDashboardScreen(
                viewModel = authViewModel,
                onNavigateTo = { route -> navController.navigate(route) }
            )
        }
        composable("dashboard_padre") {
            PadreDashboardScreen(
                viewModel = authViewModel,
                onNavigateTo = { route -> navController.navigate(route) }
            )
        }
        composable("dashboard_jugador") {
            JugadorDashboardScreen(
                viewModel = authViewModel,
                onNavigateTo = { route -> navController.navigate(route) }
            )
        }

        // --- Pantallas de Usuario (US) Finalizadas ---
        composable("equipos") {
            TeamsScreen(onBackClick = { navController.navigateUp() })
        }
        composable("alineacion") {
            LineupScreen(onBackClick = { navController.navigateUp() })
        }
        composable("perfil_jugador") {
            PlayerProfileScreen(onBackClick = { navController.navigateUp() })
        }
        
        // --- Placeholders para el resto (60%) ---
        composable("pagos") {
            EnConstruccionScreen("Control de Mensualidades (US-10)", onBackClick = { navController.navigateUp() })
        }
        composable("documentos") {
            EnConstruccionScreen("Validar Documentos (US-16)", onBackClick = { navController.navigateUp() })
        }
        composable("partido_vivo") {
            EnConstruccionScreen("Partido en Vivo (US-01)", onBackClick = { navController.navigateUp() })
        }
        composable("convocatorias") {
            EnConstruccionScreen("Convocatorias (US-04)", onBackClick = { navController.navigateUp() })
        }
        composable("calendario") {
            EnConstruccionScreen("Calendario (US-13)", onBackClick = { navController.navigateUp() })
        }
        composable("estadisticas") {
            EnConstruccionScreen("Mis Estadísticas (US-11)", onBackClick = { navController.navigateUp() })
        }
        composable("muro") {
            com.example.sporpro2.ui.screens.ComunidadScreen(onBackClick = { navController.navigateUp() })
        }
        composable("partidos_anteriores") {
            com.example.sporpro2.ui.screens.PartidosAnterioresScreen(onBackClick = { navController.navigateUp() })
        }
        composable("resumen_ia") {
            EnConstruccionScreen("Resumen con IA (US-02)", onBackClick = { navController.navigateUp() })
        }
        composable("entrenamientos") {
            EnConstruccionScreen("Entrenamientos (US-05)", onBackClick = { navController.navigateUp() })
        }
        composable("anuncios") {
            EnConstruccionScreen("Enviar Anuncios (US-07)", onBackClick = { navController.navigateUp() })
        }
        composable("pruebas") {
            EnConstruccionScreen("Pruebas de Talento (US-12)", onBackClick = { navController.navigateUp() })
        }
    }
}
