package com.saubh.deskbuddy

import com.saubh.deskbuddy.protocol.AppCatalog
import com.saubh.deskbuddy.protocol.ClipboardCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.RunningAppsCommand
import com.saubh.deskbuddy.protocol.TextShareCommand
import com.saubh.deskbuddy.server.ActuatorFactory
import com.saubh.deskbuddy.server.ClipboardWatcher
import com.saubh.deskbuddy.server.ControlServer
import com.saubh.deskbuddy.server.DeskBuddyAdvertiser
import com.saubh.deskbuddy.server.DesktopIdentity
import com.saubh.deskbuddy.server.FileSender
import com.saubh.deskbuddy.server.HostOs
import com.saubh.deskbuddy.server.MacPasteboard
import com.saubh.deskbuddy.server.PairedDeviceStore
import com.saubh.deskbuddy.server.ServerState
import com.saubh.deskbuddy.server.StartupRegistration
import com.saubh.deskbuddy.server.WindowsClipboardSequence
import com.saubh.deskbuddy.server.keyboard.ExitReason
import com.saubh.deskbuddy.server.keyboard.GlobalHotkey
import com.saubh.deskbuddy.server.keyboard.MacGlobalHotkey
import com.saubh.deskbuddy.server.keyboard.MacKeyboardActuator
import com.saubh.deskbuddy.server.keyboard.PhoneTypingSession
import com.saubh.deskbuddy.server.keyboard.RawKey
import com.saubh.deskbuddy.server.keyboard.TypingState
import com.saubh.deskbuddy.server.keyboard.WindowsGlobalHotkey
import com.saubh.deskbuddy.server.media.MediaStateBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Path

/** Owns the server and everything the desktop window can do; UI observes its flows. */
class DesktopController(private val scope: CoroutineScope) {

    sealed class InboxItem {
        abstract val receivedAt: Long
        data class Text(val text: String, override val receivedAt: Long) : InboxItem()
        data class File(val path: Path, override val receivedAt: Long) : InboxItem()
    }

    private val store = PairedDeviceStore()
    private val actuators = ActuatorFactory.create(
        onTextShared = { addToInbox(InboxItem.Text(it, System.currentTimeMillis())) },
        onFileReceived = { addToInbox(InboxItem.File(it, System.currentTimeMillis())) },
    )
    private val _showRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Another launch of the app asked this one to show its window. */
    val showRequests: SharedFlow<Unit> = _showRequests.asSharedFlow()

    private val server: ControlServer = ControlServer(
        store = store,
        actuators = actuators,
        desktopId = DesktopIdentity().id,
        onShowRequested = { _showRequests.tryEmit(Unit) },
        // A fast reconnect never passes through "no phone": re-announce typing mode to the new socket.
        onPhoneAuthenticated = { typing.phoneReconnected() },
    )
    private val startup = StartupRegistration()
    val isWindows: Boolean = HostOs.isWindows()
    val isMac: Boolean = HostOs.isMac()

    /** Start-at-login exists on Windows (registry) and macOS (LaunchAgent). */
    val supportsStartAtLogin: Boolean = isWindows || isMac

    /** One consumer keeps the PC's keystrokes in order on their way to the phone. */
    private val keyOutbox = Channel<Command>(Channel.UNLIMITED)
    private val typing: PhoneTypingSession = PhoneTypingSession(
        send = { keyOutbox.trySend(it) },
        phoneName = { (server.state.value as? ServerState.Connected)?.devices?.lastOrNull() },
    )
    private val hotkey: GlobalHotkey? = when {
        isWindows -> WindowsGlobalHotkey(typing::toggle)
        isMac -> MacGlobalHotkey(typing::toggle)
        else -> null
    }

    /** How the hotkey is written on this OS; null where there is none. */
    val hotkeyLabel: String? = when {
        isWindows -> WindowsGlobalHotkey.LABEL
        isMac -> MacGlobalHotkey.LABEL
        else -> null
    }

