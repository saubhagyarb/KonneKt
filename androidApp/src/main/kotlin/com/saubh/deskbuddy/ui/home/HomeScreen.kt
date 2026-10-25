package com.saubh.deskbuddy.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.service.DockPermissions
import com.saubh.deskbuddy.ui.AppViewModelProvider
import com.saubh.deskbuddy.ui.components.ActionTile
import com.saubh.deskbuddy.ui.components.ContentMaxWidth
import com.saubh.deskbuddy.ui.components.InfoCard
import com.saubh.deskbuddy.ui.components.ScreenPadding
import com.saubh.deskbuddy.ui.components.SectionHeader
import com.saubh.deskbuddy.ui.share.TransferCard
import com.saubh.deskbuddy.ui.shell.Destination
import com.saubh.deskbuddy.ui.theme.KonnektIcons

@Composable
fun HomeRoute(
    onNavigate: (Destination) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let(viewModel::sendFile) }
    val context = LocalContext.current
    var dockSetup by remember { mutableStateOf(DockSetup()) }
    // Re-checked on every return from the system settings screens the card opens.
    LifecycleResumeEffect(Unit) {
        dockSetup = DockSetup(
            needsOverlay = !DockPermissions.canOpenFromBackground(context),
            needsBackground = !DockPermissions.ignoresBatteryOptimizations(context),
        )
        onPauseOrDispose {}
    }
    HomeScreen(
        state = state,
        onPower = viewModel::power,
        onPlayPause = viewModel::playPause,
        onSendClipboard = viewModel::sendClipboard,
        onPullClipboard = viewModel::pullClipboard,
        onPickFile = { picker.launch("*/*") },
        onCancelTransfer = viewModel::cancelTransfer,
        onLaunch = viewModel::launch,
        onNavigate = onNavigate,
        onToggleAutoStandby = viewModel::setAutoStandbyEnabled,
        onToggleAutoConnect = viewModel::setAutoConnectEnabled,
        dockSetup = dockSetup,
        onAllowOverlay = { runCatching { context.startActivity(DockPermissions.overlaySettings(context)) } },
        onAllowBackground = {
            runCatching { context.startActivity(DockPermissions.batteryOptimizationRequest(context)) }
                .recoverCatching { context.startActivity(DockPermissions.batterySettingsList()) }
        },
    )
}

/** Dashboard: PC + power, feature toggles, what is playing, the everyday actions, and pinned apps. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPower: (PowerAction) -> Unit,
    onPlayPause: () -> Unit,
    onSendClipboard: () -> Unit,
    onPullClipboard: () -> Unit,
    onPickFile: () -> Unit,
    onCancelTransfer: () -> Unit,
    onLaunch: (String) -> Unit,
    onNavigate: (Destination) -> Unit,
    onToggleAutoStandby: (Boolean) -> Unit = {},
    onToggleAutoConnect: (Boolean) -> Unit = {},
    dockSetup: DockSetup = DockSetup(),
    onAllowOverlay: () -> Unit = {},
    onAllowBackground: () -> Unit = {},
) {
    val column = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "status") {
            PcStatusCard(
                pcName = state.pcName,
                reconnectAttempt = state.reconnectAttempt,
                onPower = onPower,
                modifier = column,
            )
        }
        item(key = "settings") {
            SettingsCard(
                autoStandbyEnabled = state.autoStandbyEnabled,
                onToggleAutoStandby = onToggleAutoStandby,
                autoConnectEnabled = state.autoConnectEnabled,
                onToggleAutoConnect = onToggleAutoConnect,
                modifier = column,
            )
        }
        if (!dockSetup.ready && state.autoStandbyEnabled) {
            item(key = "dock") { DockSetupCard(dockSetup, onAllowOverlay, onAllowBackground, column) }
        }
        state.transfer?.let { transfer ->
            item(key = "transfer") { TransferCard(transfer, onCancelTransfer, column) }
        }
        item(key = "media") {
            MiniPlayerCard(state.media, onOpen = { onNavigate(Destination.MEDIA) }, onPlayPause = onPlayPause, modifier = column)
        }
        item(key = "actions-header") { SectionHeader(stringResource(R.string.home_quick_actions), column.padding(top = 4.dp)) }
        item(key = "actions") {
            Row(column.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tile = Modifier.weight(1f).fillMaxHeight()
                ActionTile(KonnektIcons.Upload, stringResource(R.string.home_send_clipboard), onSendClipboard, tile)
                ActionTile(KonnektIcons.Download, stringResource(R.string.home_get_clipboard), onPullClipboard, tile)
                ActionTile(KonnektIcons.AttachFile, stringResource(R.string.home_send_file), onPickFile, tile)
                ActionTile(KonnektIcons.Keyboard, stringResource(R.string.home_type), { onNavigate(Destination.KEYBOARD) }, tile)
            }
        }
        item(key = "shortcuts-header") {
            SectionHeader(stringResource(R.string.home_shortcuts), column.padding(top = 4.dp)) {
                TextButton(onClick = { onNavigate(Destination.APPS) }) { Text(stringResource(R.string.home_all_apps)) }
            }
        }
        item(key = "shortcuts") {
            if (state.shortcuts.isEmpty()) {
                InfoCard(stringResource(R.string.home_no_shortcuts), column, icon = KonnektIcons.StarOutline)
            } else {
                ShortcutStrip(state.shortcuts, state.shortcutIcons, onLaunch, column)
            }
        }
    }
}
