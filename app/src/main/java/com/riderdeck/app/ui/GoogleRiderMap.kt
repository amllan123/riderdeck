package com.riderdeck.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
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
import com.riderdeck.app.Perms
import com.riderdeck.app.Pin
import com.riderdeck.app.RideState
import com.riderdeck.app.RouteUi

/** Google's night look, tuned to RiderDeck's colours. Traffic colours still show on top. */
private const val NIGHT_STYLE = """[
{"elementType":"geometry","stylers":[{"color":"#0e121a"}]},
{"elementType":"labels.text.fill","stylers":[{"color":"#8a94a6"}]},
{"elementType":"labels.text.stroke","stylers":[{"color":"#0e121a"}]},
{"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#2a3346"}]},
{"featureType":"landscape.man_made","elementType":"geometry","stylers":[{"color":"#121826"}]},
{"featureType":"poi","elementType":"geometry","stylers":[{"color":"#131a27"}]},
{"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#6b778c"}]},
{"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#0f2420"}]},
{"featureType":"road","elementType":"geometry","stylers":[{"color":"#232d3f"}]},
{"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#151c28"}]},
{"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#9aa6ba"}]},
{"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#2a3549"}]},
{"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#34445f"}]},
{"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#d0d9e8"}]},
{"featureType":"transit","elementType":"geometry","stylers":[{"color":"#1a2230"}]},
{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0a1a2c"}]},
{"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4e6d8c"}]}
]"""

/**
 * Google Maps inside RiderDeck: traffic, 3D buildings, Google's own location dot,
 * the RiderDeck route drawn on top, tilted and heading-up while riding.
 */
@Composable
fun GoogleRiderMap(
    ride: RideState,
    dark: Boolean,
    pin: Pin?,
    follow: Boolean,
    ru: RouteUi,
    bottomInsetDp: Int,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val cam = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(21.0, 78.9), 4f)
    }
    val night = remember { MapStyleOptions(NIGHT_STYLE) }

    // A finger on the map stops it following you.
    LaunchedEffect(cam.isMoving, cam.cameraMoveStartedReason) {
        if (cam.isMoving && cam.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) Hub.follow.value = false
    }

    LaunchedEffect(ride.lat, ride.lon, follow, ru.navigating) {
        if (!follow || !ride.hasFix) return@LaunchedEffect
        val moving = ride.speedKmh > 6f
        val target = CameraPosition.Builder()
            .target(LatLng(ride.lat, ride.lon))
            .zoom(if (ru.navigating) 17.5f else if (moving) 16.8f else 16.2f)
            .tilt(if (ru.navigating || moving) 55f else 40f)
            .bearing(if (moving) ride.bearing else cam.position.bearing)
            .build()
        try {
            cam.animate(CameraUpdateFactory.newCameraPosition(target), 900)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(ru.route, ru.navigating) {
        val r = ru.route ?: return@LaunchedEffect
        if (ru.navigating || r.points.size < 2) return@LaunchedEffect
        Hub.follow.value = false
        try {
            val b = LatLngBounds.Builder()
            r.points.forEach { b.include(LatLng(it.lat, it.lon)) }
            cam.animate(CameraUpdateFactory.newLatLngBounds(b.build(), 140), 900)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(pin) {
        if (pin == null || ru.route != null || ru.loading) return@LaunchedEffect
        Hub.follow.value = false
        try {
            cam.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(LatLng(pin.lat, pin.lon)).zoom(15.5f).tilt(30f).bearing(0f).build()
                ),
                800,
            )
        } catch (_: Exception) {
        }
    }

    val hasLocation = Perms.location(ctx)
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cam,
        contentPadding = PaddingValues(top = 84.dp, bottom = bottomInsetDp.dp),
        properties = MapProperties(
            isBuildingEnabled = true,
            isTrafficEnabled = true,
            isMyLocationEnabled = hasLocation,
            mapStyleOptions = if (dark) night else null,
        ),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            indoorLevelPickerEnabled = false,
            tiltGesturesEnabled = true,
        ),
    ) {
        val r = ru.route
        if (r != null) {
            val pts = remember(r) { r.points.map { LatLng(it.lat, it.lon) } }
            Polyline(points = pts, color = Color(0xFF0A2A66), width = 26f, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
            Polyline(points = pts, color = p.blue, width = 15f, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
        }
        if (pin != null) {
            Marker(state = MarkerState(position = LatLng(pin.lat, pin.lon)), title = pin.title)
        }
    }
}
