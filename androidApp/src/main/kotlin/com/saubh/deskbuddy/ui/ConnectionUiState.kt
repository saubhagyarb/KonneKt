package com.saubh.deskbuddy.ui

import com.saubh.deskbuddy.client.DiscoveredDesktop

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data class Discovering(val desktops: List<DiscoveredDesktop>) : ConnectionUiState
    data class Connecting(val target: String) : ConnectionUiState
    data class VerifyingCertificate(val target: String, val fingerprint: String) : ConnectionUiState
    data class Pairing(val attemptsLeft: Int) : ConnectionUiState
    data class Connected(val desktopName: String) : ConnectionUiState
    data class Reconnecting(val attempt: Int) : ConnectionUiState
}

enum class ErrorKind {
    CONNECTION_FAILED,
    CERTIFICATE_MISMATCH,
    CONNECTION_LOST,
    PAIRING_REJECTED,
    DESKTOP_BUSY,
    UNSUPPORTED_OS,
    INTERNAL,
    NOT_CONNECTED,
    APP_NOT_FOUND,
    TRANSFER_FAILED,
    CLIPBOARD_EMPTY,
    FILE_UNREADABLE,
}
