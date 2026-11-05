package com.riderdeck.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.riderdeck.app.Hub
import com.riderdeck.app.Pin
import com.riderdeck.app.RideState
import com.riderdeck.app.RouteUi

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
 * Google Maps inside RiderDeck, styled like Google Maps navigation:
 * decluttered map, traffic, 3D buildings, thick blue route with the part you've ridden in grey,
 * blue navigation arrow, tilted heading-up camera with your arrow low on the screen.
 */
@Composable
fun GoogleRiderMap(
    ride: RideState,
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
    val moving = ride.speedKmh > 6f
    val heading: Float = when {
        moving -> ride.bearing
        ru.navigating && ru.heading != null -> ru.heading
        else -> ride.bearing
    }

    // A finger on the map stops it following you.
    LaunchedEffect(cam.isMoving, cam.cameraMoveStartedReason) {
        if (cam.isMoving && cam.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) Hub.follow.value = false
    }

    LaunchedEffect(ride.lat, ride.lon, follow, ru.navigating, heading) {
        if (!follow || !ride.hasFix) return@LaunchedEffect
        val target = CameraPosition.Builder()
            .target(LatLng(ride.lat, ride.lon))
            .zoom(if (ru.navigating) 17.8f else if (moving) 17f else 16.4f)
            .tilt(if (ru.navigating) 62f else if (moving) 45f else 0f)
            .bearing(if (ru.navigating || moving) heading else cam.position.bearing)
            .build()
        try {
            cam.animate(CameraUpdateFactory.newCameraPosition(target), 900)
        } catch (_: Exception) {
        }
    }

    suspend fun fitRoute() {
        val r = ru.route ?: return
        if (r.points.size < 2) return
        Hub.follow.value = false
        try {
            val b = LatLngBounds.Builder()
            r.points.forEach { b.include(LatLng(it.lat, it.lon)) }
            if (ride.hasFix) b.include(LatLng(ride.lat, ride.lon))
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
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "ring",
    )

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cam,
        contentPadding = padding,
        properties = MapProperties(
            isBuildingEnabled = true,
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
            if (split > 0) {
                Polyline(points = pts.subList(0, split + 1), color = ridden, width = coreW, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(), zIndex = 1f)
            }
            val ahead = pts.subList(split, pts.size)
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
        if (ride.hasFix) {
            val px = with(density) { 46.dp.roundToPx() }
            val arrow = remember(px) { BitmapDescriptorFactory.fromBitmap(arrowBitmap(px)) }
            val dot = remember(px) { BitmapDescriptorFactory.fromBitmap(dotBitmap(px)) }
            val puck = remember { MarkerState() }
            SideEffect { puck.position = LatLng(ride.lat, ride.lon) }
            val showArrow = !dark && (moving || ru.navigating)
            val here = LatLng(ride.lat, ride.lon)
            if (dark) {
                if (moving || ru.navigating) {
                    val cone = remember { BitmapDescriptorFactory.fromBitmap(coneBitmap(256)) }
                    com.google.maps.android.compose.GroundOverlay(
                        position = com.google.maps.android.compose.GroundOverlayPosition.create(here, 70f),
                        image = cone,
                        anchor = Offset(0.5f, 1f),
                        bearing = heading,
                        zIndex = 6f,
                    )
                }
                com.google.maps.android.compose.Circle(
                    center = here,
                    radius = 6.0 + 26.0 * ring,
                    strokeColor = Color(0xFF3D8BFF).copy(alpha = (1f - ring) * 0.9f),
                    strokeWidth = with(density) { 2.dp.toPx() },
                    fillColor = Color(0xFF3D8BFF).copy(alpha = (1f - ring) * 0.12f),
                    zIndex = 7f,
                )
            }
            Marker(
                state = puck,
                icon = if (showArrow) arrow else dot,
                flat = true,
                rotation = if (showArrow) heading else 0f,
                anchor = Offset(0.5f, 0.5f),
                zIndex = 10f,
            )
        }
    }
}
