package com.saubh.deskbuddy.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.power.PowerController
import com.saubh.deskbuddy.power.PowerCountdownState
import com.saubh.deskbuddy.power.PowerOutcome
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** Root-level overlay for the restart / shut-down countdown. Back cancels while counting; tapping outside does nothing. */
@Composable
fun PowerCountdownDialog(state: PowerCountdownState, onCancel: () -> Unit) {
    if (state is PowerCountdownState.Idle) return
    Dialog(
        onDismissRequest = { if (state is PowerCountdownState.Counting) onCancel() },
        properties = DialogProperties(dismissOnClickOutside = false),
    ) { PowerCountdownCard(state, onCancel) }
}

@Composable
fun PowerCountdownCard(state: PowerCountdownState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "power",
        ) { s ->
            Column(
                Modifier.padding(24.dp).heightIn(min = 280.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                when (s) {
                    is PowerCountdownState.Counting -> Counting(s, onCancel)
                    is PowerCountdownState.Sending -> Sending(s)
                    is PowerCountdownState.Finished -> Result(s)
                    PowerCountdownState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun Counting(s: PowerCountdownState.Counting, onCancel: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val total = PowerController.COUNTDOWN_SECONDS.toFloat()
    // Starts from the current second, so a rotation mid-countdown resumes the ring where it was.
    val ring = remember { Animatable(s.secondsLeft / total) }
    LaunchedEffect(s.secondsLeft) {
        haptics.performHapticFeedback(if (s.secondsLeft <= 3) HapticFeedbackType.LongPress else HapticFeedbackType.SegmentTick)
        ring.animateTo((s.secondsLeft - 1) / total, tween(1_000, easing = LinearEasing))
    }
    val restart = s.action == PowerAction.RESTART
    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { ring.value },
            modifier = Modifier.size(160.dp),
            color = MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 10.dp,
        )
        AnimatedContent(
            targetState = s.secondsLeft,
            transitionSpec = { (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) },
            label = "seconds",
        ) { Text(it.toString(), style = MaterialTheme.typography.displayLarge) }
    }
    // TalkBack hears the time left only at 10, 5, 3, 2 and 1 seconds, not every tick.
    val milestone = when {
        s.secondsLeft > 5 -> PowerController.COUNTDOWN_SECONDS
        s.secondsLeft > 3 -> 5
        else -> s.secondsLeft
    }
    val announcement = pluralStringResource(R.plurals.power_countdown_in_seconds, milestone, milestone)
    Text(
        stringResource(if (restart) R.string.power_countdown_restart_title else R.string.power_countdown_shutdown_title, s.pcName),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            stateDescription = announcement
        },
    )
    Text(
        pluralStringResource(R.plurals.power_countdown_in_seconds, s.secondsLeft, s.secondsLeft),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Text(
        stringResource(R.string.power_countdown_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(KonnektIcons.Close, contentDescription = null)
        Text(stringResource(R.string.power_countdown_cancel), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Result(s: PowerCountdownState.Finished) {
    val restart = s.action == PowerAction.RESTART
    val (icon, tint) = when (s.outcome) {
        PowerOutcome.SENT -> KonnektIcons.CheckCircle to MaterialTheme.colorScheme.primary
        PowerOutcome.CANCELLED -> KonnektIcons.Close to MaterialTheme.colorScheme.onSurfaceVariant
        PowerOutcome.NOT_CONNECTED -> KonnektIcons.LinkOff to MaterialTheme.colorScheme.error
    }
    val iconScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { iconScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(72.dp).graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value })
    val title = when (s.outcome) {
        PowerOutcome.SENT -> stringResource(if (restart) R.string.power_result_restart_sent else R.string.power_result_shutdown_sent, s.pcName)
        PowerOutcome.CANCELLED -> stringResource(if (restart) R.string.power_result_restart_cancelled else R.string.power_result_shutdown_cancelled)
        PowerOutcome.NOT_CONNECTED -> stringResource(R.string.power_result_not_connected, s.pcName)
    }
    val hint = when (s.outcome) {
        PowerOutcome.SENT -> stringResource(R.string.power_result_sent_hint)
        PowerOutcome.CANCELLED -> stringResource(R.string.power_result_cancelled_hint, s.pcName)
        PowerOutcome.NOT_CONNECTED -> stringResource(R.string.power_result_not_connected_hint)
    }
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun Sending(s: PowerCountdownState.Sending) {
    CircularProgressIndicator(Modifier.size(96.dp))
    Text(
        stringResource(R.string.power_countdown_sending, s.pcName),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
