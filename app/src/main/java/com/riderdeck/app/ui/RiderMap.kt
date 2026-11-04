package com.riderdeck.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.riderdeck.app.Hub
import com.riderdeck.app.LL
import com.riderdeck.app.Pin
import com.riderdeck.app.RideState
import com.riderdeck.app.RouteUi
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

private fun pointJson(lat: Double, lon: Double) =
    """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[$lon,$lat]}}"""

private fun lineJson(points: List<LL>): String {
    val sb = StringBuilder("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""")
    points.forEachIndexed { i, p ->
        if (i > 0) sb.append(',')
        sb.append('[').append(p.lon).append(',').append(p.lat).append(']')
    }
    return sb.append("]}}").toString()
}

/**
 * Vector map (MapLibre + OpenFreeMap tiles): tilted 3D view with buildings, heading-up while riding,
 * glowing route line, location puck and destination pin.
 */
@Composable
fun RiderMap(ride: RideState, dark: Boolean, pin: Pin?, follow: Boolean, ru: RouteUi, modifier: Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val p = LocalPal.current
    val mapView = remember { MapView(ctx).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            try {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = true
            m.uiSettings.setAttributionMargins(16, 0, 0, 16)
            m.setMinZoomPreference(3.0)
            // Dragging, pinching or tilting the map stops it following you.
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) Hub.follow.value = false
            }
            m.cameraPosition = CameraPosition.Builder().target(LatLng(21.0, 78.9)).zoom(4.0).tilt(0.0).build()
            map = m
        }
    }

    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        val routeColor = String.format("#%06X", 0xFFFFFF and p.blue.toArgb())
        m.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { st ->
            addRiderLayers(st, dark, routeColor)
            style = st
        }
    }

    // Data on the map
    LaunchedEffect(style, ride.lat, ride.lon, ride.hasFix) {
        val st = style ?: return@LaunchedEffect
        st.getSourceAs<GeoJsonSource>("rd-me")?.setGeoJson(if (ride.hasFix) pointJson(ride.lat, ride.lon) else EMPTY)
    }
    LaunchedEffect(style, pin) {
        val st = style ?: return@LaunchedEffect
        st.getSourceAs<GeoJsonSource>("rd-pin")?.setGeoJson(pin?.let { pointJson(it.lat, it.lon) } ?: EMPTY)
    }
    LaunchedEffect(style, ru.route) {
        val st = style ?: return@LaunchedEffect
        val r = ru.route
        st.getSourceAs<GeoJsonSource>("rd-route")?.setGeoJson(if (r != null) lineJson(r.points) else EMPTY)
    }

    // Camera
    LaunchedEffect(map, ru.route, ru.navigating) {
        val m = map ?: return@LaunchedEffect
        val r = ru.route ?: return@LaunchedEffect
        if (ru.navigating || r.points.size < 2) return@LaunchedEffect
        Hub.follow.value = false
        try {
            val bounds = LatLngBounds.fromLatLngs(r.points.map { LatLng(it.lat, it.lon) })
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140), 900)
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(map, pin) {
        val m = map ?: return@LaunchedEffect
        if (pin == null || ru.route != null || ru.loading) return@LaunchedEffect
        Hub.follow.value = false
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(pin.lat, pin.lon)).zoom(15.5).tilt(30.0).bearing(0.0).build()
            ),
            800,
        )
    }
    LaunchedEffect(map, ride.lat, ride.lon, follow, ru.navigating) {
        val m = map ?: return@LaunchedEffect
        if (!follow || !ride.hasFix) return@LaunchedEffect
        val moving = ride.speedKmh > 6f
        val cam = CameraPosition.Builder()
            .target(LatLng(ride.lat, ride.lon))
            .zoom(if (ru.navigating) 17.4 else if (moving) 16.6 else 16.0)
            .tilt(if (ru.navigating || moving) 58.0 else 45.0)
            .bearing(if (moving) ride.bearing.toDouble() else m.cameraPosition.bearing)
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 900)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addRiderLayers(st: Style, dark: Boolean, routeColor: String) {
    val firstLabel: String? = st.layers.firstOrNull { it is SymbolLayer }?.id
    fun below(layer: Layer) {
        try {
            if (firstLabel != null) st.addLayerBelow(layer, firstLabel) else st.addLayer(layer)
        } catch (_: Exception) {
        }
    }

    // 3D buildings from the OpenMapTiles "building" layer.
    if (st.getSource("openmaptiles") != null && st.getLayer("rd-buildings") == null) {
        val b = FillExtrusionLayer("rd-buildings", "openmaptiles").withSourceLayer("building").withProperties(
            PropertyFactory.fillExtrusionColor(if (dark) "#1E2A3E" else "#D9DEE7"),
            PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
            PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
            PropertyFactory.fillExtrusionOpacity(if (dark) 0.9f else 0.75f),
        )
        b.minZoom = 14.5f
        below(b)
    }

    st.addSource(GeoJsonSource("rd-route"))
    st.addSource(GeoJsonSource("rd-pin"))
    st.addSource(GeoJsonSource("rd-me"))

    below(
        LineLayer("rd-route-glow", "rd-route").withProperties(
            PropertyFactory.lineColor(routeColor),
            PropertyFactory.lineWidth(18f),
            PropertyFactory.lineOpacity(0.25f),
            PropertyFactory.lineBlur(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    below(
        LineLayer("rd-route-casing", "rd-route").withProperties(
            PropertyFactory.lineColor(if (dark) "#0A2A66" else "#0D47A1"),
            PropertyFactory.lineWidth(10f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    below(
        LineLayer("rd-route-line", "rd-route").withProperties(
            PropertyFactory.lineColor(routeColor),
            PropertyFactory.lineWidth(6.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )

    st.addLayer(
        CircleLayer("rd-pin-halo", "rd-pin").withProperties(
            PropertyFactory.circleColor("#FF4D5A"),
            PropertyFactory.circleRadius(16f),
            PropertyFactory.circleOpacity(0.25f),
        )
    )
    st.addLayer(
        CircleLayer("rd-pin-dot", "rd-pin").withProperties(
            PropertyFactory.circleColor("#FF4D5A"),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    st.addLayer(
        CircleLayer("rd-me-halo", "rd-me").withProperties(
            PropertyFactory.circleColor(routeColor),
            PropertyFactory.circleRadius(24f),
            PropertyFactory.circleOpacity(0.22f),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
        )
    )
    st.addLayer(
        CircleLayer("rd-me-dot", "rd-me").withProperties(
            PropertyFactory.circleColor(routeColor),
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3.5f),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
        )
    )
}
