package com.riderdeck.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.TurnSharpLeft
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.material.icons.rounded.TurnSlightLeft
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material.icons.rounded.UTurnRight
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riderdeck.app.BuildConfig
import com.riderdeck.app.Hub
import com.riderdeck.app.MainActivity
import com.riderdeck.app.Maneuver
import com.riderdeck.app.Nav
import com.riderdeck.app.NavState
import com.riderdeck.app.Navigator
import com.riderdeck.app.Pin
import com.riderdeck.app.PlaceSearch
import com.riderdeck.app.RideState
import com.riderdeck.app.RouteUi
import com.riderdeck.app.Speaker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Google Maps navigation greens. */
private val NavGreen = Color(0xFF146C3B)
private val NavGreenDark = Color(0xFF0D4F2A)
private val EtaGreen = Color(0xFF1E8E3E)
private val EtaGreenNight = Color(0xFF5BD47E)

@Composable
fun MapPanel(ride: RideState, nav: NavState, act: MainActivity, open: (String) -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    val pin by Hub.pin.collectAsState()
    val follow by Hub.follow.collectAsState()
    val ru by Navigator.state.collectAsState()
    val muted by Speaker.guidanceMuted.collectAsState()
    var overviewKey by remember { mutableIntStateOf(0) }
    val routeCard = ru.loading || ru.route != null
    val bottomCard: Dp = when {
        ru.navigating -> 84.dp
        routeCard -> 132.dp
        else -> 0.dp
    }

    BoxWithConstraints(modifier.panel()) {
        val h = maxHeight
        // While guiding, put your arrow low on the screen so you see more road ahead (like Google Maps).
        val pad = if (ru.navigating && follow) {
            val top = (h - bottomCard - 150.dp).coerceAtLeast(90.dp)
            PaddingValues(top = top, bottom = bottomCard)
        } else {
            PaddingValues(top = 84.dp, bottom = bottomCard)
        }
        if (BuildConfig.HAS_MAPS_KEY) {
            GoogleRiderMap(ride, p.dark, pin, follow, ru, pad, overviewKey, Modifier.fillMaxSize())
        } else {
            RiderMap(ride, p.dark, pin, follow, ru, Modifier.fillMaxSize())
        }

        // Top: green turn banner while navigating, Google-style search bar otherwise.
        Column(
            Modifier.align(Alignment.TopStart).padding(10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                ru.navigating && ru.next != null -> TurnBanner(
                    icon = maneuverIcon(ru.next!!.type), bitmap = null,
                    distance = shortDistance(ru.toNextM), street = streetOf(ru.next!!.instruction),
                    then = ru.after,
                ) { Hub.follow.value = true }
                nav.active -> TurnBanner(
                    icon = Icons.Rounded.Navigation, bitmap = nav.icon,
                    distance = nav.distance, street = nav.instruction, then = null,
                ) { Nav.openMaps(act) }
                !routeCard -> SearchBar(onSearch = { open("goto") }, onMic = { act.startVoice() })
            }
            if (pin != null && !routeCard) PinCard(pin!!)
        }

        // Right side buttons
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = bottomCard + 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (ru.route != null) {
                MapButton(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, if (muted) "Unmute voice guidance" else "Mute voice guidance") {
                    Speaker.guidanceMuted.value = !muted
                    if (muted) Speaker.say("Voice guidance on") else Speaker.stop()
                }
            }
            if (routeCard) MapButton(Icons.Rounded.Mic, "Voice command") { act.startVoice() }
        }

        // Bottom-left: re-centre / GPS / Google Maps ETA
        Box(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = bottomCard + 10.dp)) {
            when {
                !ride.hasFix -> StatusChip("Finding GPS…", p.warn)
                !follow -> RecenterPill { Hub.follow.value = true }
                nav.active && nav.extra.isNotBlank() && ru.route == null -> StatusChip(nav.extra, EtaGreen)
            }
        }

        if (routeCard) {
            RouteSheet(ru, act, { overviewKey++ }, Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth())
        }
    }
}

/* ---------------- top ---------------- */

/** "Turn right onto MG Road." → "MG Road" for the big banner text. */
fun streetOf(instruction: String): String {
    val t = instruction.trim().trimEnd('.')
    for (w in listOf("onto", "on to", "on", "towards", "toward", "to")) {
        val m = Regex("\\b$w\\s+(.+)$", RegexOption.IGNORE_CASE).find(t)
        val street = m?.groupValues?.get(1)
        if (street != null && street.length > 2) return street
    }
    return t
}

