package com.saubh.deskbuddy.ui.connect

import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.ui.ConnectionUiState

/** Everything the Devices (connect) screen shows. */
data class DevicesUiState(
    /** Paired PCs, most recently used first. */
    val saved: List<SavedDesktop> = emptyList(),
    /** PCs on the network that are not paired yet. */
    val nearby: List<DiscoveredDesktop> = emptyList(),
    /** Name of the PC a connection attempt is running for, if any. */
    val connectingTo: String? = null,
    val certificateToVerify: ConnectionUiState.VerifyingCertificate? = null,
    /** Non-null while the PC waits for its PIN. */
    val pairingAttemptsLeft: Int? = null,
    /** Nothing connected and no attempt running: discovery is looking. */
    val searching: Boolean = true,
)
