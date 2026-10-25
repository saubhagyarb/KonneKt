@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.DesktopController
import com.saubh.deskbuddy.server.ServerState
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.KonnektIcons
import com.saubh.deskbuddy.ui.theme.KonnektTheme

private enum class Destination(val label: String, val icon: ImageVector, val title: String, val subtitle: String) {
    STATUS("Status", KonnektIcons.Smartphone, "Your phone", "Pairing and connection"),
    SHARE("Share", KonnektIcons.ContentPaste, "Share", "Clipboard, text and files"),
    SHORTCUTS("Shortcuts", KonnektIcons.Apps, "App shortcuts", "Programs your phone can launch with one tap"),
}

/** Desktop window: wide navigation rail on the left, one pane on the right. */
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
            Row(Modifier.fillMaxSize().padding(padding)) {
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
                Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 32.dp, top = 24.dp, bottom = 24.dp)) {
                    PaneHeader(destination, state)
                    Box(Modifier.fillMaxSize().padding(top = 24.dp)) {
                        when (destination) {
                            Destination.STATUS -> StatusPanel(
                                state = state,
                                hostName = controller.hostName,
                                ipAddress = controller.ipAddress,
                                port = controller.port,
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
                            Destination.SHARE -> SharePanel(controller)
                            Destination.SHORTCUTS -> ShortcutsPanel(controller)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneHeader(destination: Destination, state: ServerState) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(destination.title, style = MaterialTheme.typography.headlineLargeEmphasized)
        val status = when (state) {
            is ServerState.Connected -> "Connected to ${state.devices.joinToString()}"
            is ServerState.Pairing -> "Pairing in progress"
            is ServerState.Waiting -> destination.subtitle
        }
        Text(status, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
    }
}
