package com.saubh.deskbuddy.ui.standby.clock

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val PREVIEW_DATE_MS = 1_760_000_000_000L

@Composable
fun ClassicAnalogFace(time: ClockTime, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val smooth = rememberSmoothSeconds(enabled = !reducedMotion && !LocalInspectionMode.current, fallback = time.second.toFloat())
    val inspecting = LocalInspectionMode.current
    val context = LocalContext.current
    val battery by produceState(remember(context) { batteryPercent(context, inspecting) }) {
        while (true) {
            delay(60_000L)
            value = batteryPercent(context, inspecting)
        }
    }
    
    Row(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize(0.9f).aspectRatio(1f)) {
                val radius = min(size.width, size.height) * 0.48f
                val c = center
                
                // Draw tick marks
                for (i in 0 until 60) {
                    val isHour = i % 5 == 0
                    val angle = i * 6f
                    val tickLength = if (isHour) radius * 0.12f else radius * 0.04f
                    val strokeWidth = if (isHour) radius * 0.025f else radius * 0.01f
                    val color = if (isHour) Color(0xFFE0E0E0) else Color.White.copy(alpha = 0.4f)
                    
                    val start = polar(c, radius - tickLength, angle)
                    val end = polar(c, radius, angle)
                    drawLine(color, start, end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
                }
                
                // Draw hands
                val hourAngle = ClockAngles.hour(time)
                val minuteAngle = ClockAngles.minute(time)
                val secondAngle = (if (reducedMotion) time.second.toFloat() else smooth.value) * 6f
                
                val handColor = Color(0xFFF5F5F5)
                
                // Hour hand (with counterweight)
                drawLine(handColor, polar(c, radius * 0.1f, hourAngle - 180f), polar(c, radius * 0.55f, hourAngle), strokeWidth = radius * 0.05f, cap = StrokeCap.Round)
                // Minute hand (with counterweight)
                drawLine(handColor, polar(c, radius * 0.1f, minuteAngle - 180f), polar(c, radius * 0.85f, minuteAngle), strokeWidth = radius * 0.035f, cap = StrokeCap.Round)
                
                // Second hand (red, sweeping, with long counterweight)
                val secColor = Color(0xFFFF3B30) // Premium Red
                drawLine(secColor, polar(c, radius * 0.25f, secondAngle - 180f), polar(c, radius * 0.9f, secondAngle), strokeWidth = radius * 0.015f, cap = StrokeCap.Round)
                // Center dot
                drawCircle(secColor, radius * 0.04f, c)
                drawCircle(Color.Black, radius * 0.015f, c)
            }
        }
        
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val date = if (inspecting) Date(PREVIEW_DATE_MS) else Date()
                val dayOfWeek = remember(time.hour, time.minute, inspecting) { SimpleDateFormat("EEEE", Locale.getDefault()).format(date) }
                val dayOfMonth = remember(time.hour, time.minute, inspecting) { SimpleDateFormat("d", Locale.getDefault()).format(date) }
                val month = remember(time.hour, time.minute, inspecting) { SimpleDateFormat("MMMM", Locale.getDefault()).format(date) }

                Text(
                    text = dayOfWeek.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFFF3B30),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 4.sp
                )
                Text(
                    text = dayOfMonth,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Cursive,
                    lineHeight = 120.sp
                )
                Text(
                    text = month.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 6.sp
                )
                
                Spacer(Modifier.height(32.dp))
                
                if (battery >= 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(if (battery > 20) Color(0xFF34C759) else Color(0xFFFF3B30), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$battery%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

private fun polar(c: Offset, r: Float, degrees: Float): Offset {
    val rad = Math.toRadians((degrees - 90f).toDouble())
    return Offset(c.x + r * cos(rad).toFloat(), c.y + r * sin(rad).toFloat())
}

@Composable
private fun rememberSmoothSeconds(enabled: Boolean, fallback: Float): State<Float> =
    produceState(fallback, enabled) {
        if (!enabled) return@produceState
        var last = 0L
        while (true) withFrameMillis { frame ->
            if (frame - last >= 16L) {
                last = frame
                value = (System.currentTimeMillis() % 60_000L) / 1_000f
            }
        }
    }

private fun batteryPercent(context: Context, inspecting: Boolean): Int = if (inspecting) -1 else
    (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
