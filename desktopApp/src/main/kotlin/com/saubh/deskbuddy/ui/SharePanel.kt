@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.DesktopController
import com.saubh.deskbuddy.server.FileSender
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.MediumTonalButton
import com.saubh.deskbuddy.ui.components.SectionTitle
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** Left column: send actions. Right column: what the phone sent us. */
@Composable
fun SharePanel(controller: DesktopController, compactLayout: Boolean = false) {
    val inbox by controller.inbox.collectAsState()
    val transfer by controller.transfer.collectAsState()
    val autoSync by controller.autoSyncClipboard.collectAsState()
    var compactPane by rememberSaveable { mutableStateOf(CompactSharePane.SEND) }

    if (compactLayout) {
        Column(Modifier.fillMaxSize()) {
            val panes = CompactSharePane.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                panes.forEachIndexed { index, pane ->
                    SegmentedButton(
                        selected = compactPane == pane,
                        onClick = { compactPane = pane },
                        shape = SegmentedButtonDefaults.itemShape(index, panes.size),
                        modifier = Modifier.weight(1f),
                        label = { Text(pane.label) },
                    )
            }
            }
            AnimatedContent(
                targetState = compactPane,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "compact share pane",
            ) { pane ->
                when (pane) {
                    CompactSharePane.SEND -> SendColumn(controller, transfer, autoSync)
                    CompactSharePane.RECEIVED -> InboxColumn(inbox, controller::copyToClipboard, controller::openReceivedFolder)
                }
            }
        }
    } else {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SendColumn(controller, transfer, autoSync, Modifier.weight(1f))
            InboxColumn(inbox, controller::copyToClipboard, controller::openReceivedFolder, Modifier.weight(1f))
        }
    }
}

private enum class CompactSharePane(val label: String) {
    SEND("Send"),
    RECEIVED("Received"),
}

@Composable
private fun SendColumn(
    controller: DesktopController,
    transfer: FileSender.Progress?,
    autoSync: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "send-text", contentType = "share-card") { TextCard(controller::sendText) }
        item(key = "send-file", contentType = "share-card") {
            FileCard(transfer, onPick = { pickFile("Send file to phone")?.let(controller::sendFile) }, controller::cancelTransfer)
        }
        item(key = "clipboard", contentType = "share-card") {
            ClipboardCard(autoSync) { controller.autoSyncClipboard.value = it }
        }
    }
}

@Composable
private fun InboxColumn(
    inbox: List<DesktopController.InboxItem>,
    onCopy: (String) -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "inbox-header", contentType = "inbox-header") {
            SectionTitle("Received from phone") {
                TextButton(onClick = onOpenFolder) {
                    Icon(KonnektIcons.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Open folder", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (inbox.isEmpty()) {
            item(key = "inbox-empty", contentType = "empty-state") { InboxEmpty() }
        } else {
            items(
                items = inbox,
                key = { it.id },
                contentType = { item -> if (item is DesktopController.InboxItem.Text) "inbox-text" else "inbox-file" },
            ) { item -> InboxCard(item, onCopy, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconAvatar(icon, 40.dp, CircleShape, scheme.primaryContainer, scheme.onPrimaryContainer)
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@Composable
private fun TextCard(onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(KonnektIcons.Notes, "Send text to phone")
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Type or paste text") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )
            Button(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                shapes = ButtonDefaults.shapes(),
                enabled = draft.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(KonnektIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Send", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun FileCard(transfer: FileSender.Progress?, onPick: () -> Unit, onCancel: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(KonnektIcons.AttachFile, "Send a file to phone")
            if (transfer != null) {
                val fraction = if (transfer.totalBytes > 0) transfer.sentBytes.toFloat() / transfer.totalBytes else 0f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ContainedLoadingIndicator(progress = { fraction })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Sending ${transfer.name}",
                            style = MaterialTheme.typography.bodyLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else {
                MediumTonalButton("Choose a file…", onPick, icon = KonnektIcons.Upload, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ClipboardCard(autoSync: Boolean, onAutoSyncChange: (Boolean) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(KonnektIcons.ContentPaste, "Clipboard")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-sync clipboard to phone", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Anything you copy here appears on the phone while it is connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoSync, onCheckedChange = onAutoSyncChange)
            }
        }
    }
}

@Composable
private fun InboxEmpty() {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(KonnektIcons.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(
                "Nothing received yet. Text and files shared from the phone show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InboxCard(item: DesktopController.InboxItem, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var showFullText by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (item) {
                    is DesktopController.InboxItem.Text -> {
                        IconAvatar(KonnektIcons.Notes, 40.dp, CircleShape, scheme.secondaryContainer, scheme.onSecondaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.text,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (item.text.length > 100 || item.text.count { it == '\n' } > 3) {
                                TextButton(onClick = { showFullText = true }) { Text("Show more") }
                            }
                        }
                        IconButton(onClick = { onCopy(item.text) }) {
                            Icon(KonnektIcons.ContentCopy, contentDescription = "Copy", tint = scheme.primary)
                        }
                    }
                    is DesktopController.InboxItem.File -> {
                        IconAvatar(KonnektIcons.Folder, 40.dp, CircleShape, scheme.tertiaryContainer, scheme.onTertiaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text(item.path.fileName.toString(), style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.path.parent.toString(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onCopy(item.path.toString()) }) {
                            Icon(KonnektIcons.ContentCopy, contentDescription = "Copy path", tint = scheme.primary)
                        }
                    }
                }
            }
        }
        if (item is DesktopController.InboxItem.Text && showFullText) {
            AlertDialog(
                onDismissRequest = { showFullText = false },
                title = { Text("Text from phone") },
                text = {
                    Text(
                        item.text,
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onCopy(item.text); showFullText = false }) { Text("Copy text") }
                },
                dismissButton = {
                    TextButton(onClick = { showFullText = false }) { Text("Close") }
                },
            )
        }
    }
}