    val typingState: StateFlow<TypingState> = typing.state
    val typingEcho: StateFlow<String> = typing.echo
    private val _hotkeyAvailable = MutableStateFlow(false)
    val hotkeyAvailable: StateFlow<Boolean> = _hotkeyAvailable.asStateFlow()

    fun togglePhoneTyping() = typing.toggle()
    fun onTypingKey(key: RawKey): Boolean = typing.onKey(key)
    fun onTypingFocusLost() = typing.exit(ExitReason.FOCUS_LOST)
    fun dismissTypingNotice() = typing.dismissNotice()

    private val _startAtLogin = MutableStateFlow(false)
    val startAtLogin: StateFlow<Boolean> = _startAtLogin.asStateFlow()
    private val advertiser = DeskBuddyAdvertiser()
    private val fileSender = FileSender(server::push)
    private val broadcaster = MediaStateBroadcaster(actuators.mediaSession, actuators.audio)

    val serverState: StateFlow<ServerState> = server.state
    val mediaHelperAvailable: StateFlow<Boolean> = actuators.mediaSession.available
    val catalog: StateFlow<AppCatalog> = actuators.apps.catalog
    val autoSyncClipboard = MutableStateFlow(true)
    val receivedDirectory: Path = actuators.files.directory
    val port: Int = Protocol.PORT
    val hostName: String = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Unknown")
    val ipAddress: String = lanIpAddress()

    private val _inbox = MutableStateFlow<List<InboxItem>>(emptyList())
    val inbox: StateFlow<List<InboxItem>> = _inbox.asStateFlow()

    private val _transfer = MutableStateFlow<FileSender.Progress?>(null)
    val transfer: StateFlow<FileSender.Progress?> = _transfer.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _permissions = MutableStateFlow(com.saubh.deskbuddy.server.DesktopPermissions.check())
    val permissions: StateFlow<com.saubh.deskbuddy.server.DesktopPermissionState> = _permissions.asStateFlow()

    private var transferJob: Job? = null
    private var runningAppsJob: Job? = null

    fun checkPermissions() {
        scope.launch(Dispatchers.IO) {
            _permissions.value = com.saubh.deskbuddy.server.DesktopPermissions.check()
        }
    }

    fun openMacAccessibilitySettings() {
        com.saubh.deskbuddy.server.DesktopPermissions.openMacAccessibilitySettings()
        checkPermissions()
    }

    fun openMacAutomationSettings() {
        com.saubh.deskbuddy.server.DesktopPermissions.openMacAutomationSettings()
        checkPermissions()
    }

    fun openWindowsFirewallSettings() {
        com.saubh.deskbuddy.server.DesktopPermissions.openWindowsFirewallSettings()
    }

    fun start() {
        server.start()
        // Slow first-run work (registry/LaunchAgent, one JmDNS per adapter, the app scan) must not delay the tray.
        scope.launch(Dispatchers.IO) {
            checkPermissions()
            if (supportsStartAtLogin) _startAtLogin.value = runCatching { startup.sync() }.getOrDefault(false)
            // macOS drops synthetic keys and clicks silently until the user allows this app.
            if (isMac && !MacKeyboardActuator.isTrusted()) {
                notify("Allow KonneKt in System Settings → Privacy & Security → Accessibility so your phone can type, use media keys and close windows.")
            }
            // mDNS failure must not kill the app — manual IP entry still works.
            runCatching { advertiser.start() }
            actuators.apps.current()
        }
        scope.launch {
            val watcher = ClipboardWatcher(
                actuators.share,
                changeToken = when {
                    isWindows -> WindowsClipboardSequence::current
                    isMac -> MacPasteboard::current
                    else -> { { null } }
                },
            )
            watcher.changes().collect { text ->
                if (autoSyncClipboard.value) server.push(Push(ClipboardCommand(text)))
            }
        }
        scope.launch { catalog.drop(1).collect { server.push(it) } }
        actuators.onStateRequested = broadcaster::resendAll
        actuators.onAudioChanged = broadcaster::audioChanged
        actuators.onRunningAppsRequested = ::pushRunningApps
        actuators.mediaSession.start()
        scope.launch { broadcaster.commands().collect { server.push(Push(it)) } }
        scope.launch { for (command in keyOutbox) server.push(Push(command)) }
        scope.launch { serverState.collect { if (it is ServerState.Waiting) typing.exit(ExitReason.PHONE_DISCONNECTED) } }
        _hotkeyAvailable.value = hotkey?.start() == true
    }

