package com.riderdeck.app.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riderdeck.app.DeviceState
import com.riderdeck.app.Hub
import com.riderdeck.app.MainActivity
import com.riderdeck.app.Media
import com.riderdeck.app.MediaState
import com.riderdeck.app.Perms
import com.riderdeck.app.RideState
import com.riderdeck.app.RiderApp
import com.riderdeck.app.RiderSettings
import com.riderdeck.app.Speaker
import com.riderdeck.app.Weather
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The intro plays once per app launch. */
private object Session {
    var introShown = false
}

@Composable
fun RiderRoot(act: MainActivity) {
    val ctx = LocalContext.current
    var setup by rememberSaveable {
        mutableStateOf(!RiderApp.prefs.onboarded || !Perms.location(ctx))
    }
    if (setup) {
        Onboarding(act) {
            RiderApp.prefs.onboarded = true
            act.startTracking()
            setup = false
        }
    } else {
        Dashboard(act)
    }
}

@Composable
fun Dashboard(act: MainActivity) {
    val p = LocalPal.current
    val ride by Hub.ride.collectAsState()
    val media by Hub.media.collectAsState()
    val nav by Hub.nav.collectAsState()
    val dev by Hub.device.collectAsState()
    val listener by Hub.listenerConnected.collectAsState()
    val s by RiderApp.prefs.s.collectAsState()
    var sheet by remember { mutableStateOf<String?>(com.riderdeck.app.Demo.sheet) }
    val open: (String) -> Unit = { sheet = it }
    var intro by remember { mutableStateOf(!Session.introShown && !com.riderdeck.app.Demo.active) }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (!intro) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp)) {
                val landscape = maxWidth > maxHeight
                if (landscape) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Rail(true, dev, open, Modifier.width(70.dp).fillMaxHeight().enter(0))
                        SpeedPanel(ride, s, false, open, act, Modifier.weight(0.22f).fillMaxHeight().enter(1))
                        MapPanel(ride, nav, act, open, Modifier.weight(0.52f).fillMaxHeight().enter(2))
                        Column(Modifier.weight(0.26f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MusicPanel(media, listener, false, open, Modifier.weight(1.15f).fillMaxWidth().enter(3))
                            InfoPanel(ride, dev, false, Modifier.weight(1f).fillMaxWidth().enter(4))
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusRow(ride, dev)
                        MapPanel(ride, nav, act, open, Modifier.weight(1f).fillMaxWidth().enter(0))
                        Row(Modifier.fillMaxWidth().height(216.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SpeedPanel(ride, s, true, open, act, Modifier.weight(1.3f).fillMaxHeight().enter(1))
                            InfoPanel(ride, dev, true, Modifier.weight(1f).fillMaxHeight().enter(2))
                        }
                        MusicPanel(media, listener, true, open, Modifier.fillMaxWidth().height(80.dp).enter(3))
                        Rail(false, dev, open, Modifier.fillMaxWidth().height(70.dp).enter(4))
                    }
                }
            }
            Overlays(act)
            sheet?.let { name -> Sheets(name, act, open) { sheet = null } }
        }
        AnimatedVisibility(visible = intro, exit = fadeOut(tween(380)) + scaleOut(targetScale = 1.08f, animationSpec = tween(380))) {
            Intro {
                Session.introShown = true
                intro = false
            }
        }
    }
}

/* ---------------- intro ---------------- */

@Composable
private fun Intro(done: () -> Unit) {
    val p = LocalPal.current
    val a = remember { Animatable(0f) }
    val line = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 140f))
        line.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(250)
        done()
    }
    Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.graphicsLayer {
                    alpha = a.value.coerceIn(0f, 1f)
                    scaleX = 0.82f + 0.18f * a.value
                    scaleY = 0.82f + 0.18f * a.value
                    translationY = (1f - a.value) * 40f
                }.shimmer(p.bg),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "RiderDeck", fontWeight = FontWeight.ExtraBold, fontSize = 50.sp, letterSpacing = (-1.5).sp,
                    style = androidx.compose.ui.text.TextStyle(brush = Brush.linearGradient(listOf(p.fg, p.pop, p.pop2))),
                )
            }
            Box(Modifier.padding(top = 12.dp).width(180.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.glass)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(line.value).clip(RoundedCornerShape(4.dp)).background(p.brand()))
            }
            Text(
                "RIDE  ·  NAVIGATE  ·  STAY SAFE", fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold,
                color = p.muted, modifier = Modifier.padding(top = 14.dp).alpha(line.value),
            )
        }
    }
}

