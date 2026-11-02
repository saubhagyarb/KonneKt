package com.saubh.deskbuddy.ui

import com.saubh.deskbuddy.server.keyboard.MacForeground
import com.saubh.deskbuddy.server.keyboard.WindowsForeground
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.saubh.deskbuddy.server.keyboard.RawKey
import com.saubh.deskbuddy.server.keyboard.TypingState
import com.saubh.deskbuddy.ui.theme.KonnektIcons
import com.saubh.deskbuddy.ui.theme.KonnektTheme
import kotlinx.coroutines.delay
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

private const val NOTICE_MS = 1_500L

/**
 * Always-on-top bar that holds keyboard focus while the PC types on the phone. Every key event in
 * the app is routed to [onKey] through a KeyEventDispatcher (before focus traversal eats Tab).
 */
@Composable
fun KeyboardOverlay(
    state: TypingState,
    echo: String,
    hotkeyLabel: String?,
    onKey: (RawKey) -> Boolean,
    onFocusLost: () -> Unit,
    onDismiss: () -> Unit,
) {
    val windowState = rememberWindowState(position = WindowPosition(Alignment.TopCenter), size = DpSize(640.dp, 76.dp))
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnFocusLost by rememberUpdatedState(onFocusLost)
    Window(
        onCloseRequest = onDismiss,
        state = windowState,
        title = "KonneKt keyboard",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = true,
    ) {
        var focused by remember { mutableStateOf(false) }
        DisposableEffect(window) {
            val dispatcher = KeyEventDispatcher { event -> currentOnKey(event.toRawKey()) }
            val focusListener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent) {
                    focused = true
                }

                override fun windowLostFocus(e: WindowEvent) {
                    if (focused) currentOnFocusLost()
                }
            }
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            manager.addKeyEventDispatcher(dispatcher)
            window.addWindowFocusListener(focusListener)
            window.toFront()
            WindowsForeground.force(window)
            MacForeground.activate()
            window.requestFocus()
            onDispose {
                window.removeWindowFocusListener(focusListener)
                manager.removeKeyEventDispatcher(dispatcher)
            }
        }
        if (state !is TypingState.Active) {
            LaunchedEffect(state) {
                delay(NOTICE_MS)
                onDismiss()
            }
        }
        KonnektTheme { OverlayBar(state, echo, hotkeyLabel, focused) }
    }
}

@Composable
private fun OverlayBar(state: TypingState, echo: String, hotkeyLabel: String?, focused: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val message = when (state) {
        is TypingState.Active ->
            if (focused) "Typing on ${state.phoneName} — ${hotkeyLabel?.let { "$it or " }.orEmpty()}Esc to return" else "Click here to type to your phone"
        TypingState.NoPhone -> "No phone connected"
        TypingState.PhoneDisconnected -> "Phone disconnected"
        TypingState.Idle -> ""
    }
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        modifier = Modifier.fillMaxSize().padding(4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(KonnektIcons.Keyboard, contentDescription = null)
            Text(message, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (state is TypingState.Active && echo.isNotEmpty()) {
                Text(
                    echo,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp),
                )
            }
        }
    }
}

private fun KeyEvent.toRawKey() = RawKey(
    type = when (id) {
        KeyEvent.KEY_PRESSED -> RawKey.Type.PRESSED
        KeyEvent.KEY_RELEASED -> RawKey.Type.RELEASED
        else -> RawKey.Type.TYPED
    },
    keyCode = keyCode,
    char = keyChar,
    ctrl = isControlDown,
    alt = isAltDown,
    shift = isShiftDown,
    altGraph = isAltGraphDown,
    meta = isMetaDown,
)
