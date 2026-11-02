@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.media

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.ui.theme.KonnektIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Artwork (or the music-note placeholder), rounded, [size] square. Bytes are decoded off the main thread. */
@Composable
fun NowPlayingArtwork(artwork: ByteArray?, size: Dp, placeholderContainer: Color, placeholderContent: Color) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = artwork) {
        value = artwork?.let { bytes ->
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }
    }
    val shape = MaterialTheme.shapes.largeIncreased
    val decoded = bitmap
    if (decoded != null) {
        Image(decoded, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(Modifier.size(size).clip(shape).background(placeholderContainer), contentAlignment = Alignment.Center) {
            Icon(KonnektIcons.MusicNote, contentDescription = null, tint = placeholderContent, modifier = Modifier.size(size / 2))
        }
    }
}

/** Human-readable source app: Windows AUMIDs look like `Spotify.exe` or `Publisher.App_hash!App`. */
fun friendlyAppName(appId: String): String =
    appId.substringAfterLast('\\').substringBefore('!').substringBefore('_').removeSuffix(".exe")

/**
 * Title/artist/album/app for the media tab card; standby has its own dark layout.
 * [scrollable] lets the card scroll on its own when it sits beside the controls in a short window.
 */
@Composable
fun NowPlayingCard(state: MediaUiState, modifier: Modifier = Modifier, scrollable: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val np = state.nowPlaying
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NowPlayingArtwork(state.artwork, 160.dp, scheme.primaryContainer, scheme.onPrimaryContainer)
            if (state.isPlaying) {
                LinearWavyProgressIndicator(modifier = Modifier.width(160.dp))
            } else {
                Spacer(Modifier.height(10.dp))
            }
            Text(
                np?.title ?: stringResource(R.string.media_nothing_playing),
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
            )
            val subtitle = listOfNotNull(np?.artist, np?.album).joinToString(" • ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else if (state.status == PlaybackStatus.NONE) {
                Text(
                    stringResource(R.string.media_nothing_playing_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            np?.appName?.let { app ->
                AssistChip(onClick = {}, label = { Text(friendlyAppName(app)) }, shape = MaterialTheme.shapes.small)
            }
        }
    }
}
