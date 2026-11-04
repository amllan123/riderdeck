package com.riderdeck.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TwoWheeler
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.riderdeck.app.DeviceState
import com.riderdeck.app.Hub
import com.riderdeck.app.MainActivity
import com.riderdeck.app.Media
import com.riderdeck.app.MediaState
import com.riderdeck.app.Nav
import com.riderdeck.app.NavState
import com.riderdeck.app.Perms
import com.riderdeck.app.RideState
import com.riderdeck.app.RiderApp
import com.riderdeck.app.RiderSettings
import com.riderdeck.app.darkMapFilter
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
    var sheet by remember { mutableStateOf<String?>(null) }
    val open: (String) -> Unit = { sheet = it }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp)) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Rail(true, dev, open, Modifier.width(66.dp).fillMaxHeight())
                    SpeedPanel(ride, s, false, open, act, Modifier.weight(0.26f).fillMaxHeight())
                    MapPanel(ride, nav, act, open, Modifier.weight(0.47f).fillMaxHeight())
                    Column(Modifier.weight(0.27f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MusicPanel(media, listener, false, open, Modifier.weight(1.3f).fillMaxWidth())
                        TyrePanel(open, false, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusRow(ride, dev)
                    MapPanel(ride, nav, act, open, Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.fillMaxWidth().height(196.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SpeedPanel(ride, s, true, open, act, Modifier.weight(1.3f).fillMaxHeight())
                        TyrePanel(open, true, Modifier.weight(1f).fillMaxHeight())
                    }
                    MusicPanel(media, listener, true, open, Modifier.fillMaxWidth().height(78.dp))
                    Rail(false, dev, open, Modifier.fillMaxWidth().height(68.dp))
                }
            }
        }
        Overlays(act)
        sheet?.let { name -> Sheets(name, act, open) { sheet = null } }
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
        Text(clockText(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Dot(if (ride.hasFix) p.ok else p.warn)
                Text(if (ride.hasFix) "GPS" else "Finding GPS", fontSize = 12.sp, color = p.muted)
            }
            if (dev.tempC > 0) {
                Text("${dev.tempC.roundToInt()}°", fontSize = 12.sp, color = if (dev.tempC >= 43) p.bad else p.muted)
            }
            Text("${dev.batteryPct}%", fontSize = 12.sp, color = if (dev.batteryPct in 1..15) p.bad else p.muted)
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
        Column(
            modifier.panel(28.dp).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(clockText(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${dev.batteryPct}%", fontSize = 11.sp, color = p.muted)
                if (dev.tempC >= 40f) Text("${dev.tempC.roundToInt()}°", fontSize = 11.sp, color = if (dev.tempC >= 43f) p.bad else p.warn)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                railItems.forEach { item ->
                    Box(
                        Modifier.size(52.dp, (46 * g).dp).clip(RoundedCornerShape(16.dp)).clickable { open(item.key) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(item.icon, item.label, tint = p.muted) }
                }
            }
            SosButton(52) { open("sos") }
        }
    } else {
        Row(modifier.panel(26.dp).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            railItems.forEach { item ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).clickable { open(item.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, null, tint = p.muted)
                    Text(item.label, fontSize = 11.sp, color = p.muted)
                }
            }
            SosButton(54) { open("sos") }
        }
    }
}

@Composable
fun SosButton(sizeDp: Int, onClick: () -> Unit) {
    val p = LocalPal.current
    val g = LocalGlove.current
    Box(
        Modifier.size((sizeDp * g).dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFF8A92), p.bad), center = Offset(30f, 30f)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("SOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp) }
}

/* ---------------- speed ---------------- */

@Composable
fun SpeedPanel(ride: RideState, s: RiderSettings, compact: Boolean, open: (String) -> Unit, act: MainActivity, modifier: Modifier) {
    val p = LocalPal.current
    val over = ride.speedKmh > s.alertSpeed + 2
    val range = RiderApp.prefs.rangeKm(ride.fuelKm)
    Column(
        modifier.panel().clickable { open("ride") }.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.clickable { act.tracker.calibrateLean(); Hub.toast("Lean set to zero. Keep the bike upright") }) {
                Text("${abs(ride.leanDeg).roundToInt()}°", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Label("Lean")
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White).border(4.dp, Color(0xFFE5383B), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${s.alertSpeed}", color = Color(0xFF111111), fontWeight = FontWeight.Black, fontSize = 12.sp) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = if (maxWidth < maxHeight * 1.15f) maxWidth else maxHeight * 1.15f
            SpeedRing(ride.speedKmh, over, Modifier.size(side))
        }
        if (compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("%.1f".format(ride.tripKm), "Trip km", Modifier.weight(1f))
                Stat(duration(ride.movingSecs), "Time", Modifier.weight(1f))
                Stat("$range", "Range km", Modifier.weight(1f), if (range < 25) p.warn else p.fg)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("%.1f".format(ride.tripKm), "Trip km", Modifier.weight(1f))
                    Stat(duration(ride.movingSecs), "Time", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("${ride.avgKmh.roundToInt()}", "Avg km/h", Modifier.weight(1f))
                    Stat("$range", "Range km", Modifier.weight(1f), if (range < 25) p.warn else p.fg)
                }
            }
        }
    }
}

fun duration(secs: Double): String {
    val m = (secs / 60).toInt()
    return "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier, color: Color = LocalPal.current.fg) {
    val p = LocalPal.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(p.glass).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color, maxLines = 1)
        Text(label.uppercase(), fontSize = 9.sp, color = p.muted, letterSpacing = 0.6.sp, maxLines = 1)
    }
}

