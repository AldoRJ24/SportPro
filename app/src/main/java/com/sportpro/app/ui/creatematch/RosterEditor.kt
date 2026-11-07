package com.sportpro.app.ui.creatematch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.ExternalDraft
import com.sportpro.app.ui.components.PositionChips

/** One external-rival player: dorsal + name + position + titular/suplente. */
@Composable
fun ExternalRowEditor(
    row: ExternalDraft,
    onChange: (ExternalDraft) -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.jersey,
                    onValueChange = { v -> onChange(row.copy(jersey = v.filter { it.isDigit() }.take(2))) },
                    label = { Text("N.º") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.width(72.dp),
                )
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { v -> onChange(row.copy(name = v.take(60))) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (onRemove != null) {
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Quitar") }
                }
            }
            PositionChips(selected = row.position, onSelect = { onChange(row.copy(position = it)) })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = row.starter, onClick = { onChange(row.copy(starter = true)) }, label = { Text("Titular") })
                FilterChip(selected = !row.starter, onClick = { onChange(row.copy(starter = false)) }, label = { Text("Suplente") })
            }
        }
    }
}
