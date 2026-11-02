@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/**
 * PC identity and link status on the left; Standby and Disconnect on the right. Disconnect uses a
 * "link off" icon — the old power icon read as "shut down the PC".
 */
@Composable
fun ShellTopBar(
    pcName: String,
    reconnectAttempt: Int?,
    onStandby: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val reconnecting = reconnectAttempt != null
    val status = if (reconnecting) {
        stringResource(R.string.status_reconnecting, reconnectAttempt)
    } else {
        stringResource(R.string.status_connected)
    }
    TopAppBar(
        title = { Text(pcName, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(if (reconnecting) scheme.tertiary else scheme.primary, CircleShape))
                Text(status, color = if (reconnecting) scheme.tertiary else scheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            Box(Modifier.padding(start = 12.dp, end = 4.dp)) {
                IconAvatar(KonnektIcons.Computer, 40.dp, cookieShape(), scheme.primaryContainer, scheme.onPrimaryContainer)
            }
        },
        actions = {
            val standbyCd = stringResource(R.string.cd_standby)
            val disconnectCd = stringResource(R.string.cd_disconnect)
            IconButton(onClick = onStandby, modifier = Modifier.semantics { contentDescription = standbyCd }) {
                Icon(KonnektIcons.Nightlight, contentDescription = null)
            }
            IconButton(onClick = onDisconnect, modifier = Modifier.semantics { contentDescription = disconnectCd }) {
                Icon(KonnektIcons.LinkOff, contentDescription = null)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
    )
}