@Composable
fun SpeedRing(kmh: Float, over: Boolean, modifier: Modifier) {
    val p = LocalPal.current
    val anim by animateFloatAsState(kmh, tween(450), label = "speed")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val dim = min(size.width, size.height)
            val stroke = dim * 0.065f
            val inset = stroke / 2 + dim * 0.03f
            val arc = Size(dim - inset * 2, dim - inset * 2)
            val tl = Offset((size.width - arc.width) / 2, (size.height - arc.height) / 2)
            val c = Offset(size.width / 2, size.height / 2)
            val r = arc.width / 2
            drawArc(p.glass, 135f, 270f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            val frac = (anim / 140f).coerceIn(0f, 1f)
            val brush = Brush.sweepGradient(
                0f to p.warn, 0.125f to p.bad, 0.13f to p.bad, 0.375f to p.blue, 0.75f to p.cyan, 1f to p.warn,
                center = c,
            )
            if (frac > 0.003f) {
                drawArc(if (over) SolidColor(p.bad) else brush, 135f, 270f * frac, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            for (v in 0..140 step 10) {
                val a = Math.toRadians(135.0 + 270.0 * v / 140)
                val major = v % 20 == 0
                val r1 = r - stroke * (if (major) 1.9f else 1.5f)
                val r2 = r - stroke * 1.0f
                drawLine(
                    if (major) p.muted else p.faint,
                    Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                    Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()),
                    strokeWidth = if (major) 3f else 2f, cap = StrokeCap.Round,
                )
            }
            val ta = Math.toRadians(135.0 + 270.0 * frac)
            drawCircle(p.fg, stroke * 0.55f, Offset(c.x + r * cos(ta).toFloat(), c.y + r * sin(ta).toFloat()))
        }
        val d = LocalDensity.current
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val big = with(d) { (side * 0.30f).toSp() }
            Text(
                kmh.roundToInt().toString(), fontSize = big, lineHeight = big, fontWeight = FontWeight.Black,
                color = if (over) p.bad else p.fg, letterSpacing = (-1).sp,
            )
            Text("KM/H", fontSize = with(d) { (side * 0.055f).toSp() }, color = p.muted, letterSpacing = 3.sp)
        }
    }
}

