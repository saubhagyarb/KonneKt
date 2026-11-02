package com.saubh.deskbuddy

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.saubh.deskbuddy.server.StartupRegistration
import com.saubh.deskbuddy.server.keyboard.TypingState
import com.saubh.deskbuddy.ui.KeyboardOverlay
import com.saubh.deskbuddy.ui.ServerScreen
import com.saubh.deskbuddy.ui.rememberAppIconPainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.system.exitProcess

/**
 * Runs in the tray so phones can keep connecting while the window is closed. Launched at
 * sign-in with [StartupRegistration.BACKGROUND_ARG] it starts hidden; closing the window
 * hides it, and only the tray's Quit exits.
 */
fun main(args: Array<String>) {
    val background = StartupRegistration.BACKGROUND_ARG in args
    // A second launch (e.g. from the Start menu while we sit in the tray) just shows the running window.
    if (SingleInstance.handOffToRunningInstance(show = !background)) {
        // A packaged launch can arrive while an older/dev instance owns the server. Refresh its
        // login item before handing off, so the next sign-in starts the packaged app, not Gradle.
        if (!System.getProperty("jpackage.app-path").isNullOrBlank()) {
            runCatching { StartupRegistration().sync() }
        }
        return
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller = DesktopController(scope).also { it.start() }

    try {
        // Keep the controller alive until every Compose application exit path has returned.
        application(exitProcessOnExit = false) {
            var visible by remember { mutableStateOf(!background) }
            var quitting by remember { mutableStateOf(false) }
            val windowState = rememberWindowState(width = 960.dp, height = 640.dp)
            val show = {
                visible = true
                windowState.isMinimized = false
            }
            val quit: () -> Unit = {
                if (!quitting) {
                    quitting = true
                    exitApplication()
                }
            }
            val appIcon = rememberAppIconPainter()
            LaunchedEffect(Unit) { controller.showRequests.collect { show() } }

            if (!quitting) {
                val typing by controller.typingState.collectAsState()
                val echo by controller.typingEcho.collectAsState()
                Tray(
                    icon = appIcon,
                    tooltip = "KonneKt",
                    onAction = show,
                    menu = {
                        Item("Open KonneKt", onClick = show)
                        Item(controller.hotkeyLabel?.let { "Type on phone ($it)" } ?: "Type on phone", onClick = controller::togglePhoneTyping)
                        Item("Quit", onClick = quit)
                    },
                )
                if (typing != TypingState.Idle) {
                    KeyboardOverlay(
                        state = typing,
                        echo = echo,
                        hotkeyLabel = controller.hotkeyLabel,
                        onKey = controller::onTypingKey,
                        onFocusLost = controller::onTypingFocusLost,
                        onDismiss = controller::dismissTypingNotice,
                    )
                }
                Window(
                    onCloseRequest = { visible = false },
                    visible = visible,
                    title = "KonneKt",
                    icon = appIcon,
                    state = windowState,
                ) {
                    LaunchedEffect(visible) {
                        if (visible) {
                            window.toFront()
                            window.requestFocus()
                        }
                    }
                    ServerScreen(controller)
                }
            }
        }
    } finally {
        try {
            runBlocking { withTimeoutOrNull(QUIT_TIMEOUT_MS) { controller.stop() } }
        } finally {
            scope.cancel()
        }
    }
    // application(exitProcessOnExit = false) gives controller.stop() time to finish before exit.
    exitProcess(0)
}

private const val QUIT_TIMEOUT_MS = 3_000L
