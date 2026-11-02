package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.ui.components.AppIcon

private val KEY_GAP = 12.dp
private val KEY_MIN_HEIGHT = 120.dp
private val COMPACT_MIN_KEY = 104.dp

/** A regular key is 132dp tall (16 + 72 icon + 8 + 20 label + 16); below two such rows the grid switches to compact keys. */
private val COMPACT_BELOW = 132.dp * 2 + KEY_GAP

/**
 * Pinned shortcuts as keys sized to their content (at least 120dp tall); a dot marks apps already open on the PC.
 * Tap focuses or launches. On short grids (e.g. 800×360dp landscape) the keys turn compact, 104dp tall, so two
 * whole rows fit instead of one and a sliver.
 */
@Composable
fun AppGrid(
    shortcuts: List<DesktopApp>,
    icons: Map<String, ByteArray>,
    running: Set<String>,
    onActivate: (String) -> Unit,
    modifier: Modifier = Modifier,
    minKey: Dp = 128.dp,
) {
    if (shortcuts.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.standby_no_shortcuts), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    BoxWithConstraints(modifier) {
        val compact = constraints.hasBoundedHeight && maxHeight < COMPACT_BELOW
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (compact) COMPACT_MIN_KEY else minKey),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP),
        ) {
            items(shortcuts, key = { it.id }, contentType = { "shortcut" }) { app ->
                AppKey(app, icons[app.id], app.id in running, compact, onClick = { onActivate(app.id) }, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun AppKey(app: DesktopApp, icon: ByteArray?, running: Boolean, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val runningState = stringResource(R.string.standby_app_running_state)
    Box(
        modifier
            .heightIn(min = KEY_MIN_HEIGHT)
            .then(if (running) Modifier.semantics { stateDescription = runningState } else Modifier)
            .clickable(interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.surfaceContainer),
    ) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 8.dp, vertical = if (compact) 12.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(app.name, icon, if (compact) 48.dp else 72.dp, MaterialTheme.shapes.large, scheme.secondaryContainer, scheme.onSecondaryContainer)
            Text(
                app.name,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (running) {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(10.dp).clip(CircleShape).background(scheme.primary))
        }
    }
}