/* ---------------- map ---------------- */

@Composable
fun MapPanel(ride: RideState, nav: NavState, act: MainActivity, open: (String) -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    Box(modifier.panel()) {
        OsmMap(ride, p.dark, Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.Center).size(26.dp).clip(CircleShape).background(Color.White).padding(5.dp)
                .clip(CircleShape).background(p.blue)
        )
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(p.panel.copy(alpha = 0.9f), Color.Transparent))))
        TurnCard(nav, Modifier.align(Alignment.TopStart).padding(10.dp)) { if (nav.active) Nav.openMaps(act) else open("goto") }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            RoundFab(Icons.Rounded.Search, "Nearby", false) { open("near") }
            RoundFab(Icons.Rounded.Mic, "Voice command", true) { act.startVoice() }
        }
        if (!ride.hasFix) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(99.dp))
                    .background(p.sheet.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Dot(p.warn)
                Text("Finding GPS…", fontSize = 12.sp, color = p.fg)
            }
        } else if (nav.active && nav.extra.isNotBlank()) {
            Text(
                nav.extra, fontSize = 13.sp, color = p.ok, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(14.dp))
                    .background(p.sheet.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun RoundFab(icon: ImageVector, desc: String, primary: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val g = LocalGlove.current
    val sz = ((if (primary) 56 else 46) * g).dp
    Box(
        Modifier.size(sz).clip(if (primary) CircleShape else RoundedCornerShape(16.dp))
            .background(if (primary) Brush.linearGradient(listOf(p.blue, p.cyan)) else SolidColor(p.sheet.copy(alpha = 0.9f)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (primary) Color.White else p.fg) }
}

@Composable
fun TurnCard(nav: NavState, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(22.dp)).background(p.sheet.copy(alpha = 0.94f))
            .border(1.dp, p.stroke, RoundedCornerShape(22.dp)).clickable(onClick = onClick)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(p.blue, p.cyan))),
            contentAlignment = Alignment.Center,
        ) {
            val icon = nav.icon
            if (nav.active && icon != null) {
                Image(icon.asImageBitmap(), null, Modifier.size(36.dp), colorFilter = ColorFilter.tint(Color.White))
            } else {
                Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Column {
            if (nav.active) {
                Text(nav.distance, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(nav.instruction, fontSize = 13.5.sp, color = p.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text("Where to?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Tap to start a two-wheeler route", fontSize = 13.sp, color = p.muted)
            }
        }
    }
}

@Composable
fun OsmMap(ride: RideState, dark: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            controller.setZoom(17.0)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    LaunchedEffect(dark) {
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) darkMapFilter() else null)
        map.invalidate()
    }
    LaunchedEffect(ride.lat, ride.lon) {
        if (ride.hasFix) {
            map.controller.animateTo(GeoPoint(ride.lat, ride.lon))
            if (ride.speedKmh > 6f) map.mapOrientation = -ride.bearing
        }
    }
    AndroidView(factory = { map }, modifier = modifier)
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

@Composable
fun MusicPanel(m: MediaState, listenerOn: Boolean, compact: Boolean, open: (String) -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val g = LocalGlove.current
    Box(modifier.panel()) {
        val art = m.art
        if (art != null) {
            Image(art.asImageBitmap(), null, Modifier.fillMaxSize().blur(36.dp).alpha(0.4f), contentScale = ContentScale.Crop)
        }
        when {
            !listenerOn -> BasicMusic(compact) { if (!RiderApp.isLite) Perms.openListenerSettings(ctx) else Media.openYtMusic(ctx) }
            !m.connected || m.title.isBlank() -> EmptyMusic("Nothing playing", "Open YT Music", compact) { Media.openYtMusic(ctx) }
            compact -> Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Art(m, 46, Modifier.clickable { open("music") })
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(m.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.artist, fontSize = 12.5.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PlayButton(m.playing, (48 * g).toInt())
                    CtrlButton(Icons.Rounded.SkipNext, "Next") { Media.next() }
                }
                ProgressLine(m)
            }
            else -> Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(Color(0xFFFF3B30))
                    Label(m.appName.ifBlank { "Music" })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(m, 58, Modifier.clickable { open("music") })
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(m.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.artist, fontSize = 13.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column {
                    ProgressLine(m)
                    if (m.durationMs > 0) {
                        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                            Text(mmss(musicPosition(m)), fontSize = 11.sp, color = p.muted)
                            Spacer(Modifier.weight(1f))
                            Text(mmss(m.durationMs), fontSize = 11.sp, color = p.muted)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CtrlButton(Icons.Rounded.SkipPrevious, "Previous") { Media.prev() }
                    PlayButton(m.playing, (52 * g).toInt())
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
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(p.glass).clickable(onClick = onHint),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MusicNote, null, tint = p.muted) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable(onClick = onHint)) {
                Text("Music", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(hint, fontSize = 12.sp, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CtrlButton(Icons.Rounded.SkipPrevious, "Previous") { Media.prev() }
            PlayButton(false, (48 * g).toInt())
            CtrlButton(Icons.Rounded.SkipNext, "Next") { Media.next() }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Label("Music")
            Row(Modifier.clickable(onClick = onHint), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(p.glass), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = p.muted)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Play / pause / skip", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(hint, fontSize = 12.sp, color = p.muted, maxLines = 2)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CtrlButton(Icons.Rounded.SkipPrevious, "Previous") { Media.prev() }
                PlayButton(false, (52 * g).toInt())
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
            PillButton(button, primary = true, onClick = onClick)
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = p.muted, modifier = Modifier.size(30.dp))
            Text(text, fontSize = 13.sp, color = p.muted)
            PillButton(button, primary = true, onClick = onClick)
        }
    }
}

@Composable
private fun Art(m: MediaState, sizeDp: Int, modifier: Modifier) {
    val p = LocalPal.current
    val art = m.art
    Box(
        modifier.size(sizeDp.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF7A45), Color(0xFF7C3AED)))),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = p.fg)
    }
}

