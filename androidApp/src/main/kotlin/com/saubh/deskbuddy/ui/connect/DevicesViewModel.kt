package com.saubh.deskbuddy.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.session.AutoConnector
import com.saubh.deskbuddy.session.RemoteSession
import com.saubh.deskbuddy.ui.ConnectionUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Finding, pairing and reconnecting PCs. */
class DevicesViewModel(
    private val session: RemoteSession,
    private val autoConnector: AutoConnector,
) : ViewModel() {

    val uiState: StateFlow<DevicesUiState> =
        combine(session.uiState, session.savedDesktops, session.discovered) { state, saved, discovered ->
            val ordered = saved.sortedByDescending { it.lastUsedAt }
            DevicesUiState(
                saved = ordered,
                nearby = discovered.filterNot { d -> ordered.any { it.host == d.host || it.isSamePc(d.id, d.name) } },
                connectingTo = (state as? ConnectionUiState.Connecting)?.target,
                certificateToVerify = state as? ConnectionUiState.VerifyingCertificate,
                pairingAttemptsLeft = (state as? ConnectionUiState.Pairing)?.attemptsLeft,
                searching = state is ConnectionUiState.Idle || state is ConnectionUiState.Discovering,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DevicesUiState())

    init {
        session.startDiscovery()
    }

    /** The screen is visible: allow the costly subnet sweep and try paired PCs right away. */
    fun onVisibleChanged(visible: Boolean) {
        session.sweepAllowed = visible
        if (visible) autoConnector.nudge()
    }

    fun connect(desktop: DiscoveredDesktop) = session.connect(desktop.name, desktop.host, desktop.port)

    fun connectManual(host: String) {
        val trimmed = host.trim()
        if (trimmed.isNotEmpty()) session.connect(trimmed, trimmed, Protocol.PORT)
    }

    fun reconnect(desktop: SavedDesktop) = session.connect(desktop, quiet = false)

    fun forget(desktop: SavedDesktop) = session.forget(desktop)

    fun submitPin(pin: String) = session.submitPin(pin)

    fun trustCertificate() = session.trustCertificate()

    fun cancel() = session.disconnect()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
