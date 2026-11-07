package com.sportpro.app.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.sportpro.app.core.data.AcademyRef
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.components.prepareAvatarJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.MemberRow
import com.sportpro.app.core.data.TeamRef
import com.sportpro.app.core.data.TeamStaffMember
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.Avatar
import com.sportpro.app.ui.components.EmptyState
import com.sportpro.app.ui.components.SectionHeader
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AcademyTeamsUi(
    val loading: Boolean = true,
    val academy: AcademyRef? = null,
    val teams: List<TeamRef> = emptyList(),
    val staff: List<TeamStaffMember> = emptyList(),
    val coaches: List<MemberRow> = emptyList(),
    val busy: Boolean = false,
)

class AcademyTeamsViewModel(private val container: AppContainer, private val academyId: String) : ViewModel() {
    private val _ui = MutableStateFlow(AcademyTeamsUi())
    val ui: StateFlow<AcademyTeamsUi> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val teams = container.account.academyTeams(academyId)
                val staff = container.account.teamStaff(teams.map { it.id })
                val coaches = container.account.academyCoaches(academyId)
                // La foto de la academia no bloquea la pantalla si falla
                val academy = runCatching { container.account.academy(academyId) }.getOrNull()
                _ui.update {
                    AcademyTeamsUi(
                        loading = false, academy = academy, teams = teams, staff = staff,
                        coaches = coaches.distinctBy { it.userId },
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false) }
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                block()
                load()
            } catch (e: Exception) {
                _messages.tryEmit(e.friendlyMessage())
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    fun createTeam(name: String, category: String, maxAge: Int?, format: Int, half: Int, onDone: () -> Unit) {
        if (name.trim().length < 2 || category.isBlank()) {
            _messages.tryEmit("Escribe el nombre y la categoría del equipo.")
            return
        }
        act {
            container.account.createTeam(academyId, name, category, maxAge, format, half)
            _messages.tryEmit("Equipo creado")
            onDone()
        }
    }

    fun assign(team: TeamRef, coach: MemberRow) = act {
        container.account.assignCoach(team.id, coach.userId)
        _messages.tryEmit("${coach.profile?.fullName ?: "DT"} asignado a ${team.name}")
    }

    fun unassign(member: TeamStaffMember) = act { container.account.removeCoach(member.teamId, member.userId) }

    // Foto de la academia: solo el admin llega a esta pantalla y el servidor vuelve a comprobarlo.
    fun setLogo(jpeg: ByteArray, onChanged: () -> Unit) = act {
        container.account.uploadAcademyLogo(academyId, jpeg)
        _messages.tryEmit("Foto de la academia actualizada en todos sus equipos")
        onChanged()
    }

    fun removeLogo(onChanged: () -> Unit) = act {
        container.account.removeAcademyLogo(academyId)
        _messages.tryEmit("Foto de la academia quitada")
        onChanged()
    }
}

/** ADM: foto de la academia, crea equipos de su academia y les asigna DT (los DT llegan por invitación QR). */
@Composable
fun AcademyTeamsScreen(
    academyId: String,
    academyName: String,
    onBack: () -> Unit,
    onInviteCoach: () -> Unit,
    onLogoChanged: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val vm: AcademyTeamsViewModel = viewModel(key = "teams-$academyId") { AcademyTeamsViewModel(container, academyId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var maxAge by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(7) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Galería del sistema → recorte cuadrado 512 px → subir como foto de la academia
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val jpeg = runCatching { withContext(Dispatchers.Default) { prepareAvatarJpeg(context, uri) } }.getOrNull()
                if (jpeg == null) snack.showSnackbar("No se pudo leer la imagen") else vm.setLogo(jpeg, onLogoChanged)
            }
        }
    }

    SpScaffold(title = "Equipos · $academyName", onBack = onBack, snackbarHostState = snack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Foto de la academia") }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamCrest(ui.academy?.name ?: academyName, ui.academy?.crestPath, size = 72.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Se muestra en la academia y en todos sus equipos. Solo el administrador puede cambiarla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = !ui.busy,
                            ) { Text("Cambiar foto") }
                            if (ui.academy?.crestPath != null) {
                                TextButton(onClick = { vm.removeLogo(onLogoChanged) }, enabled = !ui.busy) { Text("Quitar") }
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Nuevo equipo") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nombre (ej. Halcones)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(category, { category = it.take(20) }, label = { Text("Categoría (ej. Sub-15)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            maxAge, { v -> maxAge = v.filter { it.isDigit() }.take(2) }, label = { Text("Edad máx.") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.6f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 7, 9, 11).forEach { n ->
                            FilterChip(selected = format == n, onClick = { format = n }, label = { Text("$n vs $n") })
                        }
                    }
                    Button(
                        onClick = {
                            vm.createTeam(name, category, maxAge.toIntOrNull(), format, if (format >= 9) 35 else 25) {
                                name = ""; category = ""; maxAge = ""
                            }
                        },
                        enabled = !ui.busy,
                    ) { Text("Crear equipo") }
                }
            }
            item { SectionHeader("Equipos y su DT") }
            if (!ui.loading && ui.teams.isEmpty()) item { EmptyState("Sin equipos", "Crea el primer equipo de la academia.") }
            items(ui.teams, key = { it.id }) { team ->
                val staff = ui.staff.filter { it.teamId == team.id }
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamCrest(team.name, team.crestPath, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("${team.name} · ${team.category ?: ""}", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            "Formato ${team.defaultFormatPlayers} vs ${team.defaultFormatPlayers} · ${team.halfMinutes}' por tiempo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            staff.forEach { s ->
                                InputChip(
                                    selected = true,
                                    onClick = { vm.unassign(s) },
                                    label = { Text("DT: ${s.profile?.fullName ?: "—"} ✕") },
                                    avatar = { Avatar(s.profile?.fullName ?: "?", s.profile?.avatarPath, size = 24.dp) },
                                )
                            }
                        }
                        AssignCoachButton(
                            coaches = ui.coaches.filter { c -> staff.none { it.userId == c.userId } },
                            enabled = !ui.busy,
                            onPick = { vm.assign(team, it) },
                            onInvite = onInviteCoach,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignCoachButton(coaches: List<MemberRow>, enabled: Boolean, onPick: (MemberRow) -> Unit, onInvite: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled) { Text("Asignar DT") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (coaches.isEmpty()) {
                DropdownMenuItem(text = { Text("No hay DT disponibles: invitar uno") }, onClick = { open = false; onInvite() })
            }
            coaches.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.profile?.fullName?.ifBlank { null } ?: "Entrenador") },
                    leadingIcon = { Avatar(c.profile?.fullName ?: "?", c.profile?.avatarPath, size = 28.dp) },
                    onClick = { open = false; onPick(c) },
                )
            }
        }
    }
}
