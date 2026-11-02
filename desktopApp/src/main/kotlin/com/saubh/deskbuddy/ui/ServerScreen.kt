@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.DesktopController
import com.saubh.deskbuddy.server.ServerState
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.KonnektIcons
import com.saubh.deskbuddy.ui.theme.KonnektTheme

private enum class Destination(val label: String, val icon: ImageVector, val title: String) {
    STATUS("Status", KonnektIcons.Smartphone, "Your phone"),
    SHARE("Share", KonnektIcons.ContentPaste, "Share"),
    SHORTCUTS("Shortcuts", KonnektIcons.Apps, "App shortcuts"),
}

private const val COMPACT_NAVIGATION_WIDTH_DP = 760
private const val COMPACT_SHARE_WIDTH_DP = 640

/** Desktop window: a navigation rail at regular widths and a compact top selector when resized narrow. */
@Composable
fun ServerScreen(controller: DesktopController) {
    val state by controller.serverState.collectAsState()
    val notice by controller.notice.collectAsState()
    val helperAvailable by controller.mediaHelperAvailable.collectAsState()
    val startAtLogin by controller.startAtLogin.collectAsState()
    val hotkey by controller.hotkeyAvailable.collectAsState()
    val permissions by controller.permissions.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var destination by remember { mutableStateOf(Destination.STATUS) }

    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        controller.clearNotice()
    }

    KonnektTheme {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val compactNavigation = maxWidth < COMPACT_NAVIGATION_WIDTH_DP.dp
                val compactShare = maxWidth < COMPACT_SHARE_WIDTH_DP.dp
                if (compactNavigation) {
                    Column(Modifier.fillMaxSize()) {
                        SingleChoiceSegmentedButtonRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Destination.entries.forEachIndexed { index, entry ->
                                SegmentedButton(
                                    selected = destination == entry,
                                    onClick = { destination = entry },
                                    shape = SegmentedButtonDefaults.itemShape(index, Destination.entries.size),
                                    modifier = Modifier.weight(1f),
                                    label = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                        DesktopMainPane(
                            modifier = Modifier.weight(1f),
                            destination = destination,
                            state = state,
                            controller = controller,
                            helperAvailable = helperAvailable,
                            startAtLogin = startAtLogin,
                            hotkey = hotkey,
                            permissions = permissions,
                            compactShare = compactShare,
                        )
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        WideNavigationRail(
                            header = {
                                val scheme = MaterialTheme.colorScheme
                                Box(Modifier.padding(start = 24.dp, top = 8.dp, bottom = 16.dp)) {
                                    IconAvatar(KonnektIcons.Computer, 48.dp, cookieShape(), scheme.primaryContainer, scheme.onPrimaryContainer)
                                }
                            },
                        ) {
                            Destination.entries.forEach { entry ->
                                WideNavigationRailItem(
                                    selected = destination == entry,
                                    onClick = { destination = entry },
                                    icon = { Icon(entry.icon, contentDescription = null) },
                                    label = { Text(entry.label) },
                                    railExpanded = false,
                                )
                            }
                        }
                        DesktopMainPane(
                            modifier = Modifier.weight(1f),
                            destination = destination,
                            state = state,
                            controller = controller,
                            helperAvailable = helperAvailable,
                            startAtLogin = startAtLogin,
                            hotkey = hotkey,
                            permissions = permissions,
                            compactShare = compactShare,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopMainPane(
    modifier: Modifier,
    destination: Destination,
    state: ServerState,
    controller: DesktopController,
    helperAvailable: Boolean,
    startAtLogin: Boolean,
    hotkey: Boolean,
    permissions: com.saubh.deskbuddy.server.DesktopPermissionState,
    compactShare: Boolean,
) {
    Column(
        modifier.fillMaxSize().padding(start = 24.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
    ) {
        PaneHeader(destination, state)
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp),
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "desktop destination",
        ) { target ->
            when (target) {
                Destination.STATUS -> StatusPanel(
                    state = state,
                    hostName = controller.hostName,
                    ipAddress = controller.ipAddress,
                    port = controller.port,
                    certificateFingerprint = controller.certificateFingerprint,
                    mediaHelperAvailable = helperAvailable,
                    keyboardLabel = when {
                        controller.hotkeyLabel == null -> "Keyboard: tray menu → Type on phone"
                        hotkey -> "Keyboard: ${controller.hotkeyLabel}"
                        else -> "Keyboard: hotkey unavailable — use the tray menu"
                    },
                    startup = if (controller.supportsStartAtLogin) {
                        StartupSetting(startAtLogin, controller::setStartAtLogin, inMenuBar = controller.isMac)
                    } else {
                        null
                    },
                    permissions = permissions,
                    onCheckPermissions = controller::checkPermissions,
                    onOpenMacAccessibility = controller::openMacAccessibilitySettings,
                    onOpenMacAutomation = controller::openMacAutomationSettings,
                    onOpenWindowsFirewall = controller::openWindowsFirewallSettings,
                    onUnpairAll = controller::unpairAll,
                )
                Destination.SHARE -> SharePanel(controller, compactLayout = compactShare)
                Destination.SHORTCUTS -> ShortcutsPanel(controller)
            }
        }
    }
}

@Composable
private fun PaneHeader(destination: Destination, state: ServerState) {
    val scheme = MaterialTheme.colorScheme
    val (status, statusColor) = when (state) {
        is ServerState.Connected -> {
            val count = state.devices.size
            (if (count == 1) "1 phone connected" else "$count phones connected") to scheme.primary
        }
        is ServerState.Pairing -> "Pairing in progress" to scheme.tertiary
        is ServerState.Waiting -> "Waiting for a phone" to scheme.onSurfaceVariant
    }
    Column {
        Text(destination.title, style = MaterialTheme.typography.headlineLargeEmphasized)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = 8.dp).size(8.dp).background(statusColor, androidx.compose.foundation.shape.CircleShape))
            Text(
                status,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
