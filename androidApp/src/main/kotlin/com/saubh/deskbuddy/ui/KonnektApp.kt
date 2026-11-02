package com.saubh.deskbuddy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.components.PowerCountdownDialog
import com.saubh.deskbuddy.share.PendingShare
import com.saubh.deskbuddy.ui.connect.DevicesRoute
import com.saubh.deskbuddy.ui.shell.ConnectedShell
import com.saubh.deskbuddy.ui.standby.StandbyRoute
import com.saubh.deskbuddy.ui.standby.StandbyViewModel
import com.saubh.deskbuddy.ui.theme.KonnektTheme

/** App root: the Devices screen until a PC is connected, then the connected shell. Standby lives here so it works with no PC. */
@Composable
fun KonnektApp(viewModel: ConnectionViewModel, onStandbyVisibleChange: (Boolean) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pcName by viewModel.pcName.collectAsStateWithLifecycle()
    val autoStandby by viewModel.autoStandby.collectAsStateWithLifecycle()
    val powerCountdown by viewModel.powerCountdown.collectAsStateWithLifecycle()
    val shareAwaitingConsent by viewModel.shareAwaitingConsent.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var userStandby by rememberSaveable { mutableStateOf(false) }
    var suppressAutoStandby by rememberSaveable { mutableStateOf(false) }
    // On the Keyboard destination, turning the phone sideways is just a wider keyboard. Saved so a rotation keeps it.
    var keyboardVisible by rememberSaveable { mutableStateOf(false) }
    // Only the connected shell can be on the Keyboard destination; a stale flag must not block the no-PC clock.
    val keyboardBlocks = keyboardVisible && (state is ConnectionUiState.Connected || state is ConnectionUiState.Reconnecting)
    val standby = userStandby || (autoStandby && !suppressAutoStandby && !keyboardBlocks)
    val standbyViewModel = viewModel<StandbyViewModel>(factory = AppViewModelProvider.Factory)
    val mainState = rememberSaveableStateHolder()
    LaunchedEffect(autoStandby) { if (!autoStandby) suppressAutoStandby = false }
    // Ends the session even when the route skipped it during a configuration change.
    // Also tells the host, which shows standby over the lock screen only while it is visible.
    val currentOnStandbyVisibleChange by rememberUpdatedState(onStandbyVisibleChange)
    LaunchedEffect(standby) {
        if (!standby) standbyViewModel.onLeave()
        currentOnStandbyVisibleChange(standby)
    }

    LaunchedEffect(Unit) {
        viewModel.errors.collect { error -> snackbarHostState.showSnackbar(resources.getString(error.messageRes())) }
    }

    KonnektTheme {
        // Its own window, so the countdown shows over the shell and over standby alike.
        // Dark over always-dark standby, so the dialog never flashes a light card on the dock.
        KonnektTheme(darkTheme = standby || isSystemInDarkTheme()) {
            PowerCountdownDialog(powerCountdown, viewModel::cancelPowerCountdown)
        }
        shareAwaitingConsent?.let { pending ->
            IncomingShareDialog(
                pending = pending,
                onConfirm = viewModel::confirmExternalShare,
                onDismiss = viewModel::dismissExternalShare,
            )
        }
        if (standby) {
            StandbyRoute(onExit = {
                userStandby = false
                if (autoStandby) suppressAutoStandby = true
            })
        } else {
            // Keeps each branch's saved state (selected tab, per-tab state) across a standby round-trip.
            when (val s = state) {
                is ConnectionUiState.Connected, is ConnectionUiState.Reconnecting -> mainState.SaveableStateProvider("shell") {
                    ConnectedShell(
                        pcName = pcName,
                        reconnectAttempt = (s as? ConnectionUiState.Reconnecting)?.attempt,
                        snackbarHostState = snackbarHostState,
                        onDisconnect = viewModel::disconnect,
                        onOpenStandby = { userStandby = true },
                        onKeyboardVisible = { keyboardVisible = it },
                    )
                }
                else -> mainState.SaveableStateProvider("devices") { DevicesRoute(snackbarHostState) }
            }
        }
    }
}

@Composable
private fun IncomingShareDialog(pending: PendingShare, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = when (pending) {
        is PendingShare.Text -> "Send shared text?"
        is PendingShare.File -> "Send shared file?"
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (pending) {
                    is PendingShare.Text -> {
                        val preview = pending.text.replace(Regex("\\s+"), " ").take(180)
                        androidx.compose.material3.Text(
                            preview + if (pending.text.length > 180) "…" else "",
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    is PendingShare.File -> androidx.compose.material3.Text(
                        pending.displayName ?: "A file from another app is ready to send.",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.Text(
                    "It will be sent to your connected PC, or queued until a PC connects.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { androidx.compose.material3.Button(onClick = onConfirm) { androidx.compose.material3.Text("Send") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") } },
    )
}

fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.CONNECTION_FAILED -> R.string.err_connection_failed
    ErrorKind.CERTIFICATE_MISMATCH -> R.string.err_certificate_mismatch
    ErrorKind.CONNECTION_LOST -> R.string.err_connection_lost
    ErrorKind.PAIRING_REJECTED -> R.string.err_pairing_rejected
    ErrorKind.DESKTOP_BUSY -> R.string.err_desktop_busy
    ErrorKind.UNSUPPORTED_OS -> R.string.err_unsupported_os
    ErrorKind.INTERNAL -> R.string.err_internal
    ErrorKind.NOT_CONNECTED -> R.string.err_not_connected
    ErrorKind.APP_NOT_FOUND -> R.string.err_app_not_found
    ErrorKind.TRANSFER_FAILED -> R.string.err_transfer_failed
    ErrorKind.CLIPBOARD_EMPTY -> R.string.err_clipboard_empty
    ErrorKind.FILE_UNREADABLE -> R.string.err_file_unreadable
}
