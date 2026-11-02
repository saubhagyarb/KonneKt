@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.share.TransferProgress

/** A file on its way to or from the PC; outgoing transfers can be cancelled. */
@Composable
fun TransferCard(transfer: TransferProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (transfer.outgoing) R.string.share_sending else R.string.share_receiving, transfer.name),
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.share_percent, (transfer.fraction * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                if (transfer.outgoing) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
            LinearWavyProgressIndicator(progress = { transfer.fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}
