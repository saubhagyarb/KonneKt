package com.saubh.deskbuddy

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.saubh.deskbuddy.apps.AppShortcutsController
import com.saubh.deskbuddy.apps.RunningAppsController
import com.saubh.deskbuddy.client.DesktopDiscovery
import com.saubh.deskbuddy.client.SubnetScanner
import com.saubh.deskbuddy.keyboard.KeyboardController
import com.saubh.deskbuddy.keyboard.RemoteKeyboardHub
import com.saubh.deskbuddy.media.MediaController
import com.saubh.deskbuddy.power.PowerController
import com.saubh.deskbuddy.prefs.PairedDesktopPrefs
import com.saubh.deskbuddy.prefs.StandbyPrefs
import com.saubh.deskbuddy.session.AutoConnector
import com.saubh.deskbuddy.session.RemoteSession
import com.saubh.deskbuddy.share.ClipboardShareController
import com.saubh.deskbuddy.share.FileTransferController
import com.saubh.deskbuddy.share.ReceivedFileStore
import com.saubh.deskbuddy.service.AutoStandbyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Process-wide session and feature controllers. They live as long as the process — kept alive
 * by [com.saubh.deskbuddy.service.ConnectionService] — so the PC stays connected with no screen open.
 */
class AppGraph(context: Context) {
    private val app = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs = PairedDesktopPrefs(app)

    val standbyPrefs = StandbyPrefs(app)

    val session = RemoteSession(
        scope = scope,
        prefs = prefs,
        discovery = DesktopDiscovery(app),
        scanner = SubnetScanner(app),
        deviceName = Build.MODEL,
    )

    val autoConnector = AutoConnector(session, scope)

    val share = ClipboardShareController(
        session = session,
        clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager,
        scope = scope,
    )

    val files = FileTransferController(
        session = session,
        resolver = app.contentResolver,
        store = ReceivedFileStore(app),
        cacheDir = app.cacheDir,
        scope = scope,
        onReceived = share::addToInbox,
    )

    val apps = AppShortcutsController(session, scope)

    val runningApps = RunningAppsController(session, scope)

    val power = PowerController(session, scope)

    val media = MediaController(session, scope)

    val keyboard = KeyboardController(session, scope)

    val keyboardHub = RemoteKeyboardHub(session, scope)

    // Plugging in should connect now, not after auto-connect's current (up to 5 min) backoff.
    val autoStandby = AutoStandbyManager(app, standbyPrefs = standbyPrefs, onPowerConnected = { autoConnector.nudge() })

    init {
        scope.launch {
            combine(keyboardHub.pcTyping, keyboardHub.imeBound) { typing, bound -> typing || bound }
                .collect(autoStandby::setSuppressed)
        }
        scope.launch {
            standbyPrefs.autoStandbyEnabled.collect(autoStandby::setUserEnabled)
        }
        scope.launch {
            standbyPrefs.autoConnectEnabled.collect(autoConnector::setUserEnabled)
        }
    }
}
