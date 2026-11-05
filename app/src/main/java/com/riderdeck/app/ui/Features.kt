package com.riderdeck.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.riderdeck.app.DocType
import com.riderdeck.app.Docs
import com.riderdeck.app.Hub
import com.riderdeck.app.MainActivity
import com.riderdeck.app.Navigator
import com.riderdeck.app.Parking
import com.riderdeck.app.Pin
import com.riderdeck.app.Recorder
import com.riderdeck.app.RideRecord
import com.riderdeck.app.Rides
import com.riderdeck.app.RiderApp
import com.riderdeck.app.Service
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ======================= rides ======================= */

@Composable
fun RideSheet(act: MainActivity) {
    val p = LocalPal.current
    val ride by Hub.ride.collectAsState()
    val rides by Rides.list.collectAsState()
    val recording by Recorder.active.collectAsState()
    var selected by remember { mutableStateOf<RideRecord?>(null) }
    val sel = selected
    if (sel != null) {
        RideDetail(sel, onBack = { selected = null }) {
            Rides.delete(sel)
            selected = null
            Hub.toast("Ride deleted")
        }
        return
    }
    val best = RiderApp.prefs.best060
    SheetHeader("Rides", if (recording) "Recording this ride · it saves when you stop for 4 minutes" else "Rides record by themselves once you pass 12 km/h")
    Label("Since you opened RiderDeck")
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Hero("%.1f".format(ride.tripKm), "km", Modifier.weight(1f))
        Hero(duration(ride.movingSecs), "moving time", Modifier.weight(1f))
        Hero("${ride.avgKmh.roundToInt()}", "avg km/h", Modifier.weight(1f))
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Hero("${ride.topKmh.roundToInt()}", "top km/h", Modifier.weight(1f))
        Hero("${ride.maxLeanDeg.roundToInt()}°", "max lean", Modifier.weight(1f))
        Hero(if (best > 0f) "%.1fs".format(best) else "--", "best 0–60", Modifier.weight(1f), p.pop.takeIf { best > 0f } ?: p.fg)
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Odometer %,.0f km".format(RiderApp.prefs.odoKm), color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        PopButton("Reset trip", kind = PopKind.Secondary, height = 40.dp) {
            act.tracker.resetTrip()
            Hub.toast("Trip reset")
        }
    }
    SectionTitle("Past rides")
    if (rides.isEmpty()) {
        Text(
            "No saved rides yet. Ride above 12 km/h and each ride shows up here with its route, speed and lean.",
            color = p.muted, fontSize = 14.sp, lineHeight = 20.sp,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rides.forEachIndexed { i, r ->
            PopSurface(Modifier.fillMaxWidth().enter(i.coerceAtMost(6)), colors = popColors(PopKind.Secondary), onClick = { selected = r }, contentAlignment = Alignment.CenterStart) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RouteSketch(r.points, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(p.bg), stroke = 2.5f)
                    Column(Modifier.weight(1f)) {
                        Text(rideDate(r.start), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
                        Text(
                            "%.1f km · %s · top %d".format(r.km, durationWords(r.movingSecs), r.topKmh.roundToInt()),
                            color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        r.zeroTo60?.let { Text("0–60 in %.1f s".format(it), color = p.pop, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = p.faint)
                }
            }
        }
    }
}

@Composable
private fun RideDetail(r: RideRecord, onBack: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPal.current
    var confirm by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PopIcon(Icons.AutoMirrored.Rounded.ArrowBack, "All rides", size = 40.dp, onClick = onBack)
        Label("All rides")
    }
    Spacer(Modifier.height(12.dp))
    SheetHeader(rideDate(r.start), "%.1f km in %s".format(r.km, durationWords(r.movingSecs)))
    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)).background(p.bg).enter(1)) {
        RouteSketch(r.points, Modifier.fillMaxSize(), stroke = 5f, animate = true)
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("< 25" to p.cyan, "25–50" to p.ok, "50–70" to p.warn, "70+" to p.bad).forEach { (t, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp, 4.dp).background(c))
                Text(t, fontSize = 11.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("km/h", fontSize = 11.sp, color = p.muted)
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Hero("%.1f".format(r.km), "km", Modifier.weight(1f))
        Hero(duration(r.movingSecs), "moving time", Modifier.weight(1f))
        Hero("${r.avgKmh.roundToInt()}", "avg km/h", Modifier.weight(1f))
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Hero("${r.topKmh.roundToInt()}", "top km/h", Modifier.weight(1f))
        Hero("${r.maxLean.roundToInt()}°", "max lean", Modifier.weight(1f))
        Hero(r.zeroTo60?.let { "%.1fs".format(it) } ?: "--", "0–60", Modifier.weight(1f))
    }
    PopButton(
        if (confirm) "Tap again to delete" else "Delete ride", Modifier.fillMaxWidth().padding(top = 16.dp),
        kind = if (confirm) PopKind.Danger else PopKind.Secondary, icon = Icons.Rounded.Delete,
    ) { if (confirm) onDelete() else confirm = true }
}

