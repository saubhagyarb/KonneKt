package com.saubh.deskbuddy.client

import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import javax.net.ssl.X509TrustManager
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.MessageDigest
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class DeskBuddyClient {

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
    }

    @Volatile
    private var http: HttpClient? = null
    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    /**
     * Opens the socket. Collect the returned flow to receive messages; it
     * completes (or throws) when the connection closes. Throws [IOException] when the handshake
     * does not finish in [HANDSHAKE_TIMEOUT_MS]: a socket lost mid-handshake (e.g. the phone cut the
     * app's traffic in the background) would otherwise leave the caller "Connecting" forever.
     */
    suspend fun connect(host: String, port: Int = Protocol.PORT, expectedFingerprint: String): Flow<Message> {
        val expected = normalizeFingerprint(expectedFingerprint)
        if (expected.length != 64 || expected.any { it !in '0'..'9' && it !in 'A'..'F' }) {
            throw IOException("No trusted certificate fingerprint for $host:$port")
        }
        val client = HttpClient(CIO) {
            install(WebSockets) {
                // Notices a PC that vanished without closing the socket (sleep, power loss, Wi-Fi drop).
                pingIntervalMillis = 10_000
            }
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            val certificate = chain?.firstOrNull() ?: throw CertificateException("Missing desktop certificate")
                            val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                                .joinToString("") { "%02X".format(it) }
                            if (actual != expected) throw CertificateException("Desktop certificate fingerprint changed")
                        }
                    }
                }
            }
        }
        http = client
        val uriHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
        val ws = try {
            withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) {
                client.webSocketSession(urlString = "wss://$uriHost:$port${Protocol.PATH}")
            } ?: throw IOException("No handshake from $host:$port within $HANDSHAKE_TIMEOUT_MS ms")
        } catch (error: Throwable) {
            client.close()
            if (http === client) http = null
            throw error
        }
        session = ws
        return flow {
            for (frame in ws.incoming) {
                if (frame is Frame.Text) WireCodec.decodeOrNull(frame.readText())?.let { emit(it) }
            }
        }.onCompletion {
            if (session === ws) session = null
            if (http === client) http = null
            client.close()
        }
    }

    suspend fun send(message: Message) {
        session?.send(Frame.Text(WireCodec.encode(message)))
    }

    suspend fun disconnect() {
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        session = null
        http?.close()
        http = null
    }

    private fun normalizeFingerprint(value: String): String = value.filter(Char::isLetterOrDigit).uppercase()
}
