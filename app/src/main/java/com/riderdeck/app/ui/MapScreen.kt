package com.riderdeck.app.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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

/** Google Maps navigation greens, on NeoPOP slabs. */
private val NavGreen = Color(0xFF146C3B)
private val NavGreenDark = Color(0xFF0D4F2A)
private val NavColors = PopColors(NavGreen, Color.White, gradient = listOf(Color(0xFF1C8C4D), Color(0xFF0F6A37)))
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
        ru.navigating -> 82.dp
        routeCard -> 130.dp
        else -> 0.dp
    }

    BoxWithConstraints(modifier.panel()) {
        val h = maxHeight
        // While guiding, your arrow sits low on the screen so you see more road ahead (like Google Maps).
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

        Column(
            Modifier.align(Alignment.TopStart).padding(10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                ru.navigating && ru.next != null -> TurnBanner(
                    key = ru.next!!.begin, icon = maneuverIcon(ru.next!!.type), bitmap = null,
                    distance = shortDistance(ru.toNextM), street = streetOf(ru.next!!.instruction),
                    then = ru.after,
                ) { Hub.follow.value = true }
                nav.active -> TurnBanner(
                    key = nav.instruction.hashCode(), icon = Icons.Rounded.Navigation, bitmap = nav.icon,
                    distance = nav.distance, street = nav.instruction, then = null,
                ) { Nav.openMaps(act) }
                !routeCard -> SearchBar(onSearch = { open("goto") }, onMic = { act.startVoice() })
            }
            AnimatedVisibility(
                visible = pin != null && !routeCard,
                enter = slideInVertically(spring(dampingRatio = 0.7f)) { -it } + fadeIn(),
                exit = fadeOut(tween(150)),
            ) { pin?.let { PinCard(it) } }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = bottomCard + 12.dp),
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

        Box(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = bottomCard + 12.dp)) {
            when {
                !ride.hasFix -> StatusChip("Finding GPS…", p.warn)
                !follow -> PopButton("Re-centre", kind = PopKind.Plain, icon = Icons.Rounded.Navigation, height = 40.dp) { Hub.follow.value = true }
                nav.active && nav.extra.isNotBlank() && ru.route == null -> StatusChip(nav.extra, EtaGreen)
            }
        }

        AnimatedVisibility(
            visible = routeCard,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 300f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
        ) {
            RouteSheet(ru, act, { overviewKey++ }, Modifier.padding(10.dp).fillMaxWidth())
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
    key: Int,
    icon: ImageVector,
    bitmap: android.graphics.Bitmap?,
    distance: String,
    street: String,
    then: Maneuver?,
    onClick: () -> Unit,
) {
    Column(Modifier.widthIn(max = 440.dp)) {
        PopSurface(Modifier, colors = NavColors, onClick = onClick, contentAlignment = Alignment.CenterStart, shape = RoundedCornerShape(22.dp)) {
            AnimatedContent(
                targetState = Triple(key, icon, street),
                transitionSpec = {
                    (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(180)) { -it / 3 } + fadeOut(tween(150)))
                },
                label = "turn",
            ) { (_, ic, st) ->
                Row(
                    Modifier.padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x26FFFFFF)), contentAlignment = Alignment.Center) {
                        if (bitmap != null) {
                            Image(bitmap.asImageBitmap(), null, Modifier.size(36.dp), colorFilter = ColorFilter.tint(Color.White))
                        } else {
                            Icon(ic, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                    Column {
                        Text(distance, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp, maxLines = 1)
                        Text(
                            st, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (then != null) {
            Row(
                Modifier.padding(start = 18.dp).clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(NavGreenDark).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Then", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Icon(maneuverIcon(then.type), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SearchBar(onSearch: () -> Unit, onMic: () -> Unit) {
    val p = LocalPal.current
    val g = LocalGlove.current
    PopSurface(Modifier.widthIn(max = 440.dp).fillMaxWidth(), colors = popColors(PopKind.Plain), onClick = onSearch, contentAlignment = Alignment.CenterStart, shape = RoundedCornerShape(50)) {
        Row(
            Modifier.fillMaxWidth().height((52 * g).dp).padding(start = 16.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Search, null, tint = p.muted)
            Text("Where to?", color = p.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PopIcon(Icons.Rounded.Mic, "Voice command", kind = PopKind.Accent, size = 42.dp, onClick = onMic)
        }
    }
}

@Composable
private fun PinCard(pin: Pin) {
    val p = LocalPal.current
    val km = PlaceSearch.distanceFromMe(pin.lat, pin.lon)
    PopSurface(Modifier.widthIn(max = 440.dp).fillMaxWidth(), colors = popColors(PopKind.Plain), contentAlignment = Alignment.CenterStart, shape = RoundedCornerShape(22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Place, null, tint = Color(0xFFEA4335))
            Column(Modifier.weight(1f)) {
                Text(pin.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (km != null) Text(formatKm(km), fontSize = 12.5.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
            }
            PopButton("Route", kind = PopKind.Accent, icon = Icons.Rounded.Navigation, height = 40.dp) { Navigator.preview(pin) }
            PopIcon(Icons.Rounded.Close, "Clear", kind = PopKind.Secondary, size = 38.dp) { Navigator.stop() }
        }
    }
}

/* ---------------- bottom ---------------- */

private fun shareEta(act: MainActivity, dest: String, arrive: String, left: String) {
    val r = Hub.ride.value
    val where = if (r.hasFix) "\nWhere I am now: https://maps.google.com/?q=%.5f,%.5f".format(Locale.ROOT, r.lat, r.lon) else ""
    val text = "On my way to $dest. Reaching around $arrive ($left to go).$where"
    try {
        act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share ETA"))
    } catch (_: Exception) {
        Hub.toast("No app to share with")
    }
}

@Composable
private fun RouteSheet(ru: RouteUi, act: MainActivity, onOverview: () -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    val r = ru.route
    val green = if (p.dark) EtaGreenNight else EtaGreen
    PopSurface(modifier.widthIn(max = 520.dp), colors = popColors(PopKind.Plain), contentAlignment = Alignment.TopStart, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (r == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Dot(p.pop)
                    Text("Finding the best bike route…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    PopIcon(Icons.Rounded.Close, "Cancel", kind = PopKind.Secondary, size = 38.dp) { Navigator.stop() }
                }
                return@Column
            }
            val meters = if (ru.navigating) ru.remainingM else r.lengthM
            val secs = if (ru.navigating) ru.remainingS else r.timeS
            val mins = (secs / 60).roundToInt().coerceAtLeast(1)
            val km = if (meters >= 10_000) "${(meters / 1000).roundToInt()} km" else "%.1f km".format(meters / 1000)
            val arrive = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(System.currentTimeMillis() + (secs * 1000).toLong()))
            val timeText = if (mins >= 60) "${mins / 60} hr ${mins % 60} min" else "$mins min"

            if (ru.navigating) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PopIcon(Icons.Rounded.Close, "End navigation", kind = PopKind.Danger, size = 42.dp) { Navigator.stop() }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (mins < 60) {
                                RollingNumber(mins, fontSize = 24.sp, color = green)
                                Text(" min", color = green, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 2.dp))
                            } else {
                                Text(timeText, color = green, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text("$km · $arrive", color = p.muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    PopIcon(Icons.Rounded.Share, "Share ETA", kind = PopKind.Secondary, size = 42.dp) { shareEta(act, r.dest.title, arrive, timeText) }
                    PopIcon(Icons.Rounded.ZoomOutMap, "Route overview", kind = PopKind.Secondary, size = 42.dp, onClick = onOverview)
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(timeText, color = green, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text("($km)", color = p.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
                        }
                        Text(
                            "Arrive $arrive · ${r.dest.title}", color = p.muted, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PopIcon(Icons.Rounded.Close, "Cancel route", kind = PopKind.Secondary, size = 38.dp) { Navigator.stop() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PopButton("Start", Modifier.weight(1f), kind = PopKind.Accent, icon = Icons.Rounded.Navigation, shimmer = true, height = 46.dp) { Navigator.start() }
                    PopButton("Google Maps", Modifier.weight(1f), kind = PopKind.Secondary, height = 46.dp) { Nav.navigateTo(act, "${r.dest.lat},${r.dest.lon}") }
                }
            }
        }
    }
}

/* ---------------- small parts ---------------- */

@Composable
fun MapButton(icon: ImageVector, desc: String, @Suppress("UNUSED_PARAMETER") tint: Color = LocalPal.current.blue, onClick: () -> Unit) {
    PopIcon(icon, desc, kind = PopKind.Plain, size = 46.dp, onClick = onClick)
}

@Composable
private fun StatusChip(text: String, dot: Color) {
    val p = LocalPal.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(p.sheet.copy(alpha = 0.94f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Dot(dot)
        Text(text, fontSize = 13.sp, color = p.fg, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

fun formatKm(km: Float): String =
    if (km < 1f) "${(km * 1000).roundToInt()} m away" else if (km < 100f) "%.1f km away".format(km) else "${km.roundToInt()} km away"

/** Square NeoPOP button used in sheets (accent when primary). */
@Composable
fun RoundFab(icon: ImageVector, desc: String, primary: Boolean, onClick: () -> Unit) {
    PopIcon(icon, desc, kind = if (primary) PopKind.Accent else PopKind.Plain, size = if (primary) 52.dp else 46.dp, onClick = onClick)
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
