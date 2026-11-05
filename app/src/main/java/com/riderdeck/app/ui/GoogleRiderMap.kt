package com.riderdeck.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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

/** Day: Google's normal look without shop/POI clutter, like Google Maps in navigation. */
private const val DAY_STYLE = """[
{"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},
{"featureType":"poi.business","stylers":[{"visibility":"off"}]},
{"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"}]},
{"featureType":"transit","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
{"featureType":"road","elementType":"labels.icon","stylers":[{"visibility":"off"}]}
]"""

/** Night: Google's navigation night palette, no shop/POI clutter. */
private const val NIGHT_STYLE = """[
{"elementType":"geometry","stylers":[{"color":"#1d2333"}]},
{"elementType":"labels.text.fill","stylers":[{"color":"#8e9bb3"}]},
{"elementType":"labels.text.stroke","stylers":[{"color":"#1d2333"}]},
{"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#3a4560"}]},
{"featureType":"landscape.man_made","elementType":"geometry","stylers":[{"color":"#222a3c"}]},
{"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},
{"featureType":"poi.business","stylers":[{"visibility":"off"}]},
{"featureType":"poi","elementType":"geometry","stylers":[{"color":"#20293a"}]},
{"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#1a3a33"}]},
{"featureType":"road","elementType":"geometry","stylers":[{"color":"#3b4760"}]},
{"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#1d2333"}]},
{"featureType":"road","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
{"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#c3cde0"}]},
{"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#46536f"}]},
{"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#5b6a8a"}]},
{"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#e6ecf7"}]},
{"featureType":"transit","stylers":[{"visibility":"off"}]},
{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0f2a46"}]},
{"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#5d7ea3"}]}
]"""

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
    c.drawCircle(cx, cx, px * 0.17f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt() })
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

    val routeOuter = if (dark) Color(0xFF0B3C8C) else Color(0xFF1558D6)
    val routeInner = if (dark) Color(0xFF669DF6) else Color(0xFF4C8DF6)
    val ridden = if (dark) Color(0xFF5F6B82) else Color(0xFFA6AEBB)
    val outerW = with(density) { 12.dp.toPx() }
    val innerW = with(density) { 8.dp.toPx() }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cam,
        contentPadding = padding,
        properties = MapProperties(
            isBuildingEnabled = true,
            isTrafficEnabled = true,
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
                Polyline(points = pts.subList(0, split + 1), color = ridden, width = innerW, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(), zIndex = 1f)
            }
            val ahead = pts.subList(split, pts.size)
            if (ahead.size >= 2) {
                Polyline(points = ahead, color = routeOuter, width = outerW, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(), zIndex = 2f)
                Polyline(points = ahead, color = routeInner, width = innerW, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(), zIndex = 3f)
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
            val showArrow = moving || ru.navigating
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
