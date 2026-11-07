package com.sportpro.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sportpro.app.core.InviteLinks
import io.github.jan.supabase.auth.handleDeeplinks
import com.sportpro.app.ui.navigation.SportProRoot
import com.sportpro.app.ui.theme.SportProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-dark UI: light icons on transparent system bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Solo en el primer arranque (no al rotar la pantalla) se procesan los enlaces que abrieron la app.
        if (savedInstanceState == null) {
            handleInvite(intent)
            handleAuthLink(intent)
        }
        setContent {
            SportProTheme { SportProRoot() }
        }
    }

    // App ya abierta (singleTask): un enlace nuevo llega por aquí.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInvite(intent)
        handleAuthLink(intent)
    }

    /**
     * Deep link del correo de verificación: sportpro://auth-callback#access_token=…
     * Supabase verifica el enlace y redirige aquí; handleDeeplinks guarda la sesión y la app entra sola.
     */
    private fun handleAuthLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == SupabaseProvider.DEEPLINK_SCHEME && data.host == SupabaseProvider.DEEPLINK_AUTH_HOST &&
            SupabaseProvider.isConfigured
        ) {
            SupabaseProvider.client.handleDeeplinks(intent)
        }
    }

    /** Deep link de invitación sportpro://invite/CÓDIGO (QR escaneado con la cámara del teléfono). */
    private fun handleInvite(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == InviteLinks.SCHEME && data.host == InviteLinks.HOST) {
            InviteLinks.offer(data.lastPathSegment)
        }
    }
}
