package com.sportpro.app.ui.tryouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.data.AcademyRef
import com.sportpro.app.core.data.Position
import com.sportpro.app.core.data.TryoutFormError
import com.sportpro.app.core.data.Tryouts
import com.sportpro.app.core.data.UserContext
import com.sportpro.app.ui.components.DATE_MASK
import com.sportpro.app.ui.components.MaskedDigitsField
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.TIME_MASK
import com.sportpro.app.ui.components.UiMessageEffect
import com.sportpro.app.ui.components.parseDateDigits
import com.sportpro.app.ui.components.parseTimeDigits
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Academias donde el usuario puede publicar avisos: es admin o DT activo y la academia está
 * verificada. Solo decide si se muestra el botón «Publicar»; el servidor vuelve a comprobarlo.
 */
fun UserContext?.publishableAcademies(): List<AcademyRef> =
    this?.memberships.orEmpty()
        .filter { it.status == "active" && (it.role == "adm" || it.role == "dt") }
        .mapNotNull { it.academy }
        .filter { it.status == "verified" }
        .distinctBy { it.id }

@Composable
private fun formErrorText(error: TryoutFormError): String = stringResource(
    when (error) {
        TryoutFormError.TITLE -> R.string.tryouts_form_error_title
        TryoutFormError.CATEGORY -> R.string.tryouts_form_error_category
        TryoutFormError.AGES -> R.string.tryouts_form_error_ages
        TryoutFormError.DATE -> R.string.tryouts_form_error_date
        TryoutFormError.PAST -> R.string.tryouts_form_error_past
        TryoutFormError.VENUE -> R.string.tryouts_form_error_venue
        TryoutFormError.DISTRICT -> R.string.tryouts_form_error_district
        TryoutFormError.SLOTS -> R.string.tryouts_form_error_slots
    },
)

/** Formulario para publicar un aviso de prueba. Al crearlo abre su detalle ([onCreated]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTryoutScreen(session: SessionViewModel, onBack: () -> Unit, onCreated: (tryoutId: String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: CreateTryoutViewModel = viewModel { CreateTryoutViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx by session.context.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    UiMessageEffect(ui.message, snack, vm::consumeMessage)
    LaunchedEffect(ui.createdId) { ui.createdId?.let(onCreated) }

    val academies = ctx.publishableAcademies()
    var academyId by rememberSaveable { mutableStateOf<String?>(null) }
    // Con una sola academia no hay nada que elegir
    val chosenAcademy = academyId ?: academies.singleOrNull()?.id

    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var minAge by rememberSaveable { mutableStateOf("") }
    var maxAge by rememberSaveable { mutableStateOf("") }
    var positions by rememberSaveable { mutableStateOf(listOf<String>()) }
    var dateDigits by rememberSaveable { mutableStateOf("") }
    var timeDigits by rememberSaveable { mutableStateOf("") }
    var venue by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    var slots by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    val date = parseDateDigits(dateDigits)
    val time = parseTimeDigits(timeDigits)
    // Validación local para avisar rápido (misma regla que tryout_create en el servidor)
    val error = Tryouts.validateForm(
        title, category, minAge.toIntOrNull(), maxAge.toIntOrNull(), date, time, venue, district,
        slots.toIntOrNull(), LocalDateTime.now(),
    )
    // El aviso de error solo aparece cuando la persona ya empezó a llenar el formulario
    val touched = title.isNotBlank() || category.isNotBlank() || venue.isNotBlank()

    SpScaffold(title = stringResource(R.string.tryouts_create_title), onBack = onBack, snackbarHostState = snack) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (academies.isEmpty()) {
                Text(stringResource(R.string.tryouts_create_no_academy), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            if (academies.size > 1) {
                Text(stringResource(R.string.tryouts_create_academy), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    academies.forEach { a ->
                        FilterChip(selected = chosenAcademy == a.id, onClick = { academyId = a.id }, label = { Text(a.name) })
                    }
                }
            }
            OutlinedTextField(
                title, { title = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tryouts_form_title)) },
            )
            OutlinedTextField(
                category, { category = it.take(30) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tryouts_form_category)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    minAge, { v -> minAge = v.filter { it.isDigit() }.take(2) }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.tryouts_form_min_age)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    maxAge, { v -> maxAge = v.filter { it.isDigit() }.take(2) }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.tryouts_form_max_age)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text(stringResource(R.string.tryouts_form_positions), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Position.entries.forEach { p ->
                    FilterChip(
                        selected = p.code in positions,
                        // Tocar un chip lo agrega o lo quita de la lista
                        onClick = { positions = if (p.code in positions) positions - p.code else positions + p.code },
                        label = { Text(p.label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MaskedDigitsField(
                    value = dateDigits, onValueChange = { dateDigits = it }, mask = DATE_MASK,
                    label = stringResource(R.string.tryouts_form_date),
                    isError = dateDigits.length == 8 && date == null,
                    modifier = Modifier.weight(1.4f),
                )
                MaskedDigitsField(
                    value = timeDigits, onValueChange = { timeDigits = it }, mask = TIME_MASK,
                    label = stringResource(R.string.tryouts_form_time),
                    isError = timeDigits.length == 4 && time == null,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                venue, { venue = it.take(120) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tryouts_form_venue)) },
            )
            OutlinedTextField(
                district, { district = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tryouts_form_district)) },
            )
            OutlinedTextField(
                slots, { v -> slots = v.filter { it.isDigit() }.take(3) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tryouts_form_slots)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                notes, { notes = it.take(300) }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tryouts_form_notes)) },
            )
            if (error != null && touched) {
                Text(formErrorText(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    // La hora escrita es la del celular; se envía con su zona horaria para que el
                    // servidor guarde el instante exacto (timestamptz).
                    val eventAt = LocalDateTime.of(date!!, time!!).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
                    vm.create(
                        chosenAcademy!!, title, category, minAge.toInt(), maxAge.toInt(), positions,
                        eventAt, venue, district, slots.toInt(), notes,
                    )
                },
                // error == null garantiza que fecha, hora, edades y cupos son válidos (por eso los !! y toInt)
                enabled = error == null && chosenAcademy != null && !ui.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tryouts_create_action)) }
        }
    }
}
