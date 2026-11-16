package com.sportpro.app.ui.profile
import com.sportpro.app.core.AppText
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportpro.app.SupabaseProvider
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.Avatar
import com.sportpro.app.ui.components.AvatarCache
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.components.prepareAvatarJpeg
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Editar nombre y foto de perfil (visibles para quienes comparten academia contigo). */
@Composable
fun EditProfileScreen(session: SessionViewModel, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val ctx by session.context.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var pendingJpeg by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val isAdult = (ctx?.account?.age ?: 18) >= 18
    LaunchedEffect(ctx?.profile?.fullName) { if (name.isEmpty()) name = ctx?.profile?.fullName.orEmpty() }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); message = null } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val jpeg = withContext(Dispatchers.Default) { prepareAvatarJpeg(context, uri) }
                    pendingJpeg = jpeg
                    preview = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
                } catch (e: Exception) {
                    message = AppText.get(R.string.edit_profile_no_se_pudo_leer_la)
                }
            }
        }
    }

    SpScaffold(title = stringResource(R.string.edit_profile_editar_perfil), onBack = onBack, snackbarHostState = snack) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val p = preview
            if (p != null) {
                Image(p, contentDescription = stringResource(R.string.edit_profile_vista_previa), contentScale = ContentScale.Crop, modifier = Modifier.size(132.dp).clip(CircleShape))
            } else {
                Avatar(ctx?.displayName ?: "?", ctx?.profile?.avatarPath, size = 132.dp)
            }
            if (isAdult) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.edit_profile_elegir_foto)) }
                    if (ctx?.profile?.avatarPath != null && p == null) {
                        TextButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        container.account.removeAvatar()
                                        session.refreshContext()
                                        message = AppText.get(R.string.edit_profile_foto_quitada)
                                    } catch (e: Exception) {
                                        message = e.friendlyMessage()
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.edit_profile_quitar_foto)) }
                    }
                }
                Text(
                    stringResource(R.string.edit_profile_tu_foto_la_ven_las),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.edit_profile_por_privacidad_las_cuentas_de),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(stringResource(R.string.edit_profile_nombre_completo)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (name.trim().length < 3) {
                        message = AppText.get(R.string.edit_profile_escribe_tu_nombre_completo)
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        try {
                            if (name.trim() != ctx?.profile?.fullName) container.account.updateMyName(name)
                            pendingJpeg?.let { jpeg ->
                                val path = container.account.uploadAvatar(jpeg)
                                SupabaseProvider.avatarUrl(path)?.let { url -> preview?.let { AvatarCache.put(url, it) } }
                            }
                            pendingJpeg = null
                            preview = null
                            session.refreshContext()
                            message = AppText.get(R.string.edit_profile_perfil_actualizado)
                        } catch (e: Exception) {
                            message = e.friendlyMessage()
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.edit_profile_guardar_cambios)) }
        }
    }
}
