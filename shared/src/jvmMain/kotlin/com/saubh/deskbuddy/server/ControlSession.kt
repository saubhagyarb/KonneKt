package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.pairing.PairingResult
import com.saubh.deskbuddy.pairing.PairingSession
import com.saubh.deskbuddy.pairing.PinGenerator
import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.ActivateAppCommand
import com.saubh.deskbuddy.protocol.AddShortcutCommand
import com.saubh.deskbuddy.protocol.AppCatalogRequestCommand
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.MediaArtworkCommand
import com.saubh.deskbuddy.protocol.MediaSeekCommand
import com.saubh.deskbuddy.protocol.MediaStateRequestCommand
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.SetAudioDeviceCommand
import com.saubh.deskbuddy.protocol.SetVolumeCommand
import com.saubh.deskbuddy.protocol.ClipboardCommand
import com.saubh.deskbuddy.protocol.ClipboardRequestCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Envelope
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.FileCancelCommand
import com.saubh.deskbuddy.protocol.FileChunkCommand
import com.saubh.deskbuddy.protocol.FileCompleteCommand
import com.saubh.deskbuddy.protocol.FileOfferCommand
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import com.saubh.deskbuddy.protocol.LaunchAppCommand
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.PairAttempt
import com.saubh.deskbuddy.protocol.PairFailure
import com.saubh.deskbuddy.protocol.PairRequest
import com.saubh.deskbuddy.protocol.PairSuccess
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.protocol.PowerCommand
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.RemoveShortcutCommand
import com.saubh.deskbuddy.protocol.TextShareCommand
import com.saubh.deskbuddy.protocol.CloseAppCommand
import com.saubh.deskbuddy.protocol.CloseTabCommand
import com.saubh.deskbuddy.protocol.CloseWindowCommand
import com.saubh.deskbuddy.protocol.RunningAppsCommand
import com.saubh.deskbuddy.protocol.RunningAppsRequestCommand

data class SessionReply(val message: Message?, val closeConnection: Boolean = false)

interface SessionListener {
    fun onPairingStarted(pin: String)
    fun onPairingEnded()
    fun onAuthenticated(deviceName: String, token: String)
    fun canStartPairing(): Boolean = true
    fun onPairingFailed() = Unit
    fun onPairingSucceeded() = Unit
}

