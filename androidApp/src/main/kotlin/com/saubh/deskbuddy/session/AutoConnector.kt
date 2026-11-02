package com.saubh.deskbuddy.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Keeps the phone connected to a paired PC with no user action: while nothing is connected it
 * quietly probes each paired PC's port and connects to the first that answers — e.g. a laptop
 * that just finished booting. Runs every [AutoConnectBackoff.currentMs] (10 s, doubling to 5 min
 * while no PC answers), at once when a network comes up or the connection drops, and only probes
 * loopback targets (USB) when the phone is off Wi-Fi/Ethernet.
 */
class AutoConnector(
    private val session: RemoteSession,
    private val scope: CoroutineScope,
    private val probe: suspend (host: String, port: Int) -> Boolean = ::portOpen,
    private val backoff: AutoConnectBackoff = AutoConnectBackoff(),
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start(context: Context) {
        if (job != null) return
        connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        session.startDiscovery()
        registerNetworkCallback()
        // A dropped connection should be retried now, not after a grown interval.
        scope.launch { session.connected.drop(1).collect { if (!it) nudge() } }
        // A desktop can come back on a new DHCP address after reboot; don't wait for the backoff
        // timer once discovery has found its new address.
        scope.launch { session.discovered.drop(1).collect { if (it.isNotEmpty()) nudge() } }
        job = scope.launch {
            while (true) {
                val missed = attempt()
                val woke = withTimeoutOrNull(backoff.currentMs) { wake.receive() } != null
                if (woke || !missed) backoff.reset() else backoff.grow()
            }
        }
    }

    @Volatile
    private var userEnabled = true

    fun setUserEnabled(enabled: Boolean) {
        userEnabled = enabled
        if (enabled) nudge()
    }

    /** Try now instead of waiting for the next tick. */
    fun nudge() {
        wake.trySend(Unit)
    }

    /** True when it probed at least one paired PC and none answered. */
    private suspend fun attempt(): Boolean {
        if (!userEnabled || !session.isSearching || session.autoConnectPaused) return false
        val all = AutoConnectPlan.candidates(session.savedDesktops.value, session.discovered.value)
        val onLan = isOnLan()
        val candidates = AutoConnectPlan.reachable(all, onLan, if (onLan) emptyList() else localSubnets())
        if (candidates.isEmpty()) return false
        for (candidate in candidates) {
            if (probe(candidate.host, candidate.port)) {
                // State may have changed while probing (the user tapped Connect).
                if (session.isSearching && !session.autoConnectPaused) session.connect(candidate, quiet = true)
                return false
            }
        }
        return true
    }

    private fun isOnLan(): Boolean {
        val cm = connectivity ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** The phone's own IPv4 subnets (hotspot, USB tether): a PC there is reachable although the default network is cellular. */
    private fun localSubnets(): List<Ipv4Subnet> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses.asSequence() }
            .filter { it.address is Inet4Address }
            .mapNotNull { address -> address.address.hostAddress?.let { Ipv4Subnet.of(it, address.networkPrefixLength.toInt()) } }
            .toList()
    }.getOrDefault(emptyList())

    private fun registerNetworkCallback() {
        val cm = connectivity ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = nudge()
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 1_500

        suspend fun portOpen(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
            runCatching {
                Socket().use {
                    it.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
                    true
                }
            }.getOrDefault(false)
        }
    }
}
