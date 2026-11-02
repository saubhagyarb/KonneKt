package com.saubh.deskbuddy.ui.share

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.AppViewModelProvider
import com.saubh.deskbuddy.ui.components.ConfirmDialog
import com.saubh.deskbuddy.ui.components.ContentMaxWidth
import com.saubh.deskbuddy.ui.components.InfoCard
import com.saubh.deskbuddy.ui.components.MediumOutlinedButton
import com.saubh.deskbuddy.ui.components.ScreenPadding
import com.saubh.deskbuddy.ui.components.SectionHeader
import com.saubh.deskbuddy.ui.theme.KonnektIcons

@Composable
fun ShareRoute(viewModel: ShareViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let(viewModel::sendFile) }
    ShareScreen(
        state = state,
        onAutoSyncChange = viewModel::setAutoSync,
        onSendClipboard = viewModel::sendClipboard,
        onPullClipboard = viewModel::pullClipboard,
        onSendText = viewModel::sendText,
        onPickFile = { picker.launch("*/*") },
        onCancelTransfer = viewModel::cancelTransfer,
        onCopy = viewModel::copy,
        onClearInbox = viewModel::clearInbox,
    )
}

/** Clipboard first (most used), then text, file, and what the PC sent. */
@Composable
fun ShareScreen(
    state: ShareScreenState,
    onAutoSyncChange: (Boolean) -> Unit,
    onSendClipboard: () -> Unit,
    onPullClipboard: () -> Unit,
    onSendText: (String) -> Unit,
    onPickFile: () -> Unit,
    onCancelTransfer: () -> Unit,
    onCopy: (String) -> Unit,
    onClearInbox: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val column = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "clipboard", contentType = "clipboard-card") { ClipboardCard(state.autoSync, onAutoSyncChange, onSendClipboard, onPullClipboard, column) }
        item(key = "text", contentType = "text-card") { SendTextCard(onSendText, column) }
        item(key = "file", contentType = "file-transfer") {
            val transfer = state.transfer
            if (transfer != null) {
                TransferCard(transfer, onCancelTransfer, column)
            } else {
                MediumOutlinedButton(stringResource(R.string.share_pick_file), onPickFile, column, icon = KonnektIcons.AttachFile)
            }
        }
        item(key = "inbox-header", contentType = "section-header") {
            SectionHeader(stringResource(R.string.share_inbox_title), column.padding(top = 12.dp), stringResource(R.string.share_inbox_hint)) {
                if (state.inbox.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.share_clear_inbox)) }
            }
        }
        if (state.inbox.isEmpty()) {
            item(key = "inbox-empty", contentType = "empty-state") { InfoCard(stringResource(R.string.share_inbox_empty), column, icon = KonnektIcons.Inbox) }
        } else {
            items(state.inbox, key = { it.id }, contentType = { "inbox-item" }) { item ->
                InboxRow(item, onCopy, column.animateItem())
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            icon = KonnektIcons.Delete,
            title = stringResource(R.string.share_clear_title),
            text = stringResource(R.string.share_clear_text),
            confirmLabel = stringResource(R.string.share_clear_inbox),
            onConfirm = {
                confirmClear = false
                onClearInbox()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
