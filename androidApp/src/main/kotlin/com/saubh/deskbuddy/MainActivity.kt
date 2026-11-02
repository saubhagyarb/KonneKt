package com.saubh.deskbuddy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import android.util.Base64
import com.saubh.deskbuddy.service.AutoStandbyLauncherActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.saubh.deskbuddy.power.PowerCountdownState
import com.saubh.deskbuddy.service.ConnectionService
import com.saubh.deskbuddy.share.PendingShare
import com.saubh.deskbuddy.ui.ConnectionViewModel
import com.saubh.deskbuddy.ui.KonnektApp
import com.saubh.deskbuddy.transfer.FileNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_INTERNAL_STANDBY_NONCE = "internal_standby_nonce"
        private const val KEY_OPENED_FOR_AUTO_STANDBY = "opened_for_auto_standby"
        private const val KEY_STANDBY_SHOWN = "standby_shown"

        /** Opens the app in standby, from the dock launcher or its notification. */
        fun autoStandbyIntent(context: Context): Intent = Intent(context, AutoStandbyLauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        internal fun internalAutoStandbyIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_INTERNAL_STANDBY_NONCE, StandbyLaunchNonce.issue())
    }

    private val viewModel: ConnectionViewModel by viewModels()
    private var openedForAutoStandby = false
    // Whether this activity has seen standby on screen: only a visible-to-hidden change means the user left it.
    private var standbyShown = false
    private var lockScreenRelease: Job? = null

    /** Only affects whether the status / media notification is visible; the service runs either way. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KonnektApp(viewModel, onStandbyVisibleChange = ::onStandbyVisibleChange)
        }
        if (savedInstanceState == null) handleShareIntent(intent)
        // Saved state wins over the launch intent: a rotation after leaving standby must not re-arm it.
        val trustedStandbyLaunch = runCatching {
            StandbyLaunchNonce.consume(intent?.getStringExtra(EXTRA_INTERNAL_STANDBY_NONCE))
        }.getOrDefault(false)
        openedForAutoStandby = savedInstanceState?.getBoolean(KEY_OPENED_FOR_AUTO_STANDBY) ?: trustedStandbyLaunch
        standbyShown = savedInstanceState?.getBoolean(KEY_STANDBY_SHOWN) ?: false
        showOverLockScreen(openedForAutoStandby)
        keepServiceInSyncWithPairedPcs()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appGraph.autoStandby.isAutoStandby.collect { isAuto ->
                    if (!isAuto && openedForAutoStandby && !powerCountdownActive()) {
                        finish()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Only finish on undock if standby is what brought the app up; if the user already had it
        // open (still started), leaving standby must return them to it, not close it.
        val wasVisible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val trustedStandbyLaunch = runCatching {
            StandbyLaunchNonce.consume(intent.getStringExtra(EXTRA_INTERNAL_STANDBY_NONCE))
        }.getOrDefault(false)
        if (trustedStandbyLaunch) {
            openedForAutoStandby = !wasVisible
            if (openedForAutoStandby) {
                lockScreenRelease?.cancel()
                showOverLockScreen(true)
            }
        }
        handleShareIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        appGraph.autoStandby.setUiVisible(true)
    }

    override fun onStop() {
        appGraph.autoStandby.setUiVisible(false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_OPENED_FOR_AUTO_STANDBY, openedForAutoStandby)
        outState.putBoolean(KEY_STANDBY_SHOWN, standbyShown)
    }

    /**
     * Leaving standby (✕, back or undock) ends what docking opened: undock must no longer close the app,
     * and the full app must not stay over the lock screen. A running countdown keeps the lock-screen
     * window until it ends, so it can still be cancelled on a locked phone.
     */
    private fun onStandbyVisibleChange(visible: Boolean) {
        if (visible) {
            standbyShown = true
            lockScreenRelease?.cancel()
            return
        }
        if (!standbyShown) return // first composition, before standby has appeared
        standbyShown = false
        openedForAutoStandby = false
        lockScreenRelease?.cancel()
        lockScreenRelease = lifecycleScope.launch {
            appGraph.power.countdown.first { it !is PowerCountdownState.Counting && it !is PowerCountdownState.Sending }
            showOverLockScreen(false)
        }
    }

    private fun powerCountdownActive(): Boolean = appGraph.power.countdown.value.let {
        it is PowerCountdownState.Counting || it is PowerCountdownState.Sending
    }

    /**
     * A docked phone is often locked: standby opened by docking shows over the lock screen and wakes
     * the display. Cleared as soon as standby is left, so the app itself is never shown over the keyguard.
     */
    private fun showOverLockScreen(show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(show)
            setTurnScreenOn(show)
        } else {
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (show) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    /** The background service runs while at least one PC is paired (started here, while in the foreground). */
    private fun keepServiceInSyncWithPairedPcs() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appGraph.prefs.saved.map { it.isNotEmpty() }.distinctUntilChanged().collect { paired ->
                    if (paired) {
                        ConnectionService.start(this@MainActivity)
                        askForNotifications()
                    } else {
                        ConnectionService.stop(this@MainActivity)
                    }
                }
            }
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** System share sheet: ACTION_SEND with text or a single stream. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val stream = runCatching { streamExtra(intent) }.getOrNull()
        val text = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }.getOrNull()
        when {
            stream != null -> viewModel.requestShareConsent(PendingShare.File(stream, sharedFileName(stream)))
            !text.isNullOrBlank() -> viewModel.requestShareConsent(PendingShare.Text(text))
        }
    }

    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun sharedFileName(uri: Uri): String? = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.take(120)
        ?.let(FileNames::sanitize)
}

/** One-shot proof that the non-exported dock launcher created the standby launch intent. */
private object StandbyLaunchNonce {
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val random = SecureRandom()

    fun issue(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val nonce = Base64.encodeToString(bytes, Base64.NO_WRAP)
        pending += nonce
        if (pending.size > 8) pending.remove(pending.first())
        return nonce
    }

    fun consume(candidate: String?): Boolean {
        if (candidate == null) return false
        val expected = pending.firstOrNull { java.security.MessageDigest.isEqual(it.toByteArray(), candidate.toByteArray()) }
            ?: return false
        return pending.remove(expected)
    }
}
