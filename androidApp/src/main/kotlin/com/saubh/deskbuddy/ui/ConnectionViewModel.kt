package com.saubh.deskbuddy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.appGraph
import com.saubh.deskbuddy.power.PowerCountdownState
import com.saubh.deskbuddy.share.PendingShare
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Activity-level state: which half of the app to show (devices vs. connected), the error stream
 * for the snackbar, and content arriving from the system share sheet. Screens have their own ViewModels.
 */
class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.appGraph
    private val session = graph.session

    val uiState: StateFlow<ConnectionUiState> = session.uiState
    val errors: SharedFlow<ErrorKind> = session.errors
    val autoStandby: StateFlow<Boolean> = graph.autoStandby.isAutoStandby
    val powerCountdown: StateFlow<PowerCountdownState> = graph.power.countdown

    /** Connected PC, or the last one used while reconnecting (Reconnecting carries no name). */
    val pcName: StateFlow<String> = combine(uiState, session.savedDesktops) { state, saved ->
        (state as? ConnectionUiState.Connected)?.desktopName ?: saved.maxByOrNull { it.lastUsedAt }?.name.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val pendingShares = ArrayDeque<PendingShare>()
    private val _shareAwaitingConsent = MutableStateFlow<PendingShare?>(null)
    val shareAwaitingConsent: StateFlow<PendingShare?> = _shareAwaitingConsent

    init {
        viewModelScope.launch {
            uiState.collect { if (it is ConnectionUiState.Connected) flushPendingShares() }
        }
    }

    fun disconnect() = session.disconnect()

    fun cancelPowerCountdown() = graph.power.cancelCountdown()

    fun requestShareConsent(pending: PendingShare) {
        _shareAwaitingConsent.value = pending
    }

    fun confirmExternalShare() {
        val pending = _shareAwaitingConsent.value ?: return
        _shareAwaitingConsent.value = null
        share(pending)
    }

    fun dismissExternalShare() {
        _shareAwaitingConsent.value = null
    }

    /** Content from the system share sheet. Sent now if connected, otherwise once connected. */
    fun share(pending: PendingShare) {
        viewModelScope.launch {
            val stored = when (pending) {
                is PendingShare.Text -> pending
                is PendingShare.File -> graph.files.stashForLater(pending.uri)?.let { PendingShare.File(it, pending.displayName) }
            }
            if (stored == null) {
                session.reportError(ErrorKind.FILE_UNREADABLE)
                return@launch
            }
            if (session.isConnected) dispatch(stored) else pendingShares.addLast(stored)
        }
    }

    private fun flushPendingShares() {
        while (pendingShares.isNotEmpty()) dispatch(pendingShares.removeFirst())
    }

    private fun dispatch(pending: PendingShare) {
        when (pending) {
            is PendingShare.Text -> graph.share.sendText(pending.text)
            is PendingShare.File -> graph.files.sendFile(pending.uri)
        }
    }
}
