package com.sportpro.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import com.sportpro.app.ui.session.SessionViewModel
import com.sportpro.app.ui.theme.BrandBlue
import com.sportpro.app.ui.theme.BrandBlueBright
import com.sportpro.app.ui.theme.pressScale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Modo manos libres: se crea un partido conversando. El asistente habla, escucha la respuesta
 * sola (no hay que tocar nada entre turnos), pregunta lo que falte y pide confirmación final.
 * Si no se puede hablar (ruido, prueba), todo se puede escribir o tocar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceAssistantScreen(
    session: SessionViewModel,
    onBack: () -> Unit,
    onMatchCreated: (String) -> Unit,
    onSent: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val ctx by session.context.collectAsStateWithLifecycle()
    val vm: VoiceViewModel = viewModel { VoiceViewModel(container, ctx) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val main = remember { Handler(Looper.getMainLooper()) }
    var listenAfterSpeech by remember { mutableStateOf(false) }
    var micAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    // Oído y voz. Los avisos llegan en otros hilos: se pasan al hilo principal antes de tocar nada.
    val listener = remember {
        VoiceListener(
            context,
            onReady = { main.post { vm.onListening() } },
            onPartial = { t -> main.post { vm.onPartial(t) } },
            onFinal = { t -> main.post { vm.onHeard(t) } },
            onLevel = { l -> main.post { vm.onLevel(l) } },
            onStopped = { _ -> main.post { vm.onIdle() } },
        )
    }
    val speaker = remember {
        VoiceSpeaker(context) {
            main.post {
                // Terminó de hablar: si esperaba respuesta, escucha sola (eso es lo «manos libres»)
                if (listenAfterSpeech && micAllowed && listener.available) listener.start() else vm.onIdle()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            listener.release()
            speaker.release()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        micAllowed = ok
        if (ok) listener.start()
    }
    LaunchedEffect(Unit) {
        if (!micAllowed) permission.launch(Manifest.permission.RECORD_AUDIO)
        vm.speech.collect { s ->
            listener.stop()
            listenAfterSpeech = s.listenAfter
            vm.onSpeaking()
            speaker.speak(s.text)
        }
    }
    // Al terminar, se abre el partido o la lista de desafíos/propuestas (después de que lo diga)
    LaunchedEffect(ui.result, ui.phase) {
        val r = ui.result ?: return@LaunchedEffect
        kotlinx.coroutines.delay(2600)
        when (r) {
            is VoiceResult.Match -> onMatchCreated(r.id)
            VoiceResult.Sent -> onSent()
        }
    }

    SpScaffold(
        title = stringResource(R.string.voice_title),
        navigationIcon = {
            IconButton(onClick = { speaker.stop(); listener.stop(); onBack() }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.voice_close))
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            // ---- Conversación
            val list = rememberLazyListState()
            LaunchedEffect(ui.messages.size) { if (ui.messages.isNotEmpty()) list.animateScrollToItem(ui.messages.lastIndex) }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(ui.messages) { _, m -> Bubble(m) }
                if (ui.partial.isNotBlank()) item { Bubble(VoiceMessage(true, ui.partial), live = true) }
            }
            // ---- Opciones de la pregunta actual (tocar = decirla)
            if (ui.options.isNotEmpty() && ui.phase != VoicePhase.DONE) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.options.forEach { o -> AssistChip(onClick = { listener.stop(); speaker.stop(); vm.choose(o) }, label = { Text(o.label) }) }
                }
            }
            // ---- Esfera, estado y escribir
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Orb(
                    phase = ui.phase,
                    level = ui.level,
                    onTap = {
                        when {
                            ui.phase == VoicePhase.DONE -> onBack()
                            !micAllowed -> permission.launch(Manifest.permission.RECORD_AUDIO)
                            ui.phase == VoicePhase.LISTENING -> listener.stop()
                            else -> { speaker.stop(); listener.start() }
                        }
                    },
                )
                Text(
                    statusText(ui.phase, micAllowed, listener.available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (ui.phase == VoicePhase.DONE && ui.result == null) {
                    Button(onClick = onBack) { Text(stringResource(R.string.voice_close)) }
                } else {
                    TypeInstead(enabled = ui.phase != VoicePhase.DONE) { text ->
                        listener.stop(); speaker.stop(); vm.onHeard(text)
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(phase: VoicePhase, micAllowed: Boolean, available: Boolean): String = stringResource(
    when {
        !available -> R.string.voice_status_no_recognizer
        !micAllowed -> R.string.voice_status_no_mic
        else -> when (phase) {
            VoicePhase.LOADING -> R.string.voice_status_loading
            VoicePhase.SPEAKING -> R.string.voice_status_speaking
            VoicePhase.LISTENING -> R.string.voice_status_listening
            VoicePhase.THINKING -> R.string.voice_status_thinking
            VoicePhase.IDLE -> R.string.voice_status_idle
            VoicePhase.DONE -> R.string.voice_status_done
        }
    },
)

/** Burbuja de conversación: el asistente a la izquierda (gris), la persona a la derecha (azul). */
@Composable
private fun Bubble(m: VoiceMessage, live: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Text(
            m.text,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (m.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = if (live) 0.55f else 1f) else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (m.fromUser) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Esfera del asistente: respira suave en reposo, gira al «pensar» y crece con el volumen de la voz
 * al escuchar (responde al instante, con un resorte sin rebote). Tocarla empieza o corta la escucha.
 */
@Composable
private fun Orb(phase: VoicePhase, level: Float, onTap: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "breathe")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val voice by animateFloatAsState(
        if (phase == VoicePhase.LISTENING) level else 0f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "voice",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(132.dp)
            .pressScale(interaction, pressed = 0.92f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension / 2 * 0.62f
            val pulse = (sin(2 * PI * breathe).toFloat() + 1f) / 2f
            val r = base * (1f + 0.06f * pulse + 0.32f * voice)
            // Halo: más grande y visible mientras escucha
            drawCircle(
                Brush.radialGradient(listOf(BrandBlueBright.copy(alpha = 0.35f + 0.3f * voice), Color.Transparent), center = center, radius = r * 1.6f),
                radius = r * 1.6f,
            )
            rotate(if (phase == VoicePhase.THINKING) spin else breathe * 40f) {
                drawCircle(
                    Brush.sweepGradient(listOf(BrandBlue, BrandBlueBright, Color(0xFF7DD3FC), BrandBlue), center = center),
                    radius = r,
                )
            }
            // Brillo de vidrio arriba a la izquierda
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center = Offset(center.x - r * 0.35f, center.y - r * 0.4f), radius = r * 0.7f),
                radius = r * 0.7f, center = Offset(center.x - r * 0.35f, center.y - r * 0.4f),
            )
        }
        Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.voice_talk), tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

/** Escribir en vez de hablar (ruido en la cancha, o probar sin micrófono). */
@Composable
private fun TypeInstead(enabled: Boolean, onSend: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    if (!open) {
        TextButton(onClick = { open = true }, enabled = enabled) { Text(stringResource(R.string.voice_type_instead)) }
        return
    }
    val send = { if (text.isNotBlank()) { onSend(text); text = "" } }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(300) },
        placeholder = { Text(stringResource(R.string.voice_type_hint)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = send, enabled = enabled && text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.voice_send))
            }
        },
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth(),
    )
}
