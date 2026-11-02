package com.saubh.deskbuddy.client

import android.content.Context
import android.net.ConnectivityManager
import com.saubh.deskbuddy.discovery.SubnetHosts
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Fallback discovery when mDNS is silent: TCP-probe every host in the phone's /24 on the
 * DeskBuddy port and confirm with `GET /info`. Same path manual entry uses, so it works
 * wherever manual entry works.
 */
class SubnetScanner(
    context: Context,
    private val port: Int = Protocol.PORT,
    private val http: HttpClient = infoHttpClient(),
) {
    private companion object {
        const val PARALLELISM = 48
        const val CONNECT_TIMEOUT_MS = 400
        const val INFO_TIMEOUT_MS = 1_000L
        const val MAX_INFO_RESPONSE_BYTES = 4 * 1024

        fun infoHttpClient(): HttpClient = HttpClient(CIO) {
            engine {
                requestTimeout = 2_000
                https {
                    // /info contains no secret. Its fingerprint is verified against the desktop UI,
                    // then the WebSocket client pins the actual certificate before sending credentials.
                    trustManager = object : X509TrustManager {
                        override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
                        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
                        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
                    }
                }
            }
        }
    }

    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Phone's IPv4 address and prefix length on the active network, or null when offline. */
    fun localAddress(): Pair<String, Int>? {
        val network = connectivity.activeNetwork ?: return null
        val link = connectivity.getLinkProperties(network)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address } ?: return null
        val host = link.address.hostAddress ?: return null
        return host to link.prefixLength
    }

    suspend fun scan(): List<DiscoveredDesktop> = withContext(Dispatchers.IO) {
        val (ip, prefix) = localAddress() ?: return@withContext emptyList()
        val gate = Semaphore(PARALLELISM)
        coroutineScope {
            SubnetHosts.candidates(ip, prefix)
                .map { host -> async { gate.withPermit { probe(host) } } }
                .awaitAll()
                .filterNotNull()
        }
    }

    private suspend fun probe(host: String): DiscoveredDesktop? {
        if (!portOpen(host)) return null
        return fetchInfo(host, port)
    }

    /** Reads the public TLS identity before any pairing PIN or saved token is sent. */
    suspend fun fetchInfo(host: String, requestedPort: Int = port): DiscoveredDesktop? = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(INFO_TIMEOUT_MS) {
                val uriHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
                val response = http.get("https://$uriHost:$requestedPort/info")
                val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                val channel = response.bodyAsChannel()
                if (declaredLength != null && declaredLength > MAX_INFO_RESPONSE_BYTES) {
                    channel.cancel()
                    return@withTimeout null
                }
                val bytes = ByteArray(MAX_INFO_RESPONSE_BYTES + 1)
                var total = 0
                while (total < bytes.size) {
                    val read = channel.readAvailable(bytes, total, bytes.size - total)
                    if (read < 0) break
                    total += read
                }
                if (total > MAX_INFO_RESPONSE_BYTES) {
                    channel.cancel()
                    return@withTimeout null
                }
                val info = WireCodec.decodeInfo(bytes.copyOf(total).toString(Charsets.UTF_8))
                if (info.port != requestedPort || info.name.length > 256 || info.id.length > 128) return@withTimeout null
                val fingerprint = info.certificateFingerprint.replace(":", "").uppercase()
                if (fingerprint.length != 64 || fingerprint.any { it !in '0'..'9' && it !in 'A'..'F' }) return@withTimeout null
                DiscoveredDesktop(info.name, host, info.port, info.id, fingerprint)
            }
        }.getOrNull()
    }

    private fun portOpen(host: String): Boolean = runCatching {
        Socket().use {
            it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            true
        }
    }.getOrDefault(false)

}