@Composable
private fun TurnBanner(
    icon: ImageVector,
    bitmap: android.graphics.Bitmap?,
    distance: String,
    street: String,
    then: Maneuver?,
    onClick: () -> Unit,
) {
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .background(NavGreen).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), null, Modifier.size(48.dp), colorFilter = ColorFilter.tint(Color.White))
            } else {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(distance, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp, maxLines = 1)
                Text(
                    street, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (then != null) {
            Row(
                Modifier.padding(start = 16.dp).clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .background(NavGreenDark).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Then", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(maneuverIcon(then.type), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SearchBar(onSearch: () -> Unit, onMic: () -> Unit) {
    val p = LocalPal.current
    val g = LocalGlove.current
    Row(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().height((52 * g).dp).shadow(6.dp, RoundedCornerShape(99.dp))
            .clip(RoundedCornerShape(99.dp)).background(p.sheet).clickable(onClick = onSearch).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.muted)
        Text("Search here", color = p.muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size((42 * g).dp).clip(CircleShape).clickable(onClick = onMic),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Mic, "Voice command", tint = p.blue) }
    }
}

@Composable
private fun PinCard(pin: Pin) {
    val p = LocalPal.current
    val km = PlaceSearch.distanceFromMe(pin.lat, pin.lon)
    Row(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(p.sheet).padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Place, null, tint = Color(0xFFEA4335))
        Column(Modifier.weight(1f)) {
            Text(pin.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (km != null) Text(formatKm(km), fontSize = 12.5.sp, color = p.muted)
        }
        Row(
            Modifier.clip(RoundedCornerShape(99.dp)).background(p.blue).clickable { Navigator.preview(pin) }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("Directions", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { Navigator.stop() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "Clear", tint = p.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/* ---------------- bottom ---------------- */

@Composable
private fun RouteSheet(ru: RouteUi, act: MainActivity, onOverview: () -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    val r = ru.route
    val green = if (p.dark) EtaGreenNight else EtaGreen
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.widthIn(max = 560.dp).shadow(10.dp, shape).clip(shape).background(p.sheet).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (r == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(p.blue)
                Text("Finding the best bike route…", fontSize = 15.sp, color = p.fg, modifier = Modifier.weight(1f))
                CircleBtn(Icons.Rounded.Close, "Cancel") { Navigator.stop() }
            }
            return@Column
        }
        val meters = if (ru.navigating) ru.remainingM else r.lengthM
        val secs = if (ru.navigating) ru.remainingS else r.timeS
        val mins = (secs / 60).roundToInt().coerceAtLeast(1)
        val time = if (mins >= 60) "${mins / 60} hr ${mins % 60} min" else "$mins min"
        val km = if (meters >= 10_000) "${(meters / 1000).roundToInt()} km" else "%.1f km".format(meters / 1000)
        val arrive = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(System.currentTimeMillis() + (secs * 1000).toLong()))

        if (ru.navigating) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleBtn(Icons.Rounded.Close, "End navigation") { Navigator.stop() }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(time, color = green, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("$km · $arrive", color = p.muted, fontSize = 14.sp, maxLines = 1)
                }
                CircleBtn(Icons.Rounded.ZoomOutMap, "Route overview", onClick = onOverview)
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(time, color = green, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text("($km)", color = p.muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    Text(
                        "Arrive $arrive · ${r.dest.title}", color = p.muted, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                CircleBtn(Icons.Rounded.Close, "Cancel route") { Navigator.stop() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).height((50 * LocalGlove.current).dp).clip(RoundedCornerShape(99.dp))
                        .background(p.blue).clickable { Navigator.start() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.Navigation, null, tint = Color.White)
                    Text("Start", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Row(
                    Modifier.weight(1f).height((50 * LocalGlove.current).dp).clip(RoundedCornerShape(99.dp))
                        .border(1.5.dp, p.stroke, RoundedCornerShape(99.dp))
                        .clickable { Nav.navigateTo(act, "${r.dest.lat},${r.dest.lon}") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Open in Google Maps", color = p.blue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

/* ---------------- small parts ---------------- */

@Composable
fun MapButton(icon: ImageVector, desc: String, tint: Color = LocalPal.current.blue, onClick: () -> Unit) {
    val p = LocalPal.current
    val sz = (48 * LocalGlove.current).dp
    Box(
        Modifier.size(sz).shadow(6.dp, CircleShape).clip(CircleShape).background(p.sheet).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun CircleBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val p = LocalPal.current
    val sz = (46 * LocalGlove.current).dp
    Box(Modifier.size(sz).clip(CircleShape).background(p.glass).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = p.fg)
    }
}

@Composable
private fun RecenterPill(onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.shadow(6.dp, RoundedCornerShape(99.dp)).clip(RoundedCornerShape(99.dp)).background(p.sheet)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Navigation, null, tint = p.blue, modifier = Modifier.size(20.dp))
        Text("Re-centre", color = p.blue, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun StatusChip(text: String, dot: Color) {
    val p = LocalPal.current
    Row(
        Modifier.shadow(4.dp, RoundedCornerShape(99.dp)).clip(RoundedCornerShape(99.dp)).background(p.sheet)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Dot(dot)
        Text(text, fontSize = 13.sp, color = p.fg, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

fun formatKm(km: Float): String =
    if (km < 1f) "${(km * 1000).roundToInt()} m away" else if (km < 100f) "%.1f km away".format(km) else "${km.roundToInt()} km away"

/** Big gradient button, used in sheets. */
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

fun maneuverIcon(type: Int): ImageVector = when (type) {
    9, 18, 20, 23 -> Icons.Rounded.TurnSlightRight
    10 -> Icons.Rounded.TurnRight
    11 -> Icons.Rounded.TurnSharpRight
    12 -> Icons.Rounded.UTurnRight
    13 -> Icons.Rounded.UTurnLeft
    14 -> Icons.Rounded.TurnSharpLeft
    15 -> Icons.Rounded.TurnLeft
    16, 19, 21, 24 -> Icons.Rounded.TurnSlightLeft
    4, 5, 6 -> Icons.Rounded.Flag
    26, 27 -> Icons.Rounded.Loop
    else -> Icons.Rounded.Straight
}

fun shortDistance(m: Double): String = when {
    m >= 1000 -> "%.1f km".format(m / 1000)
    m >= 100 -> "${(m / 10).roundToInt() * 10} m"
    else -> "${(m / 5).roundToInt() * 5} m"
}