    /** JmDNS goodbyes and the engine stop take seconds, so this runs off the UI thread. */
    suspend fun stop() = withContext(Dispatchers.IO) {
        val wasTyping = typing.state.value is TypingState.Active
        typing.exit(ExitReason.QUIT)
        // The outbox consumer may not run again before the server stops; tell the phone directly.
        if (wasTyping) withTimeoutOrNull(500) { server.push(Push(KeyboardModeCommand(active = false))) }
        hotkey?.stop()
        transferJob?.cancel()
        actuators.mediaSession.stop()
        advertiser.stop()
        server.stop()
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        scope.launch {
            if (!server.push(Push(TextShareCommand(text)))) notify("No phone is connected.")
        }
    }

    fun sendFile(path: Path) {
        if (transferJob?.isActive == true) {
            notify("A file is already being sent.")
            return
        }
        transferJob = scope.launch {
            val sent = fileSender.send(path) { _transfer.value = it }
            _transfer.value = null
            notify(if (sent) "Sent ${path.fileName}." else "Could not send ${path.fileName}. Is the phone connected?")
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        _transfer.value = null
    }

    /** Window listing reads browser tabs through UI Automation (seconds), so it runs on IO; overlapping requests share one run. */
    private fun pushRunningApps() {
        if (runningAppsJob?.isActive == true) return
        runningAppsJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val shortcuts = actuators.apps.shortcutApps()
                actuators.windows.runningApps().map { it.copy(shortcutId = actuators.windows.shortcutFor(it, shortcuts)) }
            }.onSuccess { server.push(Push(RunningAppsCommand(it))) }
        }
    }

    // Catalog changes (PowerShell scans, icon rendering, file writes) stay off the UI thread;
    // the catalog StateFlow updates the window when they finish.
    fun refreshCatalog() {
        scope.launch(Dispatchers.IO) { actuators.apps.refresh() }
    }

    fun addShortcut(id: String) {
        scope.launch(Dispatchers.IO) { actuators.apps.addShortcut(id) }
    }

    fun addCustomShortcut(path: Path) {
        scope.launch(Dispatchers.IO) { actuators.apps.addCustom(path) }
    }

    fun removeShortcut(id: String) {
        scope.launch(Dispatchers.IO) { actuators.apps.removeShortcut(id) }
    }

    fun copyToClipboard(text: String) = actuators.share.setClipboard(text)

    fun unpairAll() = store.removeAll()

    fun setStartAtLogin(enabled: Boolean) {
        if (startup.setEnabled(enabled)) {
            _startAtLogin.value = enabled
        } else {
            notify("Could not change the start-at-login setting.")
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun openReceivedFolder() {
        runCatching {
            java.nio.file.Files.createDirectories(receivedDirectory)
            Desktop.getDesktop().open(receivedDirectory.toFile())
        }.onFailure { notify("Could not open $receivedDirectory") }
    }

    private fun addToInbox(item: InboxItem) {
        _inbox.update { (listOf(item) + it).sortedByDescending { entry -> entry.receivedAt } }
    }

    private fun notify(message: String) {
        _notice.value = message
    }

    /** Best-effort LAN IP: routing trick, no packets are actually sent. */
    private fun lanIpAddress(): String = runCatching {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 80)
            socket.localAddress.hostAddress ?: "unknown"
        }
    }.getOrDefault("unknown")
}
