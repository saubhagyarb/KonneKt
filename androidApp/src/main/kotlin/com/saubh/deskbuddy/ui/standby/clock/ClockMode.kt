package com.saubh.deskbuddy.ui.standby.clock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.standby.ChromeEdge
import com.saubh.deskbuddy.ui.standby.chromeEnter
import com.saubh.deskbuddy.ui.standby.chromeExit
import com.saubh.deskbuddy.ui.standby.rememberReducedMotion
import kotlinx.coroutines.flow.drop

/** Face dots sit this far above the bottom edge. */
private val DOTS_BOTTOM = 16.dp

/**
 * Full-screen clock; swipe sideways for another face. The chosen face is saved by the ViewModel. The burn-in walk
 * moves each face inside its page (the Bubble gradient overdraws the edges), so the page itself stays full-bleed.
 * The face dots fade in and out with the standby chrome ([showDots]).
 */
@Composable
fun ClockMode(face: ClockFace, onSelectFace: (ClockFace) -> Unit, modifier: Modifier = Modifier, showDots: Boolean = true) {
    val pager = rememberPagerState(initialPage = face.ordinal) { ClockFace.entries.size }
    val select by rememberUpdatedState(onSelectFace)
    val currentFace by rememberUpdatedState(face)
    LaunchedEffect(face) { if (pager.settledPage != face.ordinal) pager.scrollToPage(face.ordinal) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { if (ClockFace.entries[it] != currentFace) select(ClockFace.entries[it]) }
    }
    val time by rememberClockTime()
    val is24h = rememberIs24Hour()
    val reducedMotion = rememberReducedMotion()
    val burnIn = rememberBurnInOffset()

    Box(modifier) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val pageFace = ClockFace.entries[page]
            val cd = stringResource(R.string.cd_clock_face, stringResource(pageFace.labelRes))
            // Clipped per page, so the Bubble overdraw never bleeds onto a neighbouring face mid-swipe.
            Box(Modifier.fillMaxSize().clipToBounds()) {
                ClockFaceContent(pageFace, time, is24h, reducedMotion, Modifier.fillMaxSize().offset { burnIn.value }.semantics { contentDescription = cd })
            }
        }
        AnimatedVisibility(
            showDots,
            Modifier.align(Alignment.BottomCenter).padding(bottom = DOTS_BOTTOM),
            enter = chromeEnter(ChromeEdge.NONE, reducedMotion),
            exit = chromeExit(ChromeEdge.NONE, reducedMotion),
        ) { FaceDots(ClockFace.entries.size, pager.currentPage) }
    }
}

@Composable
private fun FaceDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = current == i
            Box(
                Modifier.size(if (active) 8.dp else 6.dp).background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 0.8f else 0.4f),
                    CircleShape,
                ),
            )
        }
    }
}

@Composable
fun ClockFaceContent(face: ClockFace, time: ClockTime, is24h: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    when (face) {
        ClockFace.FLIP -> FlipClockFace(time, is24h, reducedMotion, modifier)
        ClockFace.BUBBLE -> BubbleMorphFace(time, is24h, reducedMotion, modifier)
        ClockFace.NEON -> NeonOrbitFace(time, reducedMotion, modifier)
        ClockFace.DOT_MATRIX -> DotMatrixFace(time, is24h, reducedMotion, modifier)
        ClockFace.SOLAR -> SolarDigitalFace(time, is24h, reducedMotion, modifier)
        ClockFace.ANALOG -> ClassicAnalogFace(time, reducedMotion, modifier)
    }
}

val ClockFace.labelRes: Int
    get() = when (this) {
        ClockFace.FLIP -> R.string.clock_face_flip
        ClockFace.BUBBLE -> R.string.clock_face_bubble
        ClockFace.NEON -> R.string.clock_face_neon
        ClockFace.DOT_MATRIX -> R.string.clock_face_dot_matrix
        ClockFace.SOLAR -> R.string.clock_face_solar
        ClockFace.ANALOG -> R.string.clock_face_analog
    }
