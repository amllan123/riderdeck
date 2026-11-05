package com.riderdeck.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.GroundOverlay
import com.google.maps.android.compose.GroundOverlayPosition
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.riderdeck.app.Hub
import com.riderdeck.app.LL
import com.riderdeck.app.Navigator
import com.riderdeck.app.Pin
import com.riderdeck.app.Recorder
import com.riderdeck.app.RideState
import com.riderdeck.app.Route
import com.riderdeck.app.RouteUi
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Day / sunlight: high contrast for glare — dark labels, outlined roads, no shop clutter. */
private const val DAY_STYLE = """[
{"elementType":"labels.text.fill","stylers":[{"color":"#1f2328"}]},
{"elementType":"labels.text.stroke","stylers":[{"color":"#ffffff"},{"weight":3}]},
{"featureType":"landscape.man_made","elementType":"geometry","stylers":[{"color":"#e3e6ea"}]},
{"featureType":"landscape.natural","elementType":"geometry","stylers":[{"color":"#eef0f2"}]},
{"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},
{"featureType":"poi.business","stylers":[{"visibility":"off"}]},
{"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#ffffff"}]},
{"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#8d949e"}]},
{"featureType":"road","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
{"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#ffd76a"}]},
{"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#b98a00"}]},
{"featureType":"transit","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
{"featureType":"water","elementType":"geometry","stylers":[{"color":"#9cc3ef"}]}
]"""

/** Night: the RiderDeck neon look — deep navy blocks, quiet streets, no clutter. */
private const val NIGHT_STYLE = """[
{"elementType":"geometry","stylers":[{"color":"#0c111b"}]},
{"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
{"elementType":"labels.text.fill","stylers":[{"color":"#6b7891"}]},
{"elementType":"labels.text.stroke","stylers":[{"color":"#090d15"}]},
{"featureType":"administrative","elementType":"geometry","stylers":[{"visibility":"off"}]},
{"featureType":"landscape.man_made","elementType":"geometry","stylers":[{"color":"#131a27"}]},
{"featureType":"landscape.natural","elementType":"geometry","stylers":[{"color":"#0c111b"}]},
{"featureType":"poi","stylers":[{"visibility":"off"}]},
{"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#0c1c19"}]},
{"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#1a2333"}]},
{"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#090d15"}]},
{"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#222c40"}]},
{"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#2a3753"}]},
{"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#94a0b6"}]},
{"featureType":"transit","stylers":[{"visibility":"off"}]},
{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0a1a2c"}]}
]"""

/** Soft blue light cone that points where you're heading (neon night look). */
private fun coneBitmap(px: Int): Bitmap {
    val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = android.graphics.RadialGradient(
        px / 2f, px.toFloat(), px.toFloat(),
        intArrayOf(0x993D8BFF.toInt(), 0x403D8BFF, 0x003D8BFF), floatArrayOf(0f, 0.45f, 1f),
        android.graphics.Shader.TileMode.CLAMP,
    )
    val path = Path().apply {
        moveTo(px / 2f, px.toFloat())
        lineTo(px * 0.5f - px * 0.42f, 0f)
        lineTo(px * 0.5f + px * 0.42f, 0f)
        close()
    }
    c.drawPath(path, p)
    return b
}

/** Google-blue navigation arrow with white rim and soft halo, like the one in Google Maps. */
private fun arrowBitmap(px: Int): Bitmap {
    val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val cx = px / 2f
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x331A73E8 }
    c.drawCircle(cx, cx, px * 0.48f, halo)
    val path = Path().apply {
        moveTo(cx, px * 0.12f)
        lineTo(px * 0.80f, px * 0.84f)
        lineTo(cx, px * 0.66f)
        lineTo(px * 0.20f, px * 0.84f)
        close()
    }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55000000
        style = Paint.Style.FILL
    }
    c.save()
    c.translate(0f, px * 0.025f)
    c.drawPath(path, shadow)
    c.restore()
    c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = px * 0.09f; strokeJoin = Paint.Join.ROUND })
    c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt(); style = Paint.Style.FILL })
    return b
}

/** Blue location dot with white rim, for when you're standing still. */
private fun dotBitmap(px: Int): Bitmap {
    val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val cx = px / 2f
    c.drawCircle(cx, cx, px * 0.48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x331A73E8 })
    c.drawCircle(cx, cx + px * 0.015f, px * 0.24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44000000 })
    c.drawCircle(cx, cx, px * 0.23f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    c.drawCircle(cx, cx, px * 0.17f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3D8BFF.toInt() })
    return b
}

/**
 * Smooth position between GPS fixes. GPS gives one fix a second, a little late; drawing it raw makes the
 * arrow hop and the map lurch. Instead the arrow keeps moving at your speed along your heading, small
 * corrections fade in over a third of a second, and while navigating it sits on the route line.
 */