/* ---------------- status / clock ---------------- */

@Composable
fun clockText(): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    return SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now))
}

@Composable
fun StatusRow(ride: RideState, dev: DeviceState) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(clockText(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Dot(if (ride.hasFix) p.ok else p.warn)
                Text(if (ride.hasFix) "GPS" else "Finding GPS", fontSize = 12.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
            }
            if (dev.tempC > 0) {
                Text("${dev.tempC.roundToInt()}°", fontSize = 12.sp, color = if (dev.tempC >= 43) p.bad else p.muted, fontWeight = FontWeight.SemiBold)
            }
            Text("${dev.batteryPct}%", fontSize = 12.sp, color = if (dev.batteryPct in 1..15) p.bad else p.muted, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ---------------- rail / bottom bar ---------------- */

private data class RailItem(val key: String, val icon: ImageVector, val label: String)

private val railItems = listOf(
    RailItem("goto", Icons.Rounded.Place, "Go to"),
    RailItem("music", Icons.Rounded.MusicNote, "Music"),
    RailItem("near", Icons.Rounded.Search, "Nearby"),
    RailItem("fuel", Icons.Rounded.LocalGasStation, "Fuel"),
    RailItem("more", Icons.Rounded.Apps, "More"),
)

@Composable
fun Rail(vertical: Boolean, dev: DeviceState, open: (String) -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    val g = LocalGlove.current
    if (vertical) {
        BoxWithConstraints(modifier.panel()) {
            val iconH = ((maxHeight - 150.dp) / 5).coerceIn(34.dp, 50.dp * g)
            Column(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(clockText(), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("${dev.batteryPct}%", fontSize = 11.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
                if (dev.tempC >= 40f) Text("${dev.tempC.roundToInt()}°", fontSize = 11.sp, color = if (dev.tempC >= 43f) p.bad else p.warn)
                Spacer(Modifier.weight(1f))
                railItems.forEach { item ->
                    Box(
                        Modifier.size(50.dp, iconH).clip(RoundedCornerShape(14.dp)).pressable { open(item.key) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(item.icon, item.label, tint = p.muted) }
                }
                Spacer(Modifier.weight(1f))
                SosButton(50) { open("sos") }
            }
        }
    } else {
        Row(modifier.panel().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            railItems.forEach { item ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).pressable { open(item.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, null, tint = p.muted)
                    Text(item.label, fontSize = 11.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
                }
            }
            SosButton(52)  { open("sos") }
        }
    }
}

@Composable
fun SosButton(sizeDp: Int, onClick: () -> Unit) {
    val g = LocalGlove.current
    PopSurface(Modifier.size((sizeDp * g).dp), colors = popColors(PopKind.Danger), onClick = onClick, shape = CircleShape) {
        Text("SOS", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.5.sp)
    }
}

/* ---------------- speed ---------------- */

@Composable
fun SpeedPanel(ride: RideState, s: RiderSettings, compact: Boolean, open: (String) -> Unit, act: MainActivity, modifier: Modifier) {
    val p = LocalPal.current
    val over = ride.speedKmh > s.alertSpeed + 2
    val range = RiderApp.prefs.rangeKm(ride.fuelKm)
    val live by com.riderdeck.app.Recorder.live.collectAsState()
    val now = rememberNow(live.active)
    val rideSecs = live.rideSecs(now)
    Column(
        modifier.panel().pressable { open("ride") }.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val lean: @Composable () -> Unit = {
            LeanGauge(ride.leanDeg) {
                act.tracker.calibrateLean()
                Hub.toast("Lean set to zero. Keep the bike upright")
            }
        }
        val limitSign: @Composable () -> Unit = {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White).border(4.dp, Color(0xFFE5383B), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${s.alertSpeed}", color = Color(0xFF111111), fontWeight = FontWeight.Black, fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            lean()
            Spacer(Modifier.weight(1f))
            limitSign()
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = if (maxWidth < maxHeight * 1.12f) maxWidth else maxHeight * 1.12f
            SpeedRing(ride.speedKmh, over, s.alertSpeed, Modifier.size(side))
        }
        if (compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                MiniStat("%.1f".format(live.km), "km")
                val mins = (rideSecs / 60).toInt()
                if (mins < 60) MiniStat("$mins", "min") else MiniStat("${mins / 60}:${(mins % 60).toString().padStart(2, '0')}", "h")
                MiniStat("$range", "km left", if (range < 25) p.warn else p.fg)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("%.1f".format(live.km), if (live.active || live.startAt == 0L) "Ride km" else "Last ride km", Modifier.weight(1f))
                    Stat(duration(rideSecs), "Ride time", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("${live.avgKmh(now).roundToInt()}", "Avg km/h", Modifier.weight(1f))
                    Stat("$range", "Range km", Modifier.weight(1f), if (range < 25) p.warn else p.fg)
                }
            }
        }
    }
}

/** Front view of the bike tilting with your lean angle. Tap to set zero. */
@Composable
private fun LeanGauge(lean: Float, onCalibrate: () -> Unit) {
    val p = LocalPal.current
    val shown by animateFloatAsState(lean, spring(dampingRatio = 0.7f, stiffness = 120f), label = "lean")
    Row(
        Modifier.pressable(onCalibrate),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(30.dp)) {
            val w = size.width
            val h = size.height
            val pivot = Offset(w / 2, h * 0.92f)
            drawLine(p.faint, Offset(0f, pivot.y), Offset(w, pivot.y), strokeWidth = 2f, cap = StrokeCap.Round)
            rotate(shown, pivot) {
                drawRoundRect(
                    p.fg, topLeft = Offset(w / 2 - w * 0.09f, h * 0.30f), size = Size(w * 0.18f, h * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.09f),
                )
                drawLine(p.pop, Offset(w * 0.18f, h * 0.30f), Offset(w * 0.82f, h * 0.30f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawCircle(p.pop, w * 0.08f, Offset(w / 2, h * 0.14f))
            }
        }
        Column {
            Text("${abs(lean).roundToInt()}°", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 17.sp)
            Label("Lean")
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, color: Color = LocalPal.current.fg) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = color, maxLines = 1)
        Text(label, fontSize = 11.sp, color = p.muted, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(bottom = 1.dp))
    }
}

/** The clock, ticking every second while [ticking] (for the ride timer). */
@Composable
fun rememberNow(ticking: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ticking) {
        now = System.currentTimeMillis()
        while (ticking) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun duration(secs: Double): String {
    val m = (secs / 60).toInt()
    return if (m < 60) "$m min" else "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier, color: Color = LocalPal.current.fg) {
    val p = LocalPal.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(p.glass).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = color, maxLines = 1)
        Text(label.uppercase(), fontSize = 8.5.sp, color = p.muted, letterSpacing = 0.8.sp, maxLines = 1, fontWeight = FontWeight.Bold)
    }
}

/** Glowing gradient speed arc (cyan → violet → pink), red with a pulse above the limit. */
@Composable
fun SpeedRing(kmh: Float, over: Boolean, limit: Int, modifier: Modifier) {
    val p = LocalPal.current
    val anim by animateFloatAsState(kmh, spring(dampingRatio = 0.85f, stiffness = 60f), label = "speed")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        0.18f, 0.5f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "glow",
    )
    val hot = Color(0xFFFF4FA3)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val dim = min(size.width, size.height)
            val stroke = dim * 0.062f
            val inset = stroke * 1.7f
            val arcSize = Size(dim - inset * 2, dim - inset * 2)
            val tl = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            val c = Offset(size.width / 2, size.height / 2)
            val r = arcSize.width / 2
            val frac = (anim / 140f).coerceIn(0f, 1f)
            val limitFrac = (limit / 140f).coerceIn(0f, 1f)

            // track
            drawArc(p.fg.copy(alpha = 0.07f), 135f, 270f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // ticks
            for (v in 0..140 step 10) {
                val a = Math.toRadians(135.0 + 270.0 * v / 140)
                val major = v % 20 == 0
                val r1 = r - stroke * 1.2f
                val r2 = r - stroke * (if (major) 1.85f else 1.55f)
                drawLine(
                    if (major) p.muted.copy(alpha = 0.7f) else p.faint.copy(alpha = 0.6f),
                    Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                    Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()),
                    strokeWidth = if (major) dim * 0.009f else dim * 0.006f, cap = StrokeCap.Round,
                )
            }
            // value arc with glow (drawn from 0° after rotating, so the gradient runs along the arc)
            if (frac > 0.004f) {
                val brush = if (over) {
                    Brush.sweepGradient(listOf(p.bad, Color(0xFFFF8A65), p.bad), center = c)
                } else {
                    Brush.sweepGradient(0f to p.pop2, 0.4f to p.pop, 0.75f to hot, 1f to p.pop2, center = c)
                }
                rotate(135f, c) {
                    drawArc(brush, 0f, 270f * frac, false, tl, arcSize, alpha = if (over) glow else 0.22f, style = Stroke(stroke * 2.6f, cap = StrokeCap.Round))
                    drawArc(brush, 0f, 270f * frac, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                val ta = Math.toRadians(135.0 + 270.0 * frac)
                val tip = Offset(c.x + r * cos(ta).toFloat(), c.y + r * sin(ta).toFloat())
                drawCircle(Color.White.copy(alpha = 0.25f), stroke * 1.05f, tip)
                drawCircle(Color.White, stroke * 0.55f, tip)
            }
            // speed limit marker
            val la = Math.toRadians(135.0 + 270.0 * limitFrac)
            drawLine(
                p.bad,
                Offset(c.x + (r + stroke * 0.9f) * cos(la).toFloat(), c.y + (r + stroke * 0.9f) * sin(la).toFloat()),
                Offset(c.x + (r + stroke * 1.6f) * cos(la).toFloat(), c.y + (r + stroke * 1.6f) * sin(la).toFloat()),
                strokeWidth = dim * 0.018f, cap = StrokeCap.Round,
            )
        }
        val d = LocalDensity.current
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RollingNumber(
                kmh.roundToInt(), fontSize = with(d) { (side * 0.245f).toSp() },
                color = if (over) p.bad else p.fg,
            )
            Text("km/h", fontSize = with(d) { (side * 0.055f).toSp() }, color = p.muted, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/* ---------------- music ---------------- */

@Composable
fun musicPosition(m: MediaState): Long {
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val pos = if (m.playing) m.positionMs + ((tick - m.positionAtMs) * m.speed).toLong() else m.positionMs
    return if (m.durationMs > 0) pos.coerceIn(0, m.durationMs) else pos.coerceAtLeast(0)
}

fun mmss(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Little equaliser that dances while music plays. */
@Composable
fun EqBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "eq")
    val speeds = listOf(430, 610, 370, 520)
    val hs = speeds.map { ms ->
        tr.animateFloat(0.2f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse), label = "b$ms")
    }
    Row(modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        hs.forEach { h ->
            Box(Modifier.width(3.dp).fillMaxHeight(if (playing) h.value else 0.2f).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
fun MusicPanel(m: MediaState, listenerOn: Boolean, compact: Boolean, open: (String) -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val g = LocalGlove.current
    Box(modifier.panel()) {
        Crossfade(m.art, animationSpec = tween(600), label = "bg") { art ->
            if (art != null) {
                Image(art.asImageBitmap(), null, Modifier.fillMaxSize().blur(40.dp).alpha(if (p.dark) 0.45f else 0.3f), contentScale = ContentScale.Crop)
            }
        }
        when {
            !listenerOn -> BasicMusic(compact) { if (!RiderApp.isLite) Perms.openListenerSettings(ctx) else Media.openYtMusic(ctx) }
            !m.connected || m.title.isBlank() -> EmptyMusic("Nothing playing", "Open YT Music", compact) { Media.openYtMusic(ctx) }
            compact -> Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Art(m, 46, Modifier.pressable { open("music") })
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(m.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EqBars(m.playing, p.pop)
                            Text(m.artist, fontSize = 12.5.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    PlayButton(m.playing, (46 * g).toInt())
                    CtrlButton(Icons.Rounded.SkipNext, "Next") { Media.next() }
                }
                ProgressLine(m)
            }
            else -> Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dot(Color(0xFFFF3B30))
                    Label(m.appName.ifBlank { "Music" }, Modifier.weight(1f))
                    EqBars(m.playing, p.pop)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(m, 58, Modifier.pressable { open("music") })
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Crossfade(m.title, label = "title") { t ->
                            Text(t, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(m.artist, fontSize = 13.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column {
                    ProgressLine(m)
                    if (m.durationMs > 0) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(mmss(musicPosition(m)), fontSize = 11.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text(mmss(m.durationMs), fontSize = 11.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CtrlButton(Icons.Rounded.SkipPrevious, "Previous") { Media.prev() }
                    PlayButton(m.playing, (50 * g).toInt())
                    CtrlButton(Icons.Rounded.SkipNext, "Next") { Media.next() }
                }
            }
        }
    }
}

/** Music controls without notification access: media keys only, no song name. */
@Composable
private fun BasicMusic(compact: Boolean, onHint: () -> Unit) {
    val p = LocalPal.current
    val g = LocalGlove.current
    val hint = if (RiderApp.isLite) "Lite edition · buttons control YT Music" else "Tap to show song names"
    if (compact) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(p.glass).pressable(onHint),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MusicNote, null, tint = p.muted) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp).pressable(onHint)) {
                Text("Music", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(hint, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CtrlButton(Icons.Rounded.SkipPrevious, "Previous") { Media.prev() }
            PlayButton(false, (46 * g).toInt())
            CtrlButton(Icons.Rounded.SkipNext, "Next") { Media.next() }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Label("Music")
            Row(Modifier.pressable(onHint), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(p.glass), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = p.muted)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Play / pause / skip", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(hint, fontSize = 12.sp, color = p.muted, maxLines = 2)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CtrlButton(Icons.Rounded.SkipPrevious, "Previous") { Media.prev() }
                PlayButton(false, (50 * g).toInt())
                CtrlButton(Icons.Rounded.SkipNext, "Next") { Media.next() }
            }
        }
    }
}

@Composable
private fun EmptyMusic(text: String, button: String, compact: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    if (compact) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicNote, null, tint = p.muted)
            Text(text, Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 13.sp, color = p.muted, maxLines = 2)
            PopButton(button, kind = PopKind.Primary, height = 40.dp, onClick = onClick)
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = p.muted, modifier = Modifier.size(30.dp))
            Text(text, fontSize = 13.sp, color = p.muted)
            PopButton(button, kind = PopKind.Primary, shimmer = true, height = 44.dp, onClick = onClick)
        }
    }
}

@Composable
private fun Art(m: MediaState, sizeDp: Int, modifier: Modifier) {
    val p = LocalPal.current
    Box(
        modifier.size(sizeDp.dp).clip(RoundedCornerShape(14.dp)).background(p.glass),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(m.art, animationSpec = tween(500), label = "art") { art ->
            if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = p.fg) }
        }
    }
}

@Composable
private fun ProgressLine(m: MediaState) {
    val p = LocalPal.current
    val pos = musicPosition(m)
    val target = if (m.durationMs > 0) pos.toFloat() / m.durationMs else 0f
    val frac by animateFloatAsState(target.coerceIn(0f, 1f), tween(500, easing = LinearEasing), label = "prog")
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.fg.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(4.dp)).background(p.brand()))
    }
}

