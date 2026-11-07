package com.sportpro.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sportpro.app.SportProApp
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.InviteLinks
import com.sportpro.app.ui.auth.AuthViewModel
import com.sportpro.app.ui.auth.LoginScreen
import com.sportpro.app.ui.auth.RegisterScreen
import com.sportpro.app.ui.family.GuardianLinkScreen
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.session.SessionState
import com.sportpro.app.ui.session.SessionViewModel

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer no disponible") }

/**
 * Raíz de la app: decide qué mostrar según la sesión (las «puertas»):
 * - sin configuración (local.properties) → aviso;
 * - cargando → indicador; sin sesión → login / registro;
 * - con sesión: si es un jugador menor sin apoderado aprobado → solo la pantalla de vínculo;
 *   en otro caso → MainShell (menú principal con pestañas).
 */
@Composable
fun SportProRoot() {
    val app = LocalContext.current.applicationContext as SportProApp
    val container = app.container
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (container == null) {
            MisconfiguredScreen()
            return@Surface
        }
        CompositionLocalProvider(LocalAppContainer provides container) {
            val session: SessionViewModel = viewModel { SessionViewModel(container) }
            val state by session.state.collectAsStateWithLifecycle()
            val ctx by session.context.collectAsStateWithLifecycle()
            val ctxError by session.contextError.collectAsStateWithLifecycle()
            when (state) {
                SessionState.Loading -> LoadingBox()
                SessionState.SignedOut -> AuthNav(container)
                is SessionState.SignedIn -> when {
                    ctx == null && ctxError != null -> ContextErrorScreen(ctxError!!, session)
                    ctx == null -> LoadingBox()
                    // Jugador menor sin apoderado aprobado: no entra a ningún apartado (migración 003)
                    ctx?.account?.needsGuardian == true -> GuardianLinkScreen(session)
                    else -> MainShell(session)
                }
            }
        }
    }
}

/** Navegación sin sesión: login ↔ registro. Si llega un QR de invitación, abre el registro con el código ya validado. */
@Composable
private fun AuthNav(container: AppContainer) {
    val nav = rememberNavController()
    val vm: AuthViewModel = viewModel { AuthViewModel(container) }
    val pendingInvite by InviteLinks.pending.collectAsStateWithLifecycle()
    // QR escaneado con la cámara del teléfono → registro con la invitación ya validada
    LaunchedEffect(pendingInvite) {
        val code = InviteLinks.consume()
        if (code != null) {
            vm.applyInvite(code)
            nav.navigate(RegisterRoute) { launchSingleTop = true }
        }
    }
    NavHost(nav, startDestination = LoginRoute) {
        composable<LoginRoute> { LoginScreen(vm, onRegister = { nav.navigate(RegisterRoute) }) }
        composable<RegisterRoute> { RegisterScreen(vm, onBackToLogin = { nav.popBackStack() }) }
    }
}

@Composable
private fun ContextErrorScreen(message: String, session: SessionViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("No se pudo cargar tu cuenta", style = MaterialTheme.typography.headlineSmall)
        Text(message)
        Button(onClick = { session.refreshContext() }) { Text("Reintentar") }
        TextButton(onClick = { session.signOut() }) { Text("Cerrar sesión") }
    }
}

@Composable
private fun MisconfiguredScreen() {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Falta configuración", style = MaterialTheme.typography.headlineSmall)
        Text("Agrega SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY en local.properties y vuelve a sincronizar Gradle.")
    }
}
