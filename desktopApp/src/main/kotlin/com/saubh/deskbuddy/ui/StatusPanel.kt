@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.saubh.deskbuddy.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.server.DesktopPermissionState
import com.saubh.deskbuddy.server.ServerState
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.MediumOutlinedButton
import com.saubh.deskbuddy.ui.components.MediumTonalButton
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.KonnektIcons
import kotlinx.coroutines.delay

/** Start-at-login switch state; null hides the card (Linux). [inMenuBar] words it for macOS. */
data class StartupSetting(val enabled: Boolean, val onChange: (Boolean) -> Unit, val inMenuBar: Boolean = false)

/** Home pane: one hero card whose content follows the server state, address chips, system permissions card, and the startup switch. */
@Composable
fun StatusPanel(
    state: ServerState,
    hostName: String,
    ipAddress: String,
    port: Int,
    mediaHelperAvailable: Boolean,
    keyboardLabel: String,
    startup: StartupSetting?,
    permissions: DesktopPermissionState,
    onCheckPermissions: () -> Unit,
    onOpenMacAccessibility: () -> Unit,
    onOpenMacAutomation: () -> Unit,
    onOpenWindowsFirewall: () -> Unit,
    onUnpairAll: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(end = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                is ServerState.Waiting -> WaitingHero()
                is ServerState.Pairing -> PairingHero(state.pin)
                is ServerState.Connected -> ConnectedHero(state.devices, onUnpairAll)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(hostName) },
                    leadingIcon = { Icon(KonnektIcons.Computer, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = CircleShape,
                )
                AssistChip(
                    onClick = {},
                    label = { Text("$ipAddress : $port") },
                    leadingIcon = { Icon(KonnektIcons.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = CircleShape,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (mediaHelperAvailable) "Now playing: on" else "Now playing: unavailable") },
                    leadingIcon = { Icon(KonnektIcons.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = CircleShape,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(keyboardLabel) },
                    leadingIcon = { Icon(KonnektIcons.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = CircleShape,
                )
            }
            PermissionsCard(
                permissions = permissions,
                onCheckPermissions = onCheckPermissions,
                onOpenMacAccessibility = onOpenMacAccessibility,
                onOpenMacAutomation = onOpenMacAutomation,
                onOpenWindowsFirewall = onOpenWindowsFirewall,
            )
            startup?.let { StartupCard(it) }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun PermissionsCard(
    permissions: DesktopPermissionState,
    onCheckPermissions: () -> Unit,
    onOpenMacAccessibility: () -> Unit,
    onOpenMacAutomation: () -> Unit,
    onOpenWindowsFirewall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(Unit) {
        onCheckPermissions()
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (permissions.isMac && !permissions.allMacPermissionsGranted) {
                scheme.errorContainer.copy(alpha = 0.25f)
            } else {
                scheme.surfaceContainerLow
            }
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        KonnektIcons.Security,
                        contentDescription = null,
                        tint = if (permissions.isMac && !permissions.allMacPermissionsGranted) scheme.error else scheme.primary,
                    )
                    Column {
                        Text(
                            "System Permissions & Access",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        Text(
                            if (permissions.isMac) {
                                if (permissions.allMacPermissionsGranted) "All required macOS permissions are granted"
                                else "Allow macOS permissions so your phone can control this Mac"
                            } else {
                                "Windows firewall and app privilege permissions"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                MediumOutlinedButton(
                    text = "Re-check",
                    onClick = onCheckPermissions,
                    icon = KonnektIcons.Refresh,
                )
            }

            if (permissions.isMac) {
                // Item 1: Accessibility
                PermissionRow(
                    title = "Accessibility Permission",
                    description = "Required so your phone can type remotely into Mac apps, use media keys, and close or switch windows.",
                    granted = permissions.macAccessibilityGranted,
                    actionLabel = if (permissions.macAccessibilityGranted) "Open Settings" else "Grant Accessibility",
                    onAction = onOpenMacAccessibility,
                )

                // Item 2: Automation
                PermissionRow(
                    title = "Automation Permission (System Events & Music)",
                    description = "Required so KonneKt can control Apple Music / Spotify playback and list open windows or browser tabs.",
                    granted = permissions.macAutomationGranted,
                    actionLabel = if (permissions.macAutomationGranted) "Open Settings" else "Grant Automation",
                    onAction = onOpenMacAutomation,
                )
            } else if (permissions.isWindows) {
                // Windows Firewall
                PermissionRow(
                    title = "Windows Defender Firewall",
                    description = "Ensure KonneKt is allowed through Windows Defender Firewall for local Wi-Fi connections.",
                    granted = true,
                    actionLabel = "Firewall Settings",
                    onAction = onOpenWindowsFirewall,
                )
                // Windows UIPI / Admin
                PermissionRow(
                    title = "Administrator Applications (UIPI)",
                    description = "Windows blocks controlling apps running as Administrator (e.g. Task Manager). To type into elevated apps, launch KonneKt as Administrator.",
                    granted = true,
                    actionLabel = null,
                    onAction = {},
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            if (granted) KonnektIcons.CheckCircle else KonnektIcons.Warning,
            contentDescription = null,
            tint = if (granted) scheme.primary else scheme.error,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmallEmphasized)
                AssistChip(
                    onClick = {},
                    label = { Text(if (granted) "Granted" else "Action Required", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            if (granted) KonnektIcons.Check else KonnektIcons.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    },
                    shape = CircleShape,
                )
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (actionLabel != null && !granted) {
            MediumTonalButton(
                text = actionLabel,
                onClick = onAction,
                icon = KonnektIcons.Lock,
            )
        } else if (actionLabel != null) {
            MediumOutlinedButton(
                text = actionLabel,
                onClick = onAction,
                icon = KonnektIcons.OpenInNew,
            )
        }
    }
}

@Composable
private fun HeroCard(container: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun WaitingHero() {
    val scheme = MaterialTheme.colorScheme
    HeroCard(scheme.surfaceContainerHigh) {
        LoadingIndicator(Modifier.size(72.dp))
        Text("Waiting for your phone", style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(
            "Open KonneKt on your phone on the same Wi-Fi network. This PC will show up automatically, or connect by address.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PairingHero(pin: String) {
    val scheme = MaterialTheme.colorScheme
    val total = (Protocol.PAIRING_TIMEOUT_MILLIS / 1_000).toInt()
    var secondsLeft by remember(pin) { mutableStateOf(total) }
    LaunchedEffect(pin) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    HeroCard(scheme.primaryContainer) {
        Text("Pairing request", style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onPrimaryContainer)
        Text(
            pin.chunked(3).joinToString("  "),
            style = MaterialTheme.typography.displayLargeEmphasized,
            color = scheme.onPrimaryContainer,
        )
        Text("Enter this PIN on your phone", style = MaterialTheme.typography.bodyLarge, color = scheme.onPrimaryContainer)
        LinearProgressIndicator(
            progress = { secondsLeft / total.toFloat() },
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Text("Expires in ${secondsLeft}s", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
    }
}

@Composable
private fun ConnectedHero(devices: List<String>, onUnpairAll: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    HeroCard(scheme.surfaceContainerHigh) {
        IconAvatar(KonnektIcons.Smartphone, 80.dp, cookieShape(), scheme.primary, scheme.onPrimary)
        Text("Connected", style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.primary)
        Text(devices.joinToString(", "), style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
        if (devices.size > 1) {
            Text("${devices.size} phones connected", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
        Text(
            "Media, apps, clipboard and files are ready. Use the Share and Shortcuts panes on the left.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        MediumOutlinedButton("Unpair all devices", onUnpairAll, icon = KonnektIcons.Delete)
    }
}

@Composable
private fun StartupCard(setting: StartupSetting) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (setting.inMenuBar) "Open at login" else "Start with Windows", style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    "Runs in the ${if (setting.inMenuBar) "menu bar" else "tray"} after you sign in, so your phone reconnects on its own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Switch(checked = setting.enabled, onCheckedChange = setting.onChange)
        }
    }
}
