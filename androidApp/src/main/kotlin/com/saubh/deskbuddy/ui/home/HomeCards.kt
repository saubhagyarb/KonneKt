@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.ui.components.ActionTile
import com.saubh.deskbuddy.ui.components.AppIcon
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.media.NowPlayingArtwork
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** Who we are connected to, plus the PC's power controls — previously hidden inside Standby. */
@Composable
fun PcStatusCard(pcName: String, reconnectAttempt: Int?, onPower: (PowerAction) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconAvatar(KonnektIcons.Computer, 64.dp, cookieShape(), scheme.primary, scheme.onPrimary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_connected_to), style = MaterialTheme.typography.labelLarge)
                    Text(pcName, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (reconnectAttempt != null) {
                            stringResource(R.string.status_reconnecting, reconnectAttempt)
                        } else {
                            stringResource(R.string.home_auto_reconnect)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tile = Modifier.weight(1f).fillMaxHeight()
                ActionTile(KonnektIcons.Lock, stringResource(R.string.power_lock), { onPower(PowerAction.LOCK) }, tile, scheme.surfaceContainerLowest)
                ActionTile(KonnektIcons.RestartAlt, stringResource(R.string.power_restart), { onPower(PowerAction.RESTART) }, tile, scheme.surfaceContainerLowest)
                ActionTile(
                    KonnektIcons.Power,
                    stringResource(R.string.power_shutdown),
                    { onPower(PowerAction.SHUTDOWN) },
                    tile,
                    scheme.surfaceContainerLowest,
                    iconTint = scheme.error,
                )
            }
        }
    }
}

/** App features & settings card allowing users to toggle Auto Standby and Auto-connect on Wi-Fi. */
@Composable
fun SettingsCard(
    autoStandbyEnabled: Boolean,
    onToggleAutoStandby: (Boolean) -> Unit,
    autoConnectEnabled: Boolean,
    onToggleAutoConnect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    KonnektIcons.Dashboard,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    stringResource(R.string.home_settings_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }

            // Setting 1: Auto Standby on Dock / Charge
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_auto_standby_title),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Text(
                        stringResource(R.string.home_auto_standby_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoStandbyEnabled,
                    onCheckedChange = onToggleAutoStandby,
                )
            }

            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))

            // Setting 2: Auto-connect on Wi-Fi
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_auto_connect_title),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Text(
                        stringResource(R.string.home_auto_connect_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoConnectEnabled,
                    onCheckedChange = onToggleAutoConnect,
                )
            }
        }
    }
}

/** Compact now-playing row; tapping it opens the full player. */
@Composable
fun MiniPlayerCard(media: MediaUiState, onOpen: () -> Unit, onPlayPause: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val playCd = stringResource(R.string.cd_play_pause)
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NowPlayingArtwork(media.artwork, 56.dp, scheme.secondaryContainer, scheme.onSecondaryContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    media.nowPlaying?.title ?: stringResource(R.string.media_nothing_playing),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    media.nowPlaying?.artist ?: stringResource(R.string.home_media_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()).semantics { contentDescription = playCd },
            ) { Icon(if (media.isPlaying) KonnektIcons.Pause else KonnektIcons.Play, contentDescription = null) }
        }
    }
}

/** Pinned apps in one swipeable row. */
@Composable
fun ShortcutStrip(shortcuts: List<DesktopApp>, icons: Map<String, ByteArray>, onLaunch: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shortcuts, key = { it.id }) { app ->
            val cd = stringResource(R.string.cd_launch, app.name)
            Surface(
                onClick = { onLaunch(app.id) },
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainerLow,
                modifier = Modifier.width(92.dp).semantics { contentDescription = cd },
            ) {
                Column(
                    Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppIcon(app.name, icons[app.id], 52.dp, cookieShape(), scheme.primaryContainer, scheme.onPrimaryContainer)
                    Text(
                        app.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