class ControlSession(
    private val store: PairedDeviceStore,
    private val actuators: Actuators,
    private val listener: SessionListener,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val newPin: () -> String = { PinGenerator.generate() },
    private val newToken: () -> String = { TokenGenerator.generate() },
    private val desktopName: String = "",
    private val desktopId: String = "",
    private val log: (String) -> Unit = ServerLog::write,
) {
    private companion object {
        val OK = Ack(ok = true)
        val TRANSFER_FAILED = Ack(ok = false, error = ErrorCode.TRANSFER_FAILED)
        val NOT_FOUND = Ack(ok = false, error = ErrorCode.NOT_FOUND)
        val INTERNAL = Ack(ok = false, error = ErrorCode.INTERNAL)
        val UNSUPPORTED_OS = Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS)

        /** Typing, not pasting: bulk text goes through Share. */
        const val MAX_TYPED_CHARS = 4096
    }

    private var pairing: PairingSession? = null
    private var pendingDeviceName: String = "Unknown device"
    private var keyboardUnsupportedReported = false

    fun onMessage(message: Message): SessionReply = when (message) {
        is PairRequest -> startPairing(message)
        is PairAttempt -> handleAttempt(message)
        is Envelope -> handleEnvelope(message)
        else -> SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL))
    }

    private fun startPairing(request: PairRequest): SessionReply {
        if (pairing != null || !listener.canStartPairing()) {
            return SessionReply(Ack(ok = false, error = ErrorCode.BUSY))
        }
        pendingDeviceName = request.deviceName
        val session = PairingSession(pin = newPin(), createdAtMillis = now())
        pairing = session
        listener.onPairingStarted(session.pin)
        return SessionReply(OK)
    }

    private fun handleAttempt(attempt: PairAttempt): SessionReply {
        val session = pairing
            ?: return SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL), closeConnection = true)
        return when (val result = session.attempt(attempt.pin, now())) {
            is PairingResult.Success -> {
                listener.onPairingSucceeded()
                val token = newToken()
                store.add(PairedDevice(pendingDeviceName, token))
                endPairing()
                listener.onAuthenticated(pendingDeviceName, token)
                SessionReply(PairSuccess(token, desktopName, desktopId))
            }
            is PairingResult.WrongPin -> {
                listener.onPairingFailed()
                SessionReply(PairFailure(result.attemptsLeft))
            }
            is PairingResult.LockedOut -> {
                listener.onPairingFailed()
                endPairing()
                SessionReply(PairFailure(attemptsLeft = 0), closeConnection = true)
            }
            is PairingResult.Expired -> {
                endPairing()
                SessionReply(PairFailure(attemptsLeft = 0), closeConnection = true)
            }
        }
    }

    private fun endPairing() {
        pairing = null
        listener.onPairingEnded()
    }

    private fun handleEnvelope(envelope: Envelope): SessionReply {
        val deviceName = store.deviceNameForToken(envelope.token)
            ?: return SessionReply(Ack(ok = false, error = ErrorCode.AUTH_REQUIRED))
        listener.onAuthenticated(deviceName, envelope.token)
        return try {
            SessionReply(execute(envelope.command))
        } catch (e: UnsupportedOsException) {
            log("${envelope.command::class.simpleName}: unsupported OS")
            SessionReply(Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS))
        } catch (e: Exception) {
            log("${envelope.command::class.simpleName}: ${e::class.simpleName}: ${e.message}")
            SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL))
        }
    }

    /** Returns the reply to send, or null when the command is deliberately silent. */
    private fun execute(command: Command): Message? = when (command) {
        is MediaCommand -> media(command.action)
        is ClipboardCommand -> OK.also { actuators.share.setClipboard(command.text) }
        is ClipboardRequestCommand -> Push(ClipboardCommand(actuators.share.getClipboard().orEmpty()))
        is TextShareCommand -> OK.also { actuators.share.onTextShared(command.text) }
        is FileOfferCommand -> if (actuators.files.offer(command)) OK else TRANSFER_FAILED
        is FileChunkCommand -> when (actuators.files.chunk(command)) {
            FileReceiver.ChunkResult.FAILED -> TRANSFER_FAILED
            FileReceiver.ChunkResult.OK, FileReceiver.ChunkResult.IGNORED -> null
        }
        is FileCompleteCommand ->
            if (actuators.files.complete(command.transferId) != null) OK else TRANSFER_FAILED
        is FileCancelCommand -> OK.also { actuators.files.cancel(command.transferId) }
        is AppCatalogRequestCommand -> actuators.apps.forPhone()
        is LaunchAppCommand -> if (actuators.apps.launch(command.appId)) OK else NOT_FOUND
        is ActivateAppCommand -> if (actuators.apps.activate(command.appId, actuators.windows::focusApp)) OK else NOT_FOUND
        is AddShortcutCommand ->
            if (actuators.apps.addShortcut(command.appId)) actuators.apps.catalog.value else NOT_FOUND
        is RemoveShortcutCommand -> {
            actuators.apps.removeShortcut(command.appId)
            actuators.apps.catalog.value
        }
        // Desktop → phone only; a phone must never send these.
        is NowPlayingCommand, is MediaArtworkCommand, is AudioStateCommand -> INTERNAL
        is MediaStateRequestCommand -> OK.also { actuators.onStateRequested() }
        is MediaSeekCommand -> if (actuators.mediaSession.seek(command.positionMs)) OK else NOT_FOUND
        is SetVolumeCommand -> OK.also {
            actuators.audio.setVolume(command.volume.coerceIn(0, 100))
            actuators.onAudioChanged()
        }
        is SetAudioDeviceCommand ->
            if (actuators.audio.setDefaultDevice(command.id)) OK.also { actuators.onAudioChanged() } else NOT_FOUND
        is PowerCommand -> OK.also {
            when (command.action) {
                PowerAction.SHUTDOWN -> actuators.power.shutdown()
                PowerAction.RESTART -> actuators.power.restart()
                PowerAction.LOCK -> actuators.power.lock()
            }
        }
        is KeyTextCommand ->
            if (command.text.length > MAX_TYPED_CHARS) INTERNAL else keyboard { actuators.keyboard.type(command.text) }
        is KeyPressCommand -> keyboard { actuators.keyboard.press(command.key, command.modifiers) }
        // Desktop → phone only.
        is KeyboardModeCommand, is RunningAppsCommand -> INTERNAL
        is RunningAppsRequestCommand -> windows { OK.also { actuators.onRunningAppsRequested() } }
        is CloseWindowCommand -> windows { if (actuators.windows.closeWindow(command.windowId)) OK else NOT_FOUND }
        is CloseAppCommand -> windows { if (actuators.windows.closeApp(command.appId)) OK else NOT_FOUND }
        is CloseTabCommand ->
            windows { if (actuators.windows.closeTab(command.windowId, command.index, command.title)) OK else NOT_FOUND }
    }

    private fun windows(action: () -> Message): Message =
        if (actuators.windows.isSupported) action() else UNSUPPORTED_OS

    /** Keystrokes are silent on success; an unsupported OS is reported once per connection, not once per key. */
    private fun keyboard(action: () -> Unit): Message? = try {
        action()
        null
    } catch (e: UnsupportedOsException) {
        if (keyboardUnsupportedReported) {
            null
        } else {
            keyboardUnsupportedReported = true
            UNSUPPORTED_OS
        }
    }

    private fun media(action: MediaAction): Message {
        when (action) {
            MediaAction.PLAY_PAUSE -> actuators.media.playPause()
            MediaAction.NEXT -> actuators.media.next()
            MediaAction.PREVIOUS -> actuators.media.previous()
            MediaAction.VOLUME_UP -> actuators.media.volumeUp()
            MediaAction.VOLUME_DOWN -> actuators.media.volumeDown()
            MediaAction.MUTE_TOGGLE -> actuators.media.muteToggle()
        }
        return OK
    }
}
