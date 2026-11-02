@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.AppWindow
import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.RunningApp
import com.saubh.deskbuddy.ui.components.LetterAvatar
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** One app: its windows, and for browsers each window's tabs — every one closable on its own. */
@Composable
fun RunningAppCard(
    app: RunningApp,
    onCloseApp: () -> Unit,
    onCloseWindow: (AppWindow) -> Unit,
    onCloseTab: (AppWindow, BrowserTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tabCount = app.windows.sumOf { it.tabs.size }
    var showAllWindows by rememberSaveable(app.id) { mutableStateOf(false) }
    val visibleWindows = if (showAllWindows) app.windows else app.windows.take(MAX_VISIBLE_WINDOWS)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LetterAvatar(app.name, 40.dp, cookieShape(), scheme.secondaryContainer, scheme.onSecondaryContainer)
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (tabCount > 0) {
                        pluralStringResource(R.plurals.open_tab_count, tabCount, tabCount)
                    } else {
                        pluralStringResource(R.plurals.open_window_count, app.windows.size, app.windows.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCloseApp) { Text(stringResource(R.string.close_app), color = scheme.error) }
        }
        Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            visibleWindows.forEach { window ->
                WindowBlock(window, startExpanded = app.windows.size == 1, onCloseWindow, onCloseTab)
            }
            if (app.windows.size > MAX_VISIBLE_WINDOWS) {
                TextButton(onClick = { showAllWindows = !showAllWindows }) {
                    Text(
                        if (showAllWindows) stringResource(R.string.open_show_fewer)
                        else pluralStringResource(R.plurals.open_show_all_windows, app.windows.size, app.windows.size),
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowBlock(
    window: AppWindow,
    startExpanded: Boolean,
    onCloseWindow: (AppWindow) -> Unit,
    onCloseTab: (AppWindow, BrowserTab) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(window.id) { mutableStateOf(startExpanded) }
    var showAllTabs by rememberSaveable(window.id) { mutableStateOf(false) }
    val visibleTabs = if (showAllTabs) window.tabs else window.tabs.take(MAX_VISIBLE_TABS)
    Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainer) {
        Column {
            CloseRow(
                icon = if (window.tabs.isEmpty()) KonnektIcons.Window else KonnektIcons.Browser,
                title = window.title,
                closeDescription = stringResource(R.string.cd_close_window, window.title),
                onClose = { onCloseWindow(window) },
                trailing = if (window.tabs.isEmpty()) {
                    null
                } else {
                    {
                        val toggleCd = stringResource(if (expanded) R.string.cd_hide_tabs else R.string.cd_show_tabs)
                        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics { contentDescription = toggleCd }) {
                            Icon(KonnektIcons.ExpandMore, contentDescription = null, modifier = Modifier.rotate(if (expanded) 180f else 0f))
                        }
                    }
                },
            )
            AnimatedVisibility(visible = expanded && window.tabs.isNotEmpty()) {
                Column(Modifier.padding(start = 24.dp, bottom = 4.dp)) {
                    HorizontalDivider(color = scheme.outlineVariant)
                    visibleTabs.forEach { tab ->
                        CloseRow(
                            icon = KonnektIcons.Tab,
                            title = tab.title,
                            closeDescription = stringResource(R.string.cd_close_tab, tab.title),
                            onClose = { onCloseTab(window, tab) },
                        )
                    }
                    if (window.tabs.size > MAX_VISIBLE_TABS) {
                        TextButton(onClick = { showAllTabs = !showAllTabs }) {
                            Text(
                                if (showAllTabs) stringResource(R.string.open_show_fewer)
                                else pluralStringResource(R.plurals.open_show_all_tabs, window.tabs.size, window.tabs.size),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_VISIBLE_WINDOWS = 12
private const val MAX_VISIBLE_TABS = 8

@Composable
private fun CloseRow(
    icon: ImageVector,
    title: String,
    closeDescription: String,
    onClose: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
        IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = closeDescription }) {
            Icon(KonnektIcons.Close, contentDescription = null, tint = scheme.error)
        }
    }
}
