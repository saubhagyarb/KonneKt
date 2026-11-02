package com.saubh.deskbuddy.client

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.saubh.deskbuddy.protocol.Protocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** [id] and its TLS certificate fingerprint are filled from the desktop's HTTPS `/info` response. */
data class DiscoveredDesktop(
    val name: String,
    val host: String,
    val port: Int,
    val id: String = "",
    val certificateFingerprint: String = "",
)

/**
 * mDNS discovery via Android NSD. Resolves are serialised (Android rejects concurrent
 * resolves) and retried, and results are keyed by host so a PC advertising on two
 * adapters shows each reachable address.
 */
class DesktopDiscovery(context: Context, private val scanner: SubnetScanner? = null) {

    private companion object {
        const val MAX_RESOLVE_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 500L
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /** Emits the current list of resolved desktops; updates as services appear/vanish. */
    fun discover(): Flow<List<DiscoveredDesktop>> = callbackFlow {
        val lock = Any()
        val found = LinkedHashMap<String, DiscoveredDesktop>()
        val queue = ArrayDeque<Pair<NsdServiceInfo, Int>>()
        var resolving = false

        fun publish() {
            trySend(synchronized(lock) { found.values.toList() })
        }

        fun resolveNext() {
            val next = synchronized(lock) {
                if (resolving) return
                queue.removeFirstOrNull()?.also { resolving = true }
            } ?: return
            val (service, attempt) = next
            @Suppress("DEPRECATION") // registerServiceInfoCallback needs API 34; minSdk is 24
            nsdManager.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = resolved.host?.hostAddress
                        synchronized(lock) {
                            resolving = false
                            if (host != null) found[host] = DiscoveredDesktop(resolved.serviceName, host, resolved.port)
                        }
                        publish()
                        if (host != null) launch {
                            val verifiedInfo = scanner?.fetchInfo(host, resolved.port)
                            if (verifiedInfo != null) {
                                synchronized(lock) {
                                    if (found[host]?.port == resolved.port) found[host] = verifiedInfo
                                }
                                publish()
                            }
                        }
                        resolveNext()
                    }

                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        synchronized(lock) { resolving = false }
                        if (attempt < MAX_RESOLVE_ATTEMPTS) {
                            launch {
                                delay(RETRY_DELAY_MS)
                                synchronized(lock) { queue.addLast(service to attempt + 1) }
                                resolveNext()
                            }
                        } else {
                            resolveNext()
                        }
                    }
                },
            )
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                synchronized(lock) { queue.addLast(service to 1) }
                resolveNext()
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                synchronized(lock) { found.values.removeAll { it.name == service.serviceName } }
                publish()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        nsdManager.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose { runCatching { nsdManager.stopServiceDiscovery(discoveryListener) } }
    }
}
