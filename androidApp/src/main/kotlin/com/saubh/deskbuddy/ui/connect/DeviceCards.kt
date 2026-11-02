@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.LetterAvatar
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** A paired PC: tap (or Connect) to connect; Forget lives in the overflow so it is never hit by accident. */
@Composable
fun SavedPcCard(pc: SavedDesktop, onConnect: () -> Unit, onForget: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val moreCd = stringResource(R.string.cd_more_options, pc.name)
    Card(
        onClick = onConnect,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconAvatar(KonnektIcons.Computer, 52.dp, cookieShape(), scheme.primaryContainer, scheme.onPrimaryContainer)
            Column(Modifier.weight(1f)) {
                Text(pc.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pc.host, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onConnect) { Text(stringResource(R.string.connect)) }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = moreCd }) {
                    Icon(KonnektIcons.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.forget_pc_confirm)) },
                        leadingIcon = { Icon(KonnektIcons.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onForget()
                        },
                    )
                }
            }
        }
    }
}

/** A PC found on the network that this phone has not paired with yet. */
@Composable
fun NearbyPcCard(desktop: DiscoveredDesktop, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconAvatar(KonnektIcons.Wifi, 44.dp, CircleShape, scheme.secondaryContainer, scheme.onSecondaryContainer)
            Column(Modifier.weight(1f)) {
                Text(desktop.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${desktop.host}:${desktop.port}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.devices_pair), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Icon(KonnektIcons.ChevronRight, contentDescription = null, tint = scheme.primary)
        }
    }
}

/** Shown on top while a connection attempt runs, with a way out. */
@Composable
fun ConnectingBanner(target: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
        modifier = modifier,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ContainedLoadingIndicator()
            Text(
                stringResource(R.string.connecting_to, target),
                style = MaterialTheme.typography.bodyLargeEmphasized,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** First run: what to do on the PC before anything can show up here. */
@Composable
fun GetStartedCard(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val steps = listOf(R.string.start_step_install, R.string.start_step_wifi, R.string.start_step_pair)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.start_title), style = MaterialTheme.typography.titleLargeEmphasized)
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LetterAvatar((index + 1).toString(), 28.dp, CircleShape, scheme.onSecondaryContainer, scheme.secondaryContainer)
                    Text(
                        stringResource(step),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
