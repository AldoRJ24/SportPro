package com.sportpro.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE d 'de' MMM yyyy", Locale.forLanguageTag("es-PE"))
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-PE"))

/**
 * Campo de fecha que se toca para abrir un calendario (no hay que escribir los 8 dígitos).
 * Solo deja elegir desde [minDate] en adelante: un partido no se programa en el pasado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate?,
    onDate: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minDate: LocalDate = LocalDate.now(),
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        text = date?.format(dayFormat)?.replaceFirstChar { it.uppercase() }.orEmpty(),
        label = label,
        icon = { Icon(Icons.Rounded.DateRange, contentDescription = null) },
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        // El calendario trabaja en milisegundos UTC a medianoche: se convierte con UTC para no
        // correr la fecha un día por la zona horaria de Perú
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: minDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(minDate)

                override fun isSelectableYear(year: Int): Boolean = year >= minDate.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

/** Campo de hora que abre un reloj de agujas (con a. m. / p. m.) en vez de escribir HH:MM. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    time: LocalTime?,
    onTime: (LocalTime) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        text = time?.format(timeFormat).orEmpty(),
        label = label,
        icon = { Icon(painterResource(R.drawable.ic_clock), contentDescription = null) },
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        val initial = time ?: LocalTime.now()
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onTime(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/**
 * Campo de solo lectura que se ve como los demás del formulario pero se toca entero para abrir
 * el selector. Una capa transparente encima recibe el toque (el campo de texto no se edita).
 */
@Composable
private fun PickerField(
    text: String,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = icon,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        )
    }
}
