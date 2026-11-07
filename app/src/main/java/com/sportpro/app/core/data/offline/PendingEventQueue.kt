package com.sportpro.app.core.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.sportpro.app.core.data.AppJson
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.core.data.isDuplicate
import com.sportpro.app.core.data.isServerAnswer
import com.sportpro.app.core.data.repo.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import java.io.File

// Evento guardado en el celular a la espera de enviarse (body = fila lista para match_events).
@Serializable
data class PendingEvent(
    val clientEventId: String,
    val matchId: String,
    val label: String,
    val body: JsonObject,
    val createdAtMs: Long,
)

// Resultado de enviar: Sent (ya está en el servidor), Queued (quedó en el celular),
// Rejected (el servidor lo rechazó por una regla, con el motivo).
sealed interface SubmitResult {
    data class Sent(val eventId: String) : SubmitResult
    data object Queued : SubmitResult
    data class Rejected(val message: String) : SubmitResult
}

 /**
  * COLA DE EVENTOS SIN CONEXIÓN (reto «pérdida de conexión en la cancha»).
  *
  * Lógica:
  * 1. Cada evento lleva un client_event_id (UUID) creado en el celular.
  * 2. submit(): con internet se envía al instante. Si la red falla, el evento se guarda en
  *    pending_events.json dentro del celular (sobrevive aunque se cierre la app).
  * 3. Reintento automático: cuando Android avisa que volvió la red y, además, cada 8 segundos.
  * 4. Los pendientes se envían en el orden en que se registraron (createdAtMs).
  * 5. Si el servidor responde «duplicado» (23505), ya se había guardado: se quita de la cola y
  *    NUNCA se duplica.
  * 6. Si el servidor RECHAZA el evento (regla de negocio), se descarta y se avisa al usuario;
  *    si es un error de red, queda en la cola y se corta el envío hasta el próximo intento.
  * Como el minuto se elige al registrar, un evento que llega tarde cae en su lugar en la cronología.
  */
class PendingEventQueue(
    context: Context,
    private val repo: MatchRepository,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "pending_events.json")
    private val listMutex = Mutex()
    private val flushMutex = Mutex()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<PendingEvent>> = _items.asStateFlow()

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _sent = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Emits the matchId whenever a queued event reaches the server. */
    val sent: SharedFlow<String> = _sent.asSharedFlow()

    init {
        // Estado inicial de la red y aviso de Android cuando la conexión vuelve o se pierde.
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active: Network? = cm?.activeNetwork
        val caps: NetworkCapabilities? = if (cm != null && active != null) cm.getNetworkCapabilities(active) else null
        _online.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _online.value = true
                    flush()
                }

                override fun onLost(network: Network) {
                    _online.value = false
                }
            })
        }
        scope.launch {
            // Reintento periódico mientras haya pendientes (por si el aviso de red no llega).
            while (true) {
                if (_items.value.isNotEmpty()) flushNow()
                delay(RETRY_MS)
            }
        }
    }

    /**
     * Intenta enviar YA. Casos:
     * - OK → Sent(id).
     * - Duplicado → ya estaba guardado: se busca su id y se devuelve Sent.
     * - Respuesta del servidor (regla incumplida) → Rejected con un mensaje entendible.
     * - Cualquier otro error (red) → se guarda en la cola persistente → Queued.
     */
    suspend fun submit(event: PendingEvent): SubmitResult {
        if (_online.value) {
            try {
                return SubmitResult.Sent(repo.insertEvent(event.body))
            } catch (e: Exception) {
                if (e.isDuplicate()) {
                    val id = runCatching { repo.eventIdByClientId(event.matchId, event.clientEventId) }.getOrNull()
                    return if (id != null) SubmitResult.Sent(id) else SubmitResult.Queued
                }
                if (e.isServerAnswer()) return SubmitResult.Rejected(e.friendlyMessage())
            }
        }
        listMutex.withLock {
            // Sin red: se agrega a la cola y se guarda en disco.
            _items.value = _items.value + event
            persist()
        }
        return SubmitResult.Queued
    }

    /** «Deshacer» de un evento que aún no salió del celular: simplemente se quita de la cola. */
    suspend fun removePending(clientEventId: String): Boolean = listMutex.withLock {
        val before = _items.value.size
        _items.value = _items.value.filterNot { it.clientEventId == clientEventId }
        persist()
        _items.value.size < before
    }

    fun pendingFor(matchId: String): List<PendingEvent> = _items.value.filter { it.matchId == matchId }

    fun flush() {
        scope.launch { flushNow() }
    }

    /**
     * Envía todos los pendientes en orden. tryLock evita dos envíos a la vez (aviso de red + reintento).
     * DONE = llegó (o ya estaba); DROP = el servidor lo rechazó; NETWORK = sin red → se detiene.
     */
    suspend fun flushNow() {
        if (!flushMutex.tryLock()) return
        try {
            for (event in _items.value.sortedBy { it.createdAtMs }) {
                val outcome = try {
                    repo.insertEvent(event.body)
                    Outcome.DONE
                } catch (e: Exception) {
                    when {
                        e.isDuplicate() -> Outcome.DONE
                        e.isServerAnswer() -> {
                            _errors.tryEmit("No se pudo guardar «${event.label}»: ${e.friendlyMessage()}")
                            Outcome.DROP
                        }
                        else -> Outcome.NETWORK
                    }
                }
                if (outcome == Outcome.NETWORK) {
                    _online.value = false
                    break
                }
                listMutex.withLock {
                    _items.value = _items.value.filterNot { it.clientEventId == event.clientEventId }
                    persist()
                }
                _online.value = true
                if (outcome == Outcome.DONE) _sent.tryEmit(event.matchId)
            }
        } finally {
            flushMutex.unlock()
        }
    }

    private enum class Outcome { DONE, DROP, NETWORK }

    private fun load(): List<PendingEvent> = runCatching {
        if (file.exists()) AppJson.decodeFromString<List<PendingEvent>>(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    // Escritura atómica: primero a un .tmp y luego se renombra, para no corromper la cola
    // si la app se cierra a mitad de la escritura.
    private fun persist() {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(_items.value))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    private companion object {
        const val RETRY_MS = 8_000L
    }
}