@Composable
private fun ProgressLine(m: MediaState) {
    val p = LocalPal.current
    val pos = musicPosition(m)
    val frac = if (m.durationMs > 0) pos.toFloat() / m.durationMs else 0f
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.glass)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(p.fg))
    }
}

@Composable
fun PlayButton(playing: Boolean, sizeDp: Int) {
    val p = LocalPal.current
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(p.fg).clickable { Media.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = p.bg, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun CtrlButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val g = LocalGlove.current
    Box(Modifier.size((46 * g).dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, modifier = Modifier.size(28.dp))
    }
}

/* ---------------- tyres ---------------- */

@Composable
fun TyrePanel(open: (String) -> Unit, compact: Boolean, modifier: Modifier) {
    val p = LocalPal.current
    Column(
        modifier.panel().clickable { open("tyres") }.padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Tyres")
            Spacer(Modifier.weight(1f))
            Text("Not set up", fontSize = 11.5.sp, color = p.faint)
        }
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TyreValue("Front")
                TyreValue("Rear")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TyreValue("Front", Modifier.weight(1f))
                Icon(Icons.Rounded.TwoWheeler, null, tint = p.muted, modifier = Modifier.size(36.dp))
                TyreValue("Rear", Modifier.weight(1f), end = true)
            }
        }
        Text("Tap to add tyre sensors", fontSize = 12.sp, color = p.blue)
    }
}

@Composable
private fun TyreValue(label: String, modifier: Modifier = Modifier, end: Boolean = false) {
    val p = LocalPal.current
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Label(label)
        Text("--", fontSize = 22.sp, fontWeight = FontWeight.Black, color = p.faint)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(p.glass))
    }
}
