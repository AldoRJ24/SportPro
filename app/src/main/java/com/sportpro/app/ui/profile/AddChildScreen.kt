package com.sportpro.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import kotlinx.coroutines.launch
import com.sportpro.app.ui.components.DATE_MASK
import com.sportpro.app.ui.components.MaskedDigitsField
import com.sportpro.app.ui.components.dateHint
import com.sportpro.app.ui.components.parseDateDigits

/** D-08/D-09: only the guardian creates the profile of an under-13 child; it is linked and approved at once. */
@Composable
fun AddChildScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var birth by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    SpScaffold(title = "Agregar hijo", onBack = onBack) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Para menores de 13 años. El perfil lo gestionas tú; a los 13 podrá reclamarlo con su propia cuenta conservando su historial.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(first, { first = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(last, { last = it }, label = { Text("Apellido") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            MaskedDigitsField(
                value = birth,
                onValueChange = { birth = it },
                mask = DATE_MASK,
                label = "Fecha de nacimiento (DD/MM/AAAA)",
                isError = birth.length == 8 && parseDateDigits(birth) == null,
                supportingText = dateHint(birth),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(position, { position = it }, label = { Text("Posición (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    val date = parseDateDigits(birth)
                    if (first.isBlank() || last.isBlank() || date == null) {
                        error = "Completa nombre, apellido y una fecha de nacimiento válida (8 dígitos)."
                        return@Button
                    }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            container.club.createChild(first.trim(), last.trim(), date.toString(), position.trim().ifBlank { null })
                            onCreated()
                            onBack()
                        } catch (e: Exception) {
                            error = e.friendlyMessage()
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Crear perfil") }
        }
    }
}
