package com.sportpro.app.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.R
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.AppText
import com.sportpro.app.core.data.UserContext
import com.sportpro.app.core.data.VoiceBrain
import com.sportpro.app.core.data.VoiceDraft
import com.sportpro.app.core.data.VoiceOption
import com.sportpro.app.core.data.VoicePath
import com.sportpro.app.core.data.VoiceStep
import com.sportpro.app.core.data.VoiceTeam
import com.sportpro.app.core.data.VoiceWorld
import com.sportpro.app.core.data.friendlyMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/** En qué está el asistente (se ve en la esfera y en el texto de estado). */
enum class VoicePhase { LOADING, SPEAKING, LISTENING, THINKING, IDLE, DONE }

data class VoiceMessage(val fromUser: Boolean, val text: String)

/** Resultado al terminar: el partido creado o el desafío/propuesta enviado. */
sealed interface VoiceResult {
    data class Match(val id: String) : VoiceResult
    data object Sent : VoiceResult
}

data class VoiceUi(
    val phase: VoicePhase = VoicePhase.LOADING,
    val messages: List<VoiceMessage> = emptyList(),
    /** Lo que se va escuchando mientras la persona habla (resultados parciales). */
    val partial: String = "",
    /** Opciones de la pregunta actual (también se pueden tocar). */
    val options: List<VoiceOption> = emptyList(),
    /** Nivel de la voz (0..1) para animar la esfera. */
    val level: Float = 0f,
    val result: VoiceResult? = null,
)

/**
 * Conversación del modo manos libres. Este ViewModel:
 *  - carga los equipos que gestiono, sus DT y las academias;
 *  - manda cada frase a la IA para entenderla y le pasa el resultado a [VoiceBrain];
 *  - emite lo que hay que DECIR por [speech] (la pantalla lo pronuncia y, al terminar, escucha);
 *  - con el «sí» final crea el partido por el mismo camino que el formulario.
 */
class VoiceViewModel(private val container: AppContainer, private val ctx: UserContext?) : ViewModel() {
    private val _ui = MutableStateFlow(VoiceUi())
    val ui: StateFlow<VoiceUi> = _ui.asStateFlow()

    /** Texto a pronunciar y si después hay que escuchar la respuesta. */
    data class Speech(val text: String, val listenAfter: Boolean)
    private val _speech = Channel<Speech>(Channel.BUFFERED)
    val speech = _speech.receiveAsFlow()

    private var world: VoiceWorld? = null
    private var draft = VoiceDraft()
    /** Última pregunta (para que la IA interprete respuestas cortas) o el resumen por confirmar. */
    private var pending: VoiceStep.Ask? = null
    private var confirmText: String? = null
    private var rivalsForHome: String? = null
    private var busy = false

    init { start() }

    private fun start() {
        viewModelScope.launch {
            try {
                val access = container.voice.access()
                if (!access.allowed) {
                    finishWith(access.reason ?: AppText.get(R.string.voice_not_available))
                    return@launch
                }
                val coaches = runCatching { container.challenges.managedTeamCoaches() }.getOrDefault(emptyList()).associateBy { it.teamId }
                val teams = ctx?.managedTeams.orEmpty().map { t ->
                    VoiceTeam(t.id, t.name, t.category, null, coaches[t.id]?.coachName, t.defaultFormatPlayers, t.halfMinutes)
                }
                if (teams.isEmpty()) {
                    finishWith(AppText.get(R.string.voice_no_teams))
                    return@launch
                }
                world = VoiceWorld(
                    myTeams = teams,
                    staffTeamIds = ctx?.staffTeamIds.orEmpty(),
                    academies = container.club.visibleAcademies(),
                )
                say(AppText.get(R.string.voice_greeting), listen = true)
            } catch (e: Exception) {
                finishWith(e.friendlyMessage())
            }
        }
    }

    // ------------------------------------------------------------------ eventos de la pantalla

    fun onListening() = _ui.update { it.copy(phase = VoicePhase.LISTENING, partial = "") }
    fun onPartial(text: String) = _ui.update { it.copy(partial = text) }
    fun onLevel(level: Float) = _ui.update { it.copy(level = level) }
    fun onSpeaking() = _ui.update { it.copy(phase = VoicePhase.SPEAKING) }
    fun onIdle() = _ui.update { if (it.phase != VoicePhase.DONE) it.copy(phase = VoicePhase.IDLE, partial = "", level = 0f) else it }

    /** Se tocó una opción en pantalla: es como decirla. */
    fun choose(option: VoiceOption) = onHeard(option.label)

