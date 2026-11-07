package com.sportpro.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sportpro.app.ui.theme.SportProTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportProTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConnectionCheckScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Serializable
data class EventTypeRow(val id: String, val code: String, val name: String)

/**
 * Temporary diagnostic screen (block 2): signs in with a demo account and reads the
 * event catalog. RLS requires an authenticated user, so 17 rows = URL, key, auth and RLS OK.
 * Will be replaced by the real login in block 3.
 */
@Composable
fun ConnectionCheckScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val notConfigured = stringResource(R.string.check_not_configured)
    val running = stringResource(R.string.check_running)

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.check_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = email, onValueChange = { email = it.trim() },
            label = { Text(stringResource(R.string.field_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.field_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                if (!SupabaseProvider.isConfigured) { status = notConfigured; return@Button }
                busy = true; status = running
                val userEmail = email
                val userPassword = password
                scope.launch {
                    status = try {
                        val client = SupabaseProvider.client
                        client.auth.signInWith(Email) {
                            this.email = userEmail
                            this.password = userPassword
                        }
                        val rows = client.from("event_types")
                            .select(Columns.list("id", "code", "name"))
                            .decodeList<EventTypeRow>()
                        Log.d("SportPro", "Catalog: ${rows.size} event types")
                        "OK · Catálogo: ${rows.size} tipos de evento\n" +
                            rows.joinToString(", ") { it.name }
                    } catch (e: Exception) {
                        Log.e("SportPro", "Connection check failed", e)
                        "Error: ${e::class.simpleName}: ${e.message?.take(300)}"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.check_button)) }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}