private class Glide {
    var lat = 0.0
    var lon = 0.0
    var heading = 0f
    var has = false
        private set
    private var fixAt = Long.MIN_VALUE
    private var fLat = 0.0
    private var fLon = 0.0
    private var fSpeed = 0.0
    private var fBearing = 0f
    private var offLat = 0.0
    private var offLon = 0.0
    private var offAt = 0L
    private var lastMs = 0L

    fun step(r: RideState, nowMs: Long, ru: RouteUi) {
        if (r.fixAt != fixAt || !has) {
            if (!has || Navigator.dist(lat, lon, r.lat, r.lon) > 120) {
                // first fix or a real jump: go straight there
                lat = r.lat
                lon = r.lon
                if (!has) heading = r.bearing
                offLat = 0.0
                offLon = 0.0
            } else {
                offLat = lat - r.lat
                offLon = lon - r.lon
            }
            has = true
            offAt = nowMs
            fixAt = r.fixAt
            fLat = r.lat
            fLon = r.lon
            fSpeed = if (r.speedKmh >= 7f) r.speedKmh / 3.6 else 0.0
            fBearing = r.bearing
        }
        // keep moving along the heading for up to ~1 s after the fix (plus a bit for GPS delay)
        val since = if (r.fixAt > 0) (nowMs - r.fixAt) / 1000.0 else 0.0
        val ahead = fSpeed * (since + 0.25).coerceIn(0.0, 1.1)
        var dLat = fLat
        var dLon = fLon
        if (ahead > 0) {
            val b = Math.toRadians(fBearing.toDouble())
            dLat += ahead * cos(b) / 111_320.0
            dLon += ahead * sin(b) / (111_320.0 * cos(Math.toRadians(fLat)))
        }
        val fade = exp(-(nowMs - offAt) / 330.0)
        dLat += offLat * fade
        dLon += offLon * fade

        var target = when {
            r.speedKmh >= 7f -> fBearing
            ru.navigating && ru.heading != null -> ru.heading
            else -> heading
        }
        val route = ru.route
        if (ru.navigating && route != null) {
            val snap = snapToRoute(route, ru.doneIdx, dLat, dLon)
            if (snap != null && snap.third < 25.0) {
                dLat = snap.first.lat
                dLon = snap.first.lon
                if (r.speedKmh >= 7f || ru.heading == null) target = snap.second
            }
        }
        lat = dLat
        lon = dLon
        val dt = if (lastMs == 0L) 0.016 else ((nowMs - lastMs) / 1000.0).coerceIn(0.0, 0.25)
        lastMs = nowMs
        heading = easeAngle(heading, target, dt, 0.35)
    }
}

private fun norm360(a: Float): Float = ((a % 360f) + 360f) % 360f

/** Turn [from] toward [to] the short way round, with time constant [tau] seconds. */
private fun easeAngle(from: Float, to: Float, dt: Double, tau: Double): Float {
    var d = norm360(to) - norm360(from)
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    val k = (1.0 - exp(-dt / tau)).toFloat()
    return norm360(from + d * k)
}

private fun ease(from: Float, to: Float, dt: Double, tau: Double): Float = from + (to - from) * (1.0 - exp(-dt / tau)).toFloat()

/** Closest point on the route near where you are: (point, road bearing, distance in metres). */
private fun snapToRoute(r: Route, doneIdx: Int, lat: Double, lon: Double): Triple<LL, Float, Double>? {
    val pts = r.points
    if (pts.size < 2) return null
    val kx = 111_320.0 * cos(Math.toRadians(lat))
    val ky = 110_540.0
    var best: Triple<LL, Float, Double>? = null
    val from = (doneIdx - 3).coerceAtLeast(0)
    val to = (doneIdx + 40).coerceAtMost(pts.size - 2)
    for (i in from..to) {
        val a = pts[i]
        val b = pts[i + 1]
        val ax = (a.lon - lon) * kx
        val ay = (a.lat - lat) * ky
        val bx = (b.lon - lon) * kx
        val by = (b.lat - lat) * ky
        val vx = bx - ax
        val vy = by - ay
        val len2 = vx * vx + vy * vy
        if (len2 < 0.01) continue
        val t = (-(ax * vx + ay * vy) / len2).coerceIn(0.0, 1.0)
        val px = ax + vx * t
        val py = ay + vy * t
        val d = sqrt(px * px + py * py)
        if (best == null || d < best.third) {
            val brg = norm360(Math.toDegrees(atan2(vx, vy)).toFloat())
            best = Triple(LL(lat + py / ky, lon + px / kx), brg, d)
        }
    }
    return best
}

/**
 * Google Maps inside RiderDeck, styled like Google Maps navigation:
 * decluttered map, thick route with the part you've ridden in grey, a gliding arrow,
 * and a tilted heading-up camera that eases between modes instead of jumping.
 */
