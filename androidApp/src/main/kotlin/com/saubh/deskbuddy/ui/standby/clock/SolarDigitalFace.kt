package com.saubh.deskbuddy.ui.standby.clock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SolarDigitalFace(time: ClockTime, is24h: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val h = if (is24h) time.hour else (time.hour % 12).let { if (it == 0) 12 else it }
    val hourString = String.format(Locale.ROOT, "%02d", h)
    val minuteString = String.format(Locale.ROOT, "%02d", time.minute)

    val angle = rememberSlowRotation(reducedMotion)

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        
        // Solar background gradient
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.maxDimension * 0.7f
            val cx = center.x + cos(angle.value) * (size.width * 0.15f)
            val cy = center.y + sin(angle.value) * (size.height * 0.15f)
            
            val gradient = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF512F).copy(alpha = 0.5f),
                    Color(0xFFDD2476).copy(alpha = 0.25f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = radius
            )
            drawRect(gradient)
        }

        val fontSize = with(LocalDensity.current) { (maxHeight * 0.5f).toSp() }
        val style = TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.W300,
            fontFamily = FontFamily.SansSerif,
            color = Color.White
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedDigit(hourString, style, reducedMotion)
            Text(":", style = style.copy(color = Color.White.copy(alpha = 0.5f)), modifier = Modifier.padding(horizontal = 4.dp).align(Alignment.CenterVertically))
            AnimatedDigit(minuteString, style, reducedMotion)
        }
    }
}

@Composable
private fun rememberSlowRotation(reducedMotion: Boolean): State<Float> {
    return produceState(0f) {
        if (reducedMotion) {
            value = 0f
            return@produceState
        }
        val start = System.currentTimeMillis()
        while (true) {
            val phase = ((System.currentTimeMillis() - start) % 60_000L) / 60_000f
            value = phase * (2 * Math.PI.toFloat())
            delay(50L)
        }
    }
}

@Composable
private fun AnimatedDigit(digit: String, style: TextStyle, reducedMotion: Boolean) {
    AnimatedContent(
        targetState = digit,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(500)) togetherWith fadeOut(tween(500))
            } else {
                (slideInVertically(tween(500)) { height -> height / 2 } + fadeIn(tween(500))) togetherWith
                        (slideOutVertically(tween(500)) { height -> -height / 2 } + fadeOut(tween(500))) using SizeTransform(clip = false)
            }
        },
        label = "digit"
    ) { target ->
        Text(target, style = style)
    }
}