/** The ride's route drawn as a line, coloured by speed. */
@Composable
fun RouteSketch(points: List<FloatArray>, modifier: Modifier, stroke: Float = 3f, animate: Boolean = false) {
    val p = LocalPal.current
    var started by remember { mutableStateOf(!animate) }
    val progress by animateFloatAsState(if (started) 1f else 0f, spring(dampingRatio = 1f, stiffness = 25f), label = "draw")
    androidx.compose.runtime.LaunchedEffect(Unit) { started = true }
    Canvas(modifier.padding(6.dp)) {
        if (points.isEmpty()) return@Canvas
        val minLat = points.minOf { it[0] }
        val maxLat = points.maxOf { it[0] }
        val minLon = points.minOf { it[1] }
        val maxLon = points.maxOf { it[1] }
        val midLat = (minLat + maxLat) / 2f
        val k = cos(Math.toRadians(midLat.toDouble())).toFloat()
        val w = max((maxLon - minLon) * k, 1e-5f)
        val h = max(maxLat - minLat, 1e-5f)
        val scale = min(size.width / w, size.height / h)
        val ox = (size.width - w * scale) / 2f
        val oy = (size.height - h * scale) / 2f
        fun xy(pt: FloatArray) = Offset(ox + (pt[1] - minLon) * k * scale, oy + (maxLat - pt[0]) * scale)
        val sw = stroke * density
        val upto = (points.size * progress).toInt().coerceIn(1, points.size)
        for (i in 1 until upto) {
            val v = points[i][2]
            val c = when {
                v < 25f -> p.cyan
                v < 50f -> p.ok
                v < 70f -> p.warn
                else -> p.bad
            }
            drawLine(c, xy(points[i - 1]), xy(points[i]), strokeWidth = sw, cap = StrokeCap.Round)
        }
        drawCircle(p.fg, sw * 1.4f, xy(points.first()))
        drawCircle(p.bg, sw * 0.7f, xy(points.first()))
        if (upto == points.size) drawCircle(p.pop, sw * 1.5f, xy(points.last()))
    }
}

private fun rideDate(ms: Long): String = SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(Date(ms))

private fun durationWords(secs: Double): String {
    val m = (secs / 60).roundToInt()
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

fun ago(ms: Long): String {
    val m = ((System.currentTimeMillis() - ms) / 60_000).toInt()
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} days ago"
    }
}

/** "Where I parked" — saved automatically when a ride ends. */
@Composable
fun ParkingTile(park: Parking, close: () -> Unit) {
    val p = LocalPal.current
    Tile(Icons.Rounded.LocalParking, p.warn, "Where I parked", "Saved ${ago(park.at)} · plan a route back") {
        close()
        Navigator.preview(Pin(park.lat, park.lon, "Your bike"))
    }
}

/* ======================= service ======================= */

