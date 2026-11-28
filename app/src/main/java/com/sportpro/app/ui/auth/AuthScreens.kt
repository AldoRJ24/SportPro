package com.sportpro.app.ui.auth
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportpro.app.ui.components.DATE_MASK
import com.sportpro.app.ui.components.MaskedDigitsField
import com.sportpro.app.ui.components.PasswordField
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.dateHint
import com.sportpro.app.ui.components.parseDateDigits
import com.sportpro.app.ui.components.scanQr
import com.sportpro.app.ui.theme.ErrorRed
import com.sportpro.app.ui.theme.WarnAmber

@Composable
private fun AuthFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.auth_sportpro), fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.auth_gestion_de_academias_y_partidos),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Messages(ui: AuthUiState) {
    if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    ui.error?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodyMedium) }
    ui.info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun LoginScreen(vm: AuthViewModel, onRegister: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    AuthFrame {
        Text(stringResource(R.string.auth_iniciar_sesion), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            email, { email = it.trim() }, label = { Text(stringResource(R.string.auth_correo)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(password, { password = it }, stringResource(R.string.auth_contrasena))
        Messages(ui)
        Button(
            onClick = { vm.signIn(email, password) },
            enabled = !ui.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.auth_entrar)) }
        TextButton(onClick = { vm.clearMessages(); onRegister() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.auth_no_tienes_cuenta_registrate))
        }
        OutlinedButton(
            onClick = {
                scanQr(
                    context,
                    onResult = { raw -> if (raw != null) { vm.applyInvite(raw); onRegister() } },
                    onError = { },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.auth_me_invitaron_escanear_qr)) }
    }
}

@Composable
private fun TypeCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RegisterScreen(vm: AuthViewModel, onBackToLogin: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val invite by vm.invite.collectAsStateWithLifecycle()
    val f by vm.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showInviteBox by rememberSaveable { mutableStateOf(false) }
    var typedCode by rememberSaveable { mutableStateOf("") }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = invite.kind

    AuthFrame {
        Text(stringResource(R.string.auth_crear_cuenta), style = MaterialTheme.typography.headlineSmall)

        if (kind != null) {
            // ---- Registro por invitación (ADM / DT) ----
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(stringResource(R.string.auth_invitacion_x, invite.code.orEmpty()), MaterialTheme.colorScheme.primary)
                    Text(
                        if (kind == "adm") stringResource(R.string.auth_te_estas_registrando_como_administrador)
                        else stringResource(R.string.auth_te_estas_registrando_como_entrenador, invite.check?.academyName ?: stringResource(R.string.auth_la_academia)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (kind == "adm") stringResource(R.string.auth_debes_ser_mayor_de_edad)
                        else stringResource(R.string.auth_debes_ser_mayor_de_edad_2),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = vm::clearInvite) { Text(stringResource(R.string.auth_no_usar_esta_invitacion)) }
                }
            }
        } else {
            // ---- Registro libre: apoderado o jugador ----
            Text(stringResource(R.string.auth_como_usaras_sportpro), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeCard(
                    stringResource(R.string.auth_apoderado), stringResource(R.string.auth_mayor_de_edad_gestionas_a),
                    selected = f.accountType == "pad", onClick = { vm.updateForm { it.copy(accountType = "pad") } },
                    modifier = Modifier.weight(1f),
                )
                TypeCard(
                    stringResource(R.string.auth_jugador), stringResource(R.string.auth_13_anos_o_mas_si),
                    selected = f.accountType == "jug", onClick = { vm.updateForm { it.copy(accountType = "jug") } },
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = { showInviteBox = !showInviteBox }) {
                Text(stringResource(R.string.auth_te_invitaron_como_administrador_o))
            }
            if (showInviteBox || invite.error != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            typedCode, { typedCode = it.uppercase().take(20) },
                            label = { Text(stringResource(R.string.auth_codigo_de_invitacion_adm_o)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.applyInvite(typedCode) }, enabled = typedCode.isNotBlank() && !invite.checking) {
                                Text(stringResource(R.string.auth_validar))
                            }
                            OutlinedButton(onClick = {
                                scanError = null
                                scanQr(context, onResult = { raw -> if (raw != null) vm.applyInvite(raw) }, onError = { scanError = it })
                            }) { Text(stringResource(R.string.auth_escanear_qr)) }
                        }
                        if (invite.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                        (invite.error ?: scanError)?.let { Text(it, color = WarnAmber, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        OutlinedTextField(f.firstName, { v -> vm.updateForm { it.copy(firstName = v) } }, label = { Text(stringResource(R.string.auth_nombre)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(f.lastName, { v -> vm.updateForm { it.copy(lastName = v) } }, label = { Text(stringResource(R.string.auth_apellido)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        MaskedDigitsField(
            value = f.birthDigits,
            onValueChange = { v -> vm.updateForm { it.copy(birthDigits = v) } },
            mask = DATE_MASK,
            label = stringResource(R.string.auth_fecha_de_nacimiento_dd_mm),
            isError = f.birthDigits.length == 8 && parseDateDigits(f.birthDigits) == null,
            supportingText = dateHint(f.birthDigits),
            modifier = Modifier.fillMaxWidth(),
        )
        if (kind == "adm") {
            Text(stringResource(R.string.auth_tu_academia), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(f.academyName, { v -> vm.updateForm { it.copy(academyName = v.take(80)) } }, label = { Text(stringResource(R.string.auth_nombre_de_la_academia)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.academyCity, { v -> vm.updateForm { it.copy(academyCity = v.take(60)) } }, label = { Text(stringResource(R.string.auth_ciudad)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(
            f.email, { v -> vm.updateForm { it.copy(email = v.trim()) } }, label = { Text(stringResource(R.string.auth_correo)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(f.password, { v -> vm.updateForm { it.copy(password = v) } }, stringResource(R.string.auth_contrasena_min_6))
        Messages(ui)
        Button(
            onClick = { vm.signUp(onDone = onBackToLogin) },
            enabled = !ui.busy && !invite.checking,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.auth_crear_cuenta)) }
        Text(
            stringResource(R.string.auth_tus_datos_se_usan_solo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { vm.clearMessages(); onBackToLogin() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.auth_ya_tengo_cuenta))
        }
    }
}
