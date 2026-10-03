package com.example.sporpro2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.sporpro2.ui.theme.Sporpro2Theme
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Manejar el deep link de confirmación de email si la app fue lanzada desde él
        SupabaseClient.client.handleDeeplinks(intent)
        
        enableEdgeToEdge()
        setContent {
            Sporpro2Theme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Manejar el deep link si la app ya estaba abierta (gracias a singleTask en Manifest)
        SupabaseClient.client.handleDeeplinks(intent)
    }
}
