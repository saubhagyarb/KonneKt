package com.saubh.deskbuddy.ui.standby

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.ui.AppViewModelProvider
import com.saubh.deskbuddy.ui.isWideWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val RECONCILE_DELAY_MS = 150L
private const val CHROME_AUTO_HIDE_MS = 4_000L
private val NARROW_BELOW = 380.dp
private val RAIL_ITEM = 56.dp
private val RAIL_ITEM_NARROW = 48.dp
private val ACTION_GAP = 8.dp
private val ACTION_GAP_NARROW = 4.dp
private val EDGE = 12.dp

/** Fixed page insets that keep PC content clear of the floating chrome; they never change when the chrome toggles. */
private val PortraitPagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 224.dp, bottom = 88.dp)
private val LandscapePagePadding = PaddingValues(start = 96.dp, end = 88.dp, top = 16.dp, bottom = 88.dp)

/** Always-dark, always-on, full-screen dock screen. Back or the close button exits. */
@Composable
fun StandbyRoute(onExit: () -> Unit, viewModel: StandbyViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    BackHandler(onBack = onExit)
    KeepScreenOnImmersive()
    val activity = LocalActivity.current
    DisposableEffect(viewModel) {
        viewModel.onEnter()
        // Rotation recreates the activity and disposes this effect; standby has not ended, so the user's pick must survive.
        onDispose { if (activity?.isChangingConfigurations != true) viewModel.onLeave() }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StandbySurface(Modifier.fillMaxSize()) {
        if (state.entered) StandbyScreen(state, viewModel, onExit = onExit)
    }
}

/**
 * Pages fill the screen; the chrome floats over them. Landscape: rail at the start edge, power and close top-end,
 * control bar bottom-centre. Portrait: rail and power/close share the top row, control bar bottom-centre.
 * A tap on a blank area shows or hides all chrome together; it shows on entry and hides once after 4 s untouched.
 * [chromeAlwaysVisible] pins it on (previews and screenshots).
 */
@Composable
fun StandbyScreen(state: StandbyUiState, actions: StandbyActions, chromeAlwaysVisible: Boolean = false, onExit: () -> Unit) {
    val wide = isWideWindow()
    val haptics = LocalHapticFeedback.current
    val pager = rememberPagerState(initialPage = state.mode.ordinal) { StandbyMode.entries.size }
    val currentMode by rememberUpdatedState(state.mode)
    val scope = rememberCoroutineScope()
    val entryProgress = remember { Animatable(1f) }
    var entryPage by remember { mutableIntStateOf(-1) }
    val reducedMotion = rememberReducedMotion()

    // The pager reports only moves the user made; a move toward the ViewModel's mode is that mode arriving, not a choice.
    // Settling on the ViewModel's own mode is that mode arriving (no haptic, no report); settling anywhere else is the user's pick.
    LaunchedEffect(pager) {
        var reconcile: Job? = null
        snapshotFlow { pager.settledPage }.drop(1).collect { page ->
            val mode = StandbyMode.entries[page]
            if (mode == currentMode) return@collect
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            actions.onSelectMode(mode)
            // If the policy refused the pick, the pager and rail would disagree: bring the pager back.
            // A child job, so an interrupted scroll can never cancel this long-lived collector.
            reconcile?.cancel()
            reconcile = launch {
                delay(RECONCILE_DELAY_MS)
                if (pager.settledPage == page && currentMode.ordinal != page) {
                    try {
                        pager.animateScrollToPage(currentMode.ordinal)
                    } catch (e: CancellationException) {
                        ensureActive()
                    }
                }
            }
        }
    }
    LaunchedEffect(state.mode) {
        if (pager.settledPage != state.mode.ordinal) {
            // The page a mode change brings in fades (and scales) in. A swipe already showed its page, so it skips this.
            // Launched outside this effect so a newer mode can never strand the page half-transparent.
            entryPage = state.mode.ordinal
            scope.launch {
                entryProgress.snapTo(0f)
                entryProgress.animateTo(1f, tween(PAGE_ENTRY_MS, easing = StrongEaseOut))
            }
            pager.animateScrollToPage(state.mode.ordinal)
        }
    }

    var chromeShown by rememberSaveable { mutableStateOf(true) }
    var autoHideDone by rememberSaveable { mutableStateOf(false) }
    var interacted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoHideDone) return@LaunchedEffect
        delay(CHROME_AUTO_HIDE_MS)
        if (!interacted) chromeShown = false
        autoHideDone = true
    }
    val chrome = chromeShown || chromeAlwaysVisible
    val pcPage = pager.currentPage != StandbyMode.CLOCK.ordinal
    val pageEntry = remember(entryPage, reducedMotion) { PageEntry(entryPage, entryProgress, reducedMotion) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .blankTaps(onPress = { interacted = true }, onBlankTap = { chromeShown = !chromeShown })
            // Keyboard / D-pad use wakes the chrome, so it is reachable without touch.
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    interacted = true
                    chromeShown = true
                }
                false
            },
    ) {
        // The portrait top row needs ~360dp (232 rail + 88 actions + margins): narrow phones get 48dp items.
        val narrow = !wide && maxWidth < NARROW_BELOW
        val itemSize = if (narrow) RAIL_ITEM_NARROW else RAIL_ITEM
        StandbyPages(
            pager, state, actions, wide, pageEntry,
            contentPadding = if (wide) LandscapePagePadding else PortraitPagePadding,
            clockDotsVisible = chrome,
            modifier = Modifier.fillMaxSize(),
        )
        ChromeItem(
            chrome, if (wide) ChromeEdge.START else ChromeEdge.TOP, reducedMotion,
            Modifier.align(if (wide) Alignment.CenterStart else Alignment.TopStart).padding(start = EDGE, top = if (wide) 0.dp else EDGE),
        ) {
            ModeRail(
                StandbyMode.entries[pager.targetPage],
                onSelect = { mode ->
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    actions.onSelectMode(mode)
                },
                vertical = wide,
                itemSize = itemSize,
            )
        }
        ChromeItem(chrome, ChromeEdge.TOP_END, reducedMotion, Modifier.align(Alignment.TopEnd).padding(top = EDGE, end = EDGE)) {
            StandbyTopActions(
                connected = state.connected,
                onPower = actions::onPower,
                onExit = onExit,
                spacing = if (narrow) ACTION_GAP_NARROW else ACTION_GAP,
            )
        }
        ChromeItem(
            chrome && pcPage, ChromeEdge.BOTTOM, reducedMotion,
            Modifier.align(Alignment.BottomCenter).padding(bottom = if (wide) EDGE else 16.dp),
        ) {
            StandbyControlBar(
                isPlaying = state.media.isPlaying,
                volume = state.media.audio?.volume,
                enabled = state.connected,
                onLock = actions::onLockPc,
                onPlayPause = { actions.onMedia(MediaAction.PLAY_PAUSE) },
                onVolumeStep = actions::onVolumeStep,
                onVolumeRelease = actions::onVolumeRelease,
            )
        }
    }
}
