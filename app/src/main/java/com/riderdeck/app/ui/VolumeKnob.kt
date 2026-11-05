package com.riderdeck.app.ui

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rotary volume knob for media (music and voice guidance).
 * Turn it clockwise to go louder, anticlockwise to go quieter. Tap the centre to mute or unmute.
 */
@Composable
fun VolumeKnob(modifier: Modifier) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val max = remember { (am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) }
    var dragging by remember { mutableStateOf(false) }
    var beforeMute by remember { mutableIntStateOf((max * 0.6f).roundToInt()) }
    var acc by remember { mutableFloatStateOf(0f) }

    // Follow changes from the phone's volume buttons or a helmet headset.
    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) am?.getStreamVolume(AudioManager.STREAM_MUSIC)?.let { vol = it }
            delay(600)
        }
    }

    fun set(v: Int) {
        val c = v.coerceIn(0, max)
        if (c == vol) return
        vol = c
        try {
            am?.setStreamVolume(AudioManager.STREAM_MUSIC, c, 0)
        } catch (_: Exception) {
        }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    val frac = vol.toFloat() / max
    val pct = (frac * 100).roundToInt()

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, maxHeight.value)
        val d = LocalDensity.current
        Box(
            Modifier
                .size(side.coerceAtLeast(1f).dp)
                .semantics { contentDescription = "Volume $pct percent. Turn to change, tap to mute" }
                .pointerInput(max) {
                    var prev = 0f
                    fun angle(o: Offset): Float =
                        Math.toDegrees(atan2((o.y - size.height / 2f).toDouble(), (o.x - size.width / 2f).toDouble())).toFloat()
                    detectDragGestures(
                        onDragStart = { o ->
                            dragging = true
                            prev = angle(o)
                            acc = 0f
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onDrag = { change, _ ->
                            val a = angle(change.position)
                            var delta = a - prev
                            while (delta > 180f) delta -= 360f
                            while (delta < -180f) delta += 360f
                            prev = a
                            acc += delta
                            val step = 270f / max
                            while (acc >= step) {
                                set(vol + 1); acc -= step
                            }
                            while (acc <= -step) {
                                set(vol - 1); acc += step
                            }
                            change.consume()
                        },
                    )
                }
                .pointerInput(max) {
                    detectTapGestures(onTap = {
                        if (vol > 0) {
                            beforeMute = vol
                            set(0)
                        } else {
                            set(beforeMute.coerceAtLeast(1))
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val dim = min(size.width, size.height)
                val stroke = dim * 0.075f
                val inset = stroke / 2f + dim * 0.02f
                val arcSize = Size(dim - inset * 2, dim - inset * 2)
                val tl = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
                val c = Offset(size.width / 2, size.height / 2)

                // Track and level
                drawArc(p.fg.copy(alpha = 0.08f), 135f, 270f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                if (frac > 0f) {
                    drawArc(p.pop.copy(alpha = 0.22f), 135f, 270f * frac, false, tl, arcSize, style = Stroke(stroke * 2.1f, cap = StrokeCap.Butt))
                    drawArc(p.pop, 135f, 270f * frac, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                // Knob body
                val body = dim * 0.31f
                drawCircle(
                    Brush.radialGradient(listOf(p.panelHi, p.panel), center = Offset(c.x - body * 0.3f, c.y - body * 0.4f), radius = body * 1.6f),
                    body, c,
                )
                drawCircle(p.stroke, body, c, style = Stroke(dim * 0.012f))
                // Pointer dot on the knob edge
                val ang = Math.toRadians(135.0 + 270.0 * frac)
                val r = body * 0.74f
                drawCircle(
                    if (vol == 0) p.muted else p.pop, dim * 0.035f,
                    Offset(c.x + r * cos(ang).toFloat(), c.y + r * sin(ang).toFloat()),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (vol == 0) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, null,
                    tint = if (vol == 0) p.bad else p.muted,
                    modifier = Modifier.size((side * 0.15f).dp),
                )
                Text(
                    if (vol == 0) "MUTE" else "$pct",
                    fontSize = with(d) { (side * if (vol == 0) 0.1f else 0.15f).dp.toSp() },
                    fontWeight = FontWeight.Black,
                    color = if (vol == 0) p.bad else p.fg,
                )
            }
        }
    }
}