    /** La persona dijo (o escribió) algo. */
    fun onHeard(raw: String) {
        val heard = raw.trim()
        if (heard.isEmpty() || busy || _ui.value.phase == VoicePhase.DONE) return
        addMessage(VoiceMessage(fromUser = true, text = heard))
        _ui.update { it.copy(partial = "", options = emptyList(), phase = VoicePhase.THINKING) }
        if (VoiceBrain.isCancel(heard)) {
            finishWith(AppText.get(R.string.voice_cancelled))
            return
        }
        // En la confirmación, un «sí» o «no» claro no necesita a la IA
        if (confirmText != null) {
            when (VoiceBrain.quickYesNo(heard)) {
                true -> { create(); return }
                false -> { confirmText = null; say(AppText.get(R.string.voice_what_change), listen = true); return }
                null -> Unit
            }
        }
        busy = true
        viewModelScope.launch {
            try {
                val question = confirmText ?: pending?.text
                val p = container.voice.parse(heard, question, pending?.options.orEmpty().map { it.label })
                busy = false
                if (p.error != null) {
                    say(p.error, listen = true)
                    return@launch
                }
                when (p.intent) {
                    "cancel" -> { finishWith(AppText.get(R.string.voice_cancelled)); return@launch }
                    "confirm_yes" -> if (confirmText != null) { create(); return@launch }
                    "confirm_no" -> if (confirmText != null) {
                        confirmText = null
                        say(AppText.get(R.string.voice_what_change), listen = true)
                        return@launch
                    }
                }
                draft = VoiceBrain.merge(draft, p, pending, heard)
                confirmText = null
                advance()
            } catch (e: Exception) {
                busy = false
                say(AppText.get(R.string.voice_error_retry, e.friendlyMessage()), listen = true)
            }
        }
    }

    // ------------------------------------------------------------------ conversación

    /** Resuelve lo que se pueda; si faltan rivales los trae; y dice la siguiente pregunta o el resumen. */
    private suspend fun advance() {
        var w = world ?: return
        repeat(6) {   // como mucho unas pocas cargas de rivales seguidas
            // Si cambió mi equipo, los rivales posibles (por división) son otros
            if (draft.homeTeamId != rivalsForHome) {
                w = w.copy(rivals = emptyMap())
                rivalsForHome = draft.homeTeamId
            }
            val (d, step) = VoiceBrain.next(draft, w, LocalDateTime.now(ZoneId.of("America/Lima")))
            draft = d
            when (step) {
                is VoiceStep.LoadRivals -> {
                    val home = draft.homeTeamId ?: return
                    val loaded = step.academyIds.associateWith { academy ->
                        runCatching { container.challenges.rivalTeams(home, academy) }.getOrDefault(emptyList())
                    }
                    w = w.copy(rivals = w.rivals + loaded)
                    world = w
                }
                is VoiceStep.Ask -> {
                    world = w
                    pending = step
                    _ui.update { it.copy(options = step.options) }
                    say(step.text, listen = true)
                    return
                }
                is VoiceStep.Confirm -> {
                    world = w
                    pending = null
                    confirmText = step.text
                    _ui.update { it.copy(options = listOf(VoiceOption(AppText.get(R.string.voice_yes), "yes"), VoiceOption(AppText.get(R.string.voice_no), "no"))) }
                    say(step.text, listen = true)
                    return
                }
            }
        }
    }

    /** Crea el partido (o envía el desafío / la propuesta) con lo confirmado. */
    private fun create() {
        val w = world ?: return
        val d = draft
        busy = true
        _ui.update { it.copy(phase = VoicePhase.THINKING, options = emptyList()) }
        viewModelScope.launch {
            try {
                val start = LocalDateTime.of(d.date, d.time).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
                val venue = d.venue?.takeIf { it.isNotBlank() }
                val path = VoiceBrain.route(d, w)
                val id = if (path == VoicePath.DIRECT) {
                    container.matches.createMatch(
                        homeTeamId = d.homeTeamId!!, awayTeamId = d.awayTeamId, awayExternalName = null,
                        formatPlayers = d.format!!, halfMinutes = d.minutes!!, scheduledAtIso = start,
                        venue = venue, visibility = "followers", externalRoster = emptyList(),
                    )
                } else {
                    container.challenges.create(d.homeTeamId!!, d.awayTeamId!!, d.format!!, d.minutes!!, start, venue, "followers")
                }
                // 1 uso de IA por partido creado por voz (si falla, el partido igual ya existe)
                runCatching { container.voice.recordUsage(if (path == VoicePath.DIRECT) id else null) }
                busy = false
                val result = if (path == VoicePath.DIRECT) VoiceResult.Match(id) else VoiceResult.Sent
                val done = when (path) {
                    VoicePath.DIRECT -> AppText.get(R.string.voice_done_direct)
                    VoicePath.CHALLENGE -> AppText.get(R.string.voice_done_challenge)
                    VoicePath.PROPOSAL -> AppText.get(R.string.voice_done_proposal)
                }
                addMessage(VoiceMessage(false, done))
                _ui.update { it.copy(phase = VoicePhase.DONE, result = result) }
                _speech.trySend(Speech(done, listenAfter = false))
            } catch (e: Exception) {
                busy = false
                confirmText = null
                // El servidor rechazó algo (por ejemplo, ya hay un desafío pendiente): se explica y se sigue
                say(AppText.get(R.string.voice_create_failed, e.friendlyMessage()), listen = true)
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    private fun say(text: String, listen: Boolean) {
        addMessage(VoiceMessage(fromUser = false, text = text))
        _speech.trySend(Speech(text, listen))
    }

    private fun finishWith(text: String) {
        addMessage(VoiceMessage(fromUser = false, text = text))
        _ui.update { it.copy(phase = VoicePhase.DONE, options = emptyList()) }
        _speech.trySend(Speech(text, listenAfter = false))
    }

    private fun addMessage(m: VoiceMessage) = _ui.update { it.copy(messages = it.messages + m) }

}
