@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.share.InboxItem
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.MediumButton
import com.saubh.deskbuddy.ui.components.MediumTonalButton
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** The most frequent share action, so it gets the hero card: send / get, and automatic sync. */
@Composable
fun ClipboardCard(autoSync: Boolean, onAutoSyncChange: (Boolean) -> Unit, onSend: () -> Unit, onGet: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val syncLabel = stringResource(R.string.share_auto_sync)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconAvatar(KonnektIcons.ContentPaste, 44.dp, CircleShape, scheme.primary, scheme.onPrimary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.share_clipboard_title), style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(stringResource(R.string.share_clipboard_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MediumButton(stringResource(R.string.share_send_clipboard), onSend, Modifier.weight(1f), icon = KonnektIcons.Upload)
                MediumTonalButton(stringResource(R.string.share_get_clipboard), onGet, Modifier.weight(1f), icon = KonnektIcons.Download)
            }
            Row(
                Modifier.toggleable(value = autoSync, role = Role.Switch, onValueChange = onAutoSyncChange),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(syncLabel, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.share_auto_sync_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoSync, onCheckedChange = null)
            }
        }
    }
}

/** Type or paste text and send it to the PC's inbox and clipboard. */
@Composable
fun SendTextCard(onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    var draft by rememberSaveable { mutableStateOf("") }
    val sendCd = stringResource(R.string.share_send_text)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.share_text_title)) },
                placeholder = { Text(stringResource(R.string.share_text_hint)) },
                shape = MaterialTheme.shapes.medium,
                minLines = 1,
                maxLines = 5,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.semantics { contentDescription = sendCd },
            ) { Icon(KonnektIcons.Send, contentDescription = null) }
        }
    }
}

/** One received item; text can be copied, files show where they were saved. */
@Composable
fun InboxRow(item: InboxItem, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val copyCd = stringResource(R.string.share_copy)
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
                    is InboxItem.Text -> {
                        IconAvatar(KonnektIcons.Notes, 40.dp, CircleShape, scheme.secondaryContainer, scheme.onSecondaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.text,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (item.text.length > 100 || item.text.count { it == '\n' } > 3) {
                                TextButton(onClick = { showFullText = true }) {
                                    Text(stringResource(R.string.share_show_more))
                                }
                            }
                        }
                        IconButton(onClick = { onCopy(item.text) }, modifier = Modifier.semantics { contentDescription = copyCd }) {
                            Icon(KonnektIcons.ContentCopy, contentDescription = null, tint = scheme.primary)
                        }
                    }
                    is InboxItem.File -> {
                        IconAvatar(KonnektIcons.Folder, 40.dp, CircleShape, scheme.tertiaryContainer, scheme.onTertiaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.share_saved_to, item.location), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (item is InboxItem.Text && showFullText) {
            AlertDialog(
                onDismissRequest = { showFullText = false },
                title = { Text(stringResource(R.string.share_received_text_title)) },
                text = {
                    Text(
                        item.text,
                        modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onCopy(item.text); showFullText = false }) {
                        Text(stringResource(R.string.share_copy))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFullText = false }) { Text(stringResource(R.string.close)) }
                },
            )
        }
    }
}
