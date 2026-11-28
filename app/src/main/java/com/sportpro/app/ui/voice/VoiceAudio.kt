package com.sportpro.app.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Oído del asistente: el reconocedor de voz de Android en español de Perú. Muestra lo que va
 * entendiendo (parciales) y entrega la frase final. El audio lo procesa el sistema: la app solo
 * recibe el TEXTO.
 */
class VoiceListener(
    context: Context,
    private val onReady: () -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    /** Silencio o error: [noMatch] = no se entendió nada (se puede volver a escuchar). */
    private val onStopped: (noMatch: Boolean) -> Unit,
) {
    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    val available: Boolean get() = recognizer != null

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onReady()
            override fun onBeginningOfSpeech() {}
            // dB aproximados (−2 a 10) → 0..1 para animar la esfera
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) = onStopped(
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            )
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onStopped(true) else onFinal(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun start() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Un poco más de paciencia antes de dar por terminada la frase (se piensa mientras se habla)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        recognizer?.startListening(intent)
    }

    fun stop() = recognizer?.cancel()
    fun release() = recognizer?.destroy()
}

/**
 * Voz del asistente: el lector de texto del celular en español (Perú si existe; si no, Latinoamérica
 * o España). Avisa cuando termina de hablar para volver a escuchar.
 */
class VoiceSpeaker(context: Context, private val onDone: () -> Unit) {
    private var ready = false
    private val queue = mutableListOf<String>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            setup()
            queue.forEach(::speakNow)
            queue.clear()
        }
    }

    private fun setup() {
        val preferred = listOf(Locale.forLanguageTag("es-PE"), Locale.forLanguageTag("es-US"), Locale.forLanguageTag("es-MX"), Locale.forLanguageTag("es-ES"))
        preferred.firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }?.let { tts.language = it }
        // Si hay una voz de buena calidad en ese idioma (no la «robótica»), se usa
        runCatching {
            tts.voices?.filter { it.locale.language == "es" && !it.isNetworkConnectionRequired }
                ?.maxByOrNull { it.quality - (if (it.locale.country == "PE" || it.locale.country == "US") 0 else 50) }
                ?.let { v: Voice -> tts.voice = v }
        }
        tts.setSpeechRate(1.05f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = onDone()
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) = onDone()
            override fun onError(utteranceId: String?, errorCode: Int) = onDone()
        })
    }

    fun speak(text: String) = if (ready) speakNow(text) else { queue += text }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sp-" + System.nanoTime())
    }

    fun stop() = tts.stop()
    fun release() = tts.shutdown()
}
