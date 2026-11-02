package com.saubh.deskbuddy

import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.server.DesktopTlsIdentity
import java.security.SecureRandom
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * Detects a copy of DeskBuddy already running on this PC (it owns the control port) and,
 * unless [show] is false, asks it over loopback to bring its window forward.
 */
object SingleInstance {
    private const val TIMEOUT_MS = 1_500

    /** True when another instance answered; the caller should then exit. */
    fun handOffToRunningInstance(show: Boolean): Boolean = runCatching {
        val path = if (show) "/show" else "/info"
        val identity = DesktopTlsIdentity()
        val connection = URI("https://127.0.0.1:${Protocol.PORT}$path").toURL().openConnection() as HttpsURLConnection
        connection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(identity.localTrustManager()), SecureRandom())
        }.socketFactory
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)
}
