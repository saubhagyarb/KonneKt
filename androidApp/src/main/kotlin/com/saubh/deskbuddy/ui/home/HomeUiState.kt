package com.saubh.deskbuddy.ui.home

import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.share.TransferProgress

/** The Home dashboard: who we are connected to, what is playing, feature settings, and one-tap actions. */
data class HomeUiState(
    val pcName: String = "",
    /** Non-null while the link is being re-established. */
    val reconnectAttempt: Int? = null,
    val media: MediaUiState = MediaUiState(),
    val shortcuts: List<DesktopApp> = emptyList(),
    val shortcutIcons: Map<String, ByteArray> = emptyMap(),
    val transfer: TransferProgress? = null,
    val autoStandbyEnabled: Boolean = true,
    val autoConnectEnabled: Boolean = true,
)