@Composable
fun GoogleRiderMap(
    dark: Boolean,
    pin: Pin?,
    follow: Boolean,
    ru: RouteUi,
    padding: PaddingValues,
    overviewKey: Int,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val cam = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(21.0, 78.9), 4f)
    }
    val day = remember { MapStyleOptions(DAY_STYLE) }
    val night = remember { MapStyleOptions(NIGHT_STYLE) }
    // Only these two facts about the ride change what's drawn; the rest moves per frame below.
    val rideState = Hub.ride.collectAsState()
    val hasFix by remember { derivedStateOf { rideState.value.hasFix } }
    val moving by remember { derivedStateOf { rideState.value.speedKmh > 6f } }

    val glide = remember { Glide() }
    val puck = remember { MarkerState() }
    val here = remember { mutableStateOf(LatLng(0.0, 0.0)) }
    val heading = remember { mutableFloatStateOf(0f) }
    val routeNow = rememberUpdatedState(ru)

    // Every frame: glide the arrow, and when following, ease the camera (position, zoom, tilt, heading).
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        var following = false
        var cLat = 0.0
        var cLon = 0.0
        var cZoom = 16.4f
        var cTilt = 0f
        var cBearing = 0f
        var ridingMode = false
        var slowSince = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (lastNanos == 0L) 0.016 else ((t - lastNanos) / 1e9).coerceIn(0.0, 0.25)
                if (lastNanos != 0L && t - lastNanos < 14_000_000L) return@withFrameNanos // ~60 fps is plenty
                lastNanos = t
                val r = Hub.ride.value
                if (!r.hasFix) return@withFrameNanos
                val now = SystemClock.elapsedRealtime()
                val route = routeNow.value
                glide.step(r, now, route)
                val pos = LatLng(glide.lat, glide.lon)
                puck.position = pos
                here.value = pos
                heading.floatValue = glide.heading

                if (!Hub.follow.value) {
                    following = false
                    return@withFrameNanos
                }
                if (cam.isMoving && cam.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
                    Hub.follow.value = false // a finger on the map stops it following you
                    following = false
                    return@withFrameNanos
                }
                // riding mode holds through short stops, so the camera doesn't swing at every signal
                if (r.speedKmh > 8f || Recorder.active.value) {
                    ridingMode = true
                    slowSince = 0L
                } else if (r.speedKmh < 3f) {
                    if (slowSince == 0L) slowSince = now
                    if (now - slowSince > 30_000) ridingMode = false
                }
                val nav = route.navigating
                val zoomT = if (nav) 17.6f else if (ridingMode) 17f else 16.4f
                val tiltT = if (nav) 60f else if (ridingMode) 45f else 0f
                if (!following) {
                    following = true
                    val p = cam.position
                    cZoom = p.zoom
                    cTilt = p.tilt
                    cBearing = p.bearing
                    cLat = p.target.latitude
                    cLon = p.target.longitude
                    if (cZoom < 10f) {
                        // first fix: start close instead of flying in from all of India
                        cZoom = zoomT; cLat = pos.latitude; cLon = pos.longitude
                    }
                }
                val far = Navigator.dist(cLat, cLon, pos.latitude, pos.longitude)
                if (far > 3000) {
                    cLat = pos.latitude; cLon = pos.longitude
                } else {
                    val k = if (far > 60) (1.0 - exp(-dt / 0.25)) else 1.0
                    cLat += (pos.latitude - cLat) * k
                    cLon += (pos.longitude - cLon) * k
                }
                cZoom = ease(cZoom, zoomT, dt, 0.7)
                cTilt = ease(cTilt, tiltT, dt, 0.7)
                if (nav || ridingMode) cBearing = easeAngle(cBearing, glide.heading, dt, 0.25)
                try {
                    cam.move(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition(LatLng(cLat, cLon), cZoom, cTilt, cBearing)
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun fitRoute() {
        val r = ru.route ?: return
        if (r.points.size < 2) return
        Hub.follow.value = false
        try {
            val b = LatLngBounds.Builder()
            r.points.forEach { b.include(LatLng(it.lat, it.lon)) }
            val me = Hub.ride.value
            if (me.hasFix) b.include(LatLng(me.lat, me.lon))
            val pad = with(density) { 48.dp.roundToPx() }
            cam.animate(CameraUpdateFactory.newLatLngBounds(b.build(), pad), 900)
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(ru.route) {
        if (ru.route != null && !ru.navigating) fitRoute()
    }
    LaunchedEffect(overviewKey) {
        if (overviewKey > 0) fitRoute()
    }

    LaunchedEffect(pin) {
        if (pin == null || ru.route != null || ru.loading) return@LaunchedEffect
        Hub.follow.value = false
        try {
            cam.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(LatLng(pin.lat, pin.lon)).zoom(16f).tilt(0f).bearing(0f).build()
                ),
                800,
            )
        } catch (_: Exception) {
        }
    }

    // Route colours: neon glow at night, deep blue with a dark edge in sunlight (glare-proof).
    val routeCore = if (dark) Color(0xFF3D8BFF) else Color(0xFF0B57D0)
    val routeEdge = if (dark) Color(0x593D8BFF) else Color(0xFF062E6F)
    val ridden = if (dark) Color(0xFF334058) else Color(0xFF9AA3AF)
    val glowW = with(density) { 26.dp.toPx() }
    val edgeW = with(density) { (if (dark) 15.dp else 13.dp).toPx() }
    val coreW = with(density) { (if (dark) 7.dp else 9.dp).toPx() }
    val shineW = with(density) { 2.dp.toPx() }
    val ringW = with(density) { 2.dp.toPx() }
    val puckPx = with(density) { 46.dp.roundToPx() }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cam,
        contentPadding = padding,
        properties = MapProperties(
            // flat dark blocks at night (the RiderDeck look), 3D buildings by day
            isBuildingEnabled = !dark,
            isTrafficEnabled = false,
            isMyLocationEnabled = false,
            mapStyleOptions = if (dark) night else day,
        ),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            indoorLevelPickerEnabled = false,
            tiltGesturesEnabled = true,
            rotationGesturesEnabled = true,
        ),
    ) {
        val r = ru.route
        if (r != null) {
            val pts = remember(r) { r.points.map { LatLng(it.lat, it.lon) } }
            val split = ru.doneIdx.coerceIn(0, pts.size - 1)
            val done = remember(pts, split) { if (split > 0) pts.subList(0, split + 1).toList() else emptyList() }
            val ahead = remember(pts, split) { pts.subList(split, pts.size).toList() }
            if (done.size >= 2) {
                Polyline(points = done, color = ridden, width = coreW, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(), zIndex = 1f)
            }
            if (ahead.size >= 2) {
                if (dark) {
                    Polyline(points = ahead, color = Color(0x263D8BFF), width = glowW, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(), zIndex = 2f)
                }
                Polyline(points = ahead, color = routeEdge, width = edgeW, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(), zIndex = 3f)
                Polyline(points = ahead, color = routeCore, width = coreW, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(), zIndex = 4f)
                if (dark) {
                    Polyline(points = ahead, color = Color(0x73FFFFFF), width = shineW, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(), zIndex = 5f)
                }
            }
        }
        if (pin != null) {
            Marker(state = MarkerState(position = LatLng(pin.lat, pin.lon)), title = pin.title, zIndex = 4f)
        }
        if (hasFix) {
            val arrow = remember(puckPx) { BitmapDescriptorFactory.fromBitmap(arrowBitmap(puckPx)) }
            val dot = remember(puckPx) { BitmapDescriptorFactory.fromBitmap(dotBitmap(puckPx)) }
            val showArrow = !dark && (moving || ru.navigating)
            if (dark) {
                if (moving || ru.navigating) {
                    val cone = remember { BitmapDescriptorFactory.fromBitmap(coneBitmap(256)) }
                    HeadingCone(here, heading, cone)
                }
                PulseRing(here, ringW)
            }
            PuckMarker(puck, if (showArrow) arrow else dot, showArrow, heading)
        }
    }
}