@Composable
fun ServiceSection() {
    val p = LocalPal.current
    var tick by remember { mutableIntStateOf(0) }
    val items = remember(tick) { Service.items() }
    SectionTitle("Service · odometer %,.0f km".format(RiderApp.prefs.odoKm))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { it ->
            val frac by animateFloatAsState(it.frac, spring(dampingRatio = 0.8f, stiffness = 120f), label = "svc")
            val col = when {
                it.due -> p.bad
                it.frac > 0.85f -> p.warn
                else -> p.ok
            }
            PopSurface(Modifier.fillMaxWidth(), colors = popColors(PopKind.Secondary), contentAlignment = Alignment.TopStart) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (it.due) "Due now" else "${it.left.roundToInt()} km left",
                            color = col, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(p.fg.copy(alpha = 0.1f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(6.dp)).background(col))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Every ${it.every.roundToInt()} km", color = p.muted, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                        PopButton("Done today", kind = PopKind.Secondary, icon = Icons.Rounded.Done, height = 36.dp) {
                            Service.markDone(it.key)
                            tick++
                            Hub.toast("${it.title} logged at %,.0f km".format(RiderApp.prefs.odoKm))
                        }
                    }
                }
            }
        }
    }
}

/* ======================= documents ======================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsSheet() {
    val ctx = LocalContext.current
    val ver by Docs.version.collectAsState()
    var pickFor by remember { mutableStateOf<String?>(null) }
    var dateFor by remember { mutableStateOf<String?>(null) }
    var viewKey by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = pickFor
        pickFor = null
        if (uri != null && key != null) {
            scope.launch {
                if (Docs.import(ctx, uri, key)) Hub.toast("Saved") else Hub.toast("Couldn't read that photo. Try another one")
            }
        }
    }
    SheetHeader("Documents", "Keep your papers ready for a police check. Photos stay only on this phone.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Docs.types.forEachIndexed { i, t ->
            DocRow(
                t, ver, Modifier.enter(i),
                onPhoto = {
                    pickFor = t.key
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onDate = { dateFor = t.key },
                onView = { viewKey = t.key },
            )
        }
    }

    dateFor?.let { key ->
        val state = rememberDatePickerState(initialSelectedDateMillis = Docs.expiry(key).takeIf { it > 0 } ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { dateFor = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { Docs.setExpiry(key, it) }
                    dateFor = null
                }) { Text("Save", fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { TextButton(onClick = { dateFor = null }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    viewKey?.let { key -> DocViewer(key) { viewKey = null } }
}

@Composable
private fun DocRow(t: DocType, ver: Int, modifier: Modifier, onPhoto: () -> Unit, onDate: () -> Unit, onView: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val thumb by produceState<Bitmap?>(null, t.key, ver) { value = Docs.load(ctx, t.key, 160) }
    val days = Docs.daysLeft(t.key)
    val has = thumb != null
    val (status, col) = when {
        !has -> "No photo yet" to p.muted
        !t.hasExpiry -> "Saved" to p.ok
        days == null -> "Add the expiry date" to p.warn
        days < 0 -> "Expired" to p.bad
        days <= 15 -> "Expires in $days days" to p.warn
        else -> "Valid till ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(Docs.expiry(t.key)))}" to p.ok
    }
    PopSurface(modifier.fillMaxWidth(), colors = popColors(PopKind.Secondary), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(width = 76.dp, height = 56.dp).clip(RoundedCornerShape(12.dp)).background(p.bg)
                    .pressable { if (has) onView() else onPhoto() },
                contentAlignment = Alignment.Center,
            ) {
                val b = thumb
                if (b != null) Image(b.asImageBitmap(), t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.Description, null, tint = p.faint)
            }
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
                Text(status, color = col, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
            }
            PopIcon(Icons.Rounded.AddAPhoto, if (has) "Change photo" else "Add photo", size = 40.dp, onClick = onPhoto)
            if (t.hasExpiry) PopIcon(Icons.Rounded.DateRange, "Set expiry date", size = 40.dp, onClick = onDate)
        }
    }
}

/** Full-screen document photo with pinch to zoom. */
@Composable
private fun DocViewer(key: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, key) { value = Docs.load(ctx, key, 2000) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            },
        ) {
            bmp?.let {
                Image(
                    it.asImageBitmap(), null,
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                    contentScale = ContentScale.Fit,
                )
            }
            Box(Modifier.safeDrawingPadding().padding(12.dp).align(Alignment.TopEnd)) {
                PopIcon(Icons.Rounded.Close, "Close", kind = PopKind.Primary, size = 46.dp, onClick = onClose)
            }
        }
    }
}