@Composable
fun PlayButton(playing: Boolean, sizeDp: Int) {
    PopSurface(Modifier.size(sizeDp.dp), colors = popColors(PopKind.Primary), onClick = { Media.toggle() }, shape = CircleShape) {
        Crossfade(playing, label = "play") { pl ->
            Icon(if (pl) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (pl) "Pause" else "Play", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun CtrlButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val g = LocalGlove.current
    Box(Modifier.size((46 * g).dp).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, modifier = Modifier.size(28.dp))
    }
}

/* ---------------- weather & volume ---------------- */

fun weatherIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0, 1 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2, 3, 45, 48 -> Icons.Rounded.Cloud
    in 51..67, in 80..82 -> Icons.Rounded.WaterDrop
    in 71..77 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

@Composable
fun InfoPanel(ride: RideState, dev: DeviceState, compact: Boolean, modifier: Modifier) {
    val p = LocalPal.current
    val w by Weather.state.collectAsState()
    val tint = when (w.code) {
        0, 1 -> if (w.isDay) p.warn else p.cyan
        in 51..67, in 80..82, in 95..99 -> p.blue
        else -> p.muted
    }
    val rain = w.rainInMin
    val rainText = when {
        w.tempC == null -> "Checking…"
        rain == null -> "Dry 2 hrs"
        rain == 0 -> "Raining"
        else -> "${w.rainChance}% rain in ~$rain min"
    }
    val weather: @Composable (Modifier) -> Unit = { m ->
        Column(m.pressable { Speaker.say(Weather.spoken()) }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(weatherIcon(w.code, w.isDay), null, tint = tint, modifier = Modifier.size(22.dp))
                Text(
                    w.tempC?.let { "${it.roundToInt()}°" } ?: "--°",
                    fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp, maxLines = 1, softWrap = false,
                )
            }
            Text(if (w.tempC == null) "Weather" else Weather.describe(w.code), fontSize = 12.5.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Umbrella, null, tint = if (rain != null) p.warn else p.ok, modifier = Modifier.size(14.dp))
                Text(rainText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (rain != null) p.warn else p.ok, maxLines = 2, lineHeight = 14.sp)
            }
        }
    }
    BoxWithConstraints(modifier.panel().padding(12.dp)) {
        if (compact) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                weather(Modifier.fillMaxWidth())
                VolumeKnob(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            val knob = minOf(maxHeight, maxWidth * 0.48f)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weather(Modifier.weight(1f))
                VolumeKnob(Modifier.size(knob))
            }
        }
    }
}