/** The light cone in front of you; recomposes on its own each frame, not the whole map. */
@Composable
@GoogleMapComposable
private fun HeadingCone(here: State<LatLng>, heading: State<Float>, cone: BitmapDescriptor) {
    GroundOverlay(
        position = GroundOverlayPosition.create(here.value, 70f),
        image = cone,
        anchor = Offset(0.5f, 1f),
        bearing = heading.value,
        zIndex = 6f,
    )
}

/** Pulsing ring under the dot (night look). */
@Composable
@GoogleMapComposable
private fun PulseRing(here: State<LatLng>, strokePx: Float) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring: Float by pulse.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ring",
    )
    Circle(
        center = here.value,
        radius = 6.0 + 26.0 * ring,
        strokeColor = Color(0xFF3D8BFF).copy(alpha = (1f - ring) * 0.9f),
        strokeWidth = strokePx,
        fillColor = Color(0xFF3D8BFF).copy(alpha = (1f - ring) * 0.12f),
        zIndex = 7f,
    )
}

@Composable
@GoogleMapComposable
private fun PuckMarker(state: MarkerState, icon: BitmapDescriptor, rotate: Boolean, heading: State<Float>) {
    Marker(
        state = state,
        icon = icon,
        flat = true,
        rotation = if (rotate) heading.value else 0f,
        anchor = Offset(0.5f, 0.5f),
        zIndex = 10f,
    )
}
