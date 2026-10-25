package com.saubh.deskbuddy.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.apps.AppShortcutsController
import com.saubh.deskbuddy.media.MediaController
import com.saubh.deskbuddy.power.PowerController
import com.saubh.deskbuddy.prefs.StandbyPrefs
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.session.RemoteSession
import com.saubh.deskbuddy.share.ClipboardShareController
import com.saubh.deskbuddy.share.FileTransferController
import com.saubh.deskbuddy.ui.ConnectionUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Dashboard state and its quick actions. */
class HomeViewModel(
    session: RemoteSession,
    private val media: MediaController,
    private val apps: AppShortcutsController,
    private val share: ClipboardShareController,
    private val files: FileTransferController,
    private val power: PowerController,
    private val standbyPrefs: StandbyPrefs,
) : ViewModel() {

    private val preferencesFlow = combine(standbyPrefs.autoStandbyEnabled, standbyPrefs.autoConnectEnabled) { autoStandby, autoConnect ->
        autoStandby to autoConnect
    }

    val uiState: StateFlow<HomeUiState> =
        combine(
            combine(session.uiState, session.savedDesktops, media.state) { link, saved, media -> Triple(link, saved, media) },
            combine(apps.state, files.progress, preferencesFlow) { apps, transfer, prefs -> Triple(apps, transfer, prefs) },
        ) { (link, saved, media), (apps, transfer, prefs) ->
            HomeUiState(
                pcName = (link as? ConnectionUiState.Connected)?.desktopName ?: saved.firstOrNull()?.name.orEmpty(),
                reconnectAttempt = (link as? ConnectionUiState.Reconnecting)?.attempt,
                media = media,
                shortcuts = apps.shortcuts,
                shortcutIcons = apps.icons,
                transfer = transfer,
                autoStandbyEnabled = prefs.first,
                autoConnectEnabled = prefs.second,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setAutoStandbyEnabled(enabled: Boolean) {
        viewModelScope.launch { standbyPrefs.setAutoStandbyEnabled(enabled) }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        viewModelScope.launch { standbyPrefs.setAutoConnectEnabled(enabled) }
    }

    /** Lock is instant; restart and shut down start the cancellable countdown. */
    fun power(action: PowerAction) =
        if (action == PowerAction.LOCK) power.send(action) else power.startCountdown(action, uiState.value.pcName)

    fun playPause() = media.send(MediaAction.PLAY_PAUSE)

    fun sendClipboard() = share.sendClipboard()

    fun pullClipboard() = share.pullClipboard()

    fun sendFile(uri: Uri) = files.sendFile(uri)

    fun cancelTransfer() = files.cancelSend()

    fun launch(appId: String) = apps.launch(appId)
}
