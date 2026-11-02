package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopInfo
import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineSSLConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.Semaphore
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WebSocket control server. Several phones can be connected at once; desktop-initiated
 * messages go to every authenticated phone. Only one PIN pairing runs at a time.
 */
class ControlServer(
    private val store: PairedDeviceStore,
    private val actuators: Actuators,
    private val port: Int = Protocol.PORT,
    private val desktopName: String = LanInterfaces.hostName(),
    private val desktopId: String = "",
    private val tlsIdentity: DesktopTlsIdentity = DesktopTlsIdentity(),
    /** A second copy of the app asks the running one (over loopback) to show its window. */
    private val onShowRequested: () -> Unit = {},
    /** A connection finished authenticating (a new phone, or the same phone on a fresh socket). */
    private val onPhoneAuthenticated: (deviceName: String) -> Unit = {},
) {
    private companion object {
        const val PUSH_TIMEOUT_MS = 5_000L
        const val MAX_CONNECTIONS = 16
        const val MAX_HTTP_REQUESTS = 32
        const val MAX_TEXT_FRAME_CHARS = 512 * 1024
        const val PAIR_FAILURE_WINDOW_MS = 60_000L
        const val PAIR_LOCKOUT_MS = 60_000L
        const val MAX_PAIR_FAILURE_SOURCES = 1024
    }

    private data class PairFailureState(var failures: Int, var windowStartedAt: Long, var blockedUntil: Long = 0)

    private class Connection(
        val socket: DefaultWebSocketServerSession,
        val outbound: Channel<Message>,
        val remoteHost: String,
    ) {
        @Volatile var deviceName: String? = null
        @Volatile var token: String? = null
    }

    private val _state = MutableStateFlow<ServerState>(ServerState.Waiting)
    val state: StateFlow<ServerState> = _state

    private val connections = CopyOnWriteArrayList<Connection>()
    private val connectionPermits = Semaphore(MAX_CONNECTIONS)
    private val httpRequestPermits = Semaphore(MAX_HTTP_REQUESTS)
    private val pairingFailures = LinkedHashMap<String, PairFailureState>(16, 0.75f, true)
    private val lock = Any()
    private var pairingPin: String? = null
    private var pairingOwner: Connection? = null
    private var pairingGeneration = 0L
    private var engine: EmbeddedServer<*, *>? = null

    /**
     * Sends a desktop-initiated message to every authenticated phone. Returns false when none
     * received it. Suspends while a phone's buffer is full (file transfer backpressure), but a
     * phone that stops reading is skipped after [PUSH_TIMEOUT_MS] so it cannot stall the others.
     */
    suspend fun push(message: Message): Boolean {
        var delivered = false
        for (connection in connections) {
            if (connection.deviceName == null) continue
            val sent = withTimeoutOrNull(PUSH_TIMEOUT_MS) { runCatching { connection.outbound.send(message) }.isSuccess }
            if (sent == true) delivered = true
        }
        return delivered
    }

    fun start() {
        val tlsConnector = EngineSSLConnectorBuilder(
            keyStore = tlsIdentity.keyStore,
            keyAlias = DesktopTlsIdentity.ALIAS,
            keyStorePassword = { tlsIdentity.password },
            privateKeyPassword = { tlsIdentity.password },
        ).apply {
            host = "0.0.0.0"
            this.port = port
        }
        engine = embeddedServer(
            factory = Netty,
            environment = applicationEnvironment(),
            configure = { connectors.add(tlsConnector) },
        ) {
            install(WebSockets) {
                pingPeriodMillis = 15_000
                timeoutMillis = 30_000
                maxFrameSize = MAX_TEXT_FRAME_CHARS.toLong()
            }
            routing {
                // Lets the phone's subnet sweep confirm an open port is DeskBuddy and learn our name.
                get("/info") {
                    if (!httpRequestPermits.tryAcquire()) {
                        call.respond(HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    try {
                        call.respondText(
                            WireCodec.encodeInfo(DesktopInfo(desktopName, port, desktopId, tlsIdentity.fingerprint)),
                            ContentType.Application.Json,
                        )
                    } finally {
                        httpRequestPermits.release()
                    }
                }
                get("/show") {
                    if (!httpRequestPermits.tryAcquire()) {
                        call.respond(HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                    val local = runCatching { InetAddress.getByName(call.request.local.remoteAddress).isLoopbackAddress }.getOrDefault(false)
                    try {
                        if (local) onShowRequested()
                        call.respond(if (local) HttpStatusCode.NoContent else HttpStatusCode.Forbidden)
                    } finally {
                        httpRequestPermits.release()
                    }
                }
                webSocket(Protocol.PATH) {
                    if (!connectionPermits.tryAcquire()) {
                        close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "connection limit reached"))
                        return@webSocket
                    }
                    // Single writer per phone: replies and pushes both go through this channel.
                    val connection = Connection(this, Channel(16), call.request.local.remoteAddress)
                    connections += connection
                    val session = ControlSession(store, actuators, listenerFor(connection), desktopName = desktopName, desktopId = desktopId)
                    val writer = launch {
                        for (message in connection.outbound) send(Frame.Text(WireCodec.encode(message)))
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            if (text.length > MAX_TEXT_FRAME_CHARS) {
                                connection.outbound.close()
                                close(CloseReason(CloseReason.Codes.TOO_BIG, "message too large"))
                                break
                            }
                            val reply = runCatching { session.onMessage(WireCodec.decode(text)) }
                                .getOrElse { e ->
                                    ServerLog.write(undecodableFrameLog(e, text.length))
                                    SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL))
                                }
                            reply.message?.let { connection.outbound.send(it) }
                            if (reply.closeConnection) {
                                connection.outbound.close()
                                writer.join()
                                close(CloseReason(CloseReason.Codes.NORMAL, "closed"))
                                break
                            }
                        }
                    } finally {
                        connections -= connection
                        connectionPermits.release()
                        connection.outbound.close()
                        writer.cancel()
                        synchronized(lock) {
                            if (pairingOwner === connection) {
                                pairingOwner = null
                                pairingPin = null
                                pairingGeneration++
                            }
                        }
                        publish()
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        engine = null
        connections.clear()
        _state.value = ServerState.Waiting
    }

    private fun listenerFor(connection: Connection) = object : SessionListener {
        override fun canStartPairing(): Boolean = synchronized(lock) {
            val now = System.currentTimeMillis()
            val current = pairingFailures[connection.remoteHost]
            if (pairingOwner != null && pairingOwner !== connection) return@synchronized false
            if (current != null && current.blockedUntil > now) return@synchronized false
            pairingOwner = connection // reserve the single pairing slot atomically
            true
        }

        override fun onPairingStarted(pin: String) {
            val generation = synchronized(lock) {
                pairingPin = pin
                pairingOwner = connection
                ++pairingGeneration
            }
            publish()
            connection.socket.launch {
                delay(Protocol.PAIRING_TIMEOUT_MILLIS)
                val expired = synchronized(lock) {
                    if (pairingOwner === connection && pairingGeneration == generation) {
                        pairingOwner = null
                        pairingPin = null
                        pairingGeneration++
                        true
                    } else false
                }
                if (expired) {
                    publish()
                    connection.socket.close(CloseReason(CloseReason.Codes.NORMAL, "pairing expired"))
                }
            }
        }

        override fun onPairingEnded() {
            synchronized(lock) {
                if (pairingOwner === connection) {
                    pairingPin = null
                    pairingOwner = null
                    pairingGeneration++
                }
            }
            publish()
        }

        override fun onAuthenticated(deviceName: String, token: String) {
            if (connection.token == token) return
            connection.deviceName = deviceName
            connection.token = token
            // The same phone reconnected (e.g. after Wi-Fi dropped); retire its stale socket.
            connections.filter { it !== connection && it.token == token }.forEach { stale ->
                stale.deviceName = null
                stale.socket.launch { stale.socket.close(CloseReason(CloseReason.Codes.NORMAL, "replaced")) }
            }
            publish()
            onPhoneAuthenticated(deviceName)
        }

        override fun onPairingFailed() {
            synchronized(lock) {
                val now = System.currentTimeMillis()
                val previous = pairingFailures[connection.remoteHost]
                val state = if (previous == null || now - previous.windowStartedAt > PAIR_FAILURE_WINDOW_MS) {
                    PairFailureState(0, now)
                } else previous
                state.failures++
                if (state.failures >= 3) {
                    state.failures = 0
                    state.windowStartedAt = now
                    state.blockedUntil = now + PAIR_LOCKOUT_MS
                }
                pairingFailures[connection.remoteHost] = state
                while (pairingFailures.size > MAX_PAIR_FAILURE_SOURCES) {
                    pairingFailures.remove(pairingFailures.keys.first())
                }
            }
        }

        override fun onPairingSucceeded() {
            synchronized(lock) { pairingFailures.remove(connection.remoteHost) }
        }
    }

    private fun publish() {
        val pin = synchronized(lock) { pairingPin }
        val devices = connections.mapNotNull { it.deviceName }
        _state.value = when {
            pin != null -> ServerState.Pairing(pin)
            devices.isNotEmpty() -> ServerState.Connected(devices)
            else -> ServerState.Waiting
        }
    }
}

/** Decode errors echo the JSON input, which carries the pairing token: log only the error type and frame size. */
internal fun undecodableFrameLog(error: Throwable, frameLength: Int): String =
    "Undecodable frame: ${error::class.simpleName} (${frameLength} chars)"
