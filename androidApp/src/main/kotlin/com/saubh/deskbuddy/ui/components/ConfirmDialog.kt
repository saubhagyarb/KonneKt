package com.saubh.deskbuddy.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** Confirmation for destructive actions (forget a PC). */
@Composable
fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Text(text, modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ForgetPcDialog(pcName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) = ConfirmDialog(
    icon = KonnektIcons.Delete,
    title = stringResource(R.string.forget_pc_title, pcName),
    text = stringResource(R.string.forget_pc_text),
    confirmLabel = stringResource(R.string.forget_pc_confirm),
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)
