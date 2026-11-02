package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.Protocol
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/** Advertises the control server on the LAN via mDNS so Android NSD can find it. */
class DeskBuddyAdvertiser(private val port: Int = Protocol.PORT) {

    private val instances = linkedMapOf<String, JmDNS>()
    private var refreshExecutor: ScheduledExecutorService? = null

    /** One JmDNS per LAN address so the phone hears us whichever adapter it shares. */
    @Synchronized
    fun start() {
        refresh()
        if (refreshExecutor != null) return
        refreshExecutor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "KonneKt network advertiser").apply { isDaemon = true }
        }.also { executor ->
            // Login items can start before Wi-Fi is ready. Rebind when interfaces appear or change.
            executor.scheduleWithFixedDelay(
                Runnable { refreshSafely() },
                REFRESH_INTERVAL_SECONDS,
                REFRESH_INTERVAL_SECONDS,
                TimeUnit.SECONDS,
            )
        }
    }

    /** Reconciles mDNS sockets with the current LAN interfaces, retrying interfaces that failed before. */
    @Synchronized
    fun refresh() {
        val name = LanInterfaces.hostName()
        val addresses = LanInterfaces.current()
            .ifEmpty { runCatching { InetAddress.getLocalHost() }.getOrNull()?.let(::listOf).orEmpty() }
            .associateBy { it.hostAddress }

        (instances.keys - addresses.keys).forEach { address ->
            instances.remove(address)?.let(::closeQuietly)
        }

        for ((address, inetAddress) in addresses) {
            if (address in instances) continue
            runCatching {
                val mdns = JmDNS.create(inetAddress, name)
                try {
                    mdns.registerService(
                        ServiceInfo.create(Protocol.SERVICE_TYPE + "local.", name, port, "KonneKt control server"),
                    )
                    instances[address] = mdns
                } catch (error: Throwable) {
                    closeQuietly(mdns)
                    throw error
                }
            }
        }
    }

    private fun refreshSafely() {
        runCatching { refresh() }
    }

    @Synchronized
    fun stop() {
        refreshExecutor?.shutdownNow()
        refreshExecutor = null
        instances.values.forEach(::closeQuietly)
        instances.clear()
    }

    private fun closeQuietly(jmdns: JmDNS) {
        runCatching {
            jmdns.unregisterAllServices()
            jmdns.close()
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_SECONDS = 10L
    }
}
