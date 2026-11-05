package com.riderdeck.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * GPS speed and trip, lean angle from the gravity sensor, and crash detection
 * (a hard impact while moving, followed by the bike stopping).
 */
class RideTracker(private val ctx: Context) : SensorEventListener {
    private val fused = LocationServices.getFusedLocationProviderClient(ctx)
    private val sensors = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displays = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var last: Location? = null
    var running = false
        private set

    private var leanZero = 0f
    // Zero lean = phone upright on screen. "Set lean to zero" re-takes it from the current angle.
    private var calibrated = true
    private var leanSmooth = 0f
    private var lastLeanPush = 0L

    private var spikeAt = 0L
    var onCrashSuspected: (() -> Unit)? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onLocation(it) }
        }
    }

    init {
        Hub.ride.update { it.copy(fuelKm = RiderApp.prefs.kmSinceFill) }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        running = true
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        fused.removeLocationUpdates(callback)
        sensors.unregisterListener(this)
    }

    fun calibrateLean() {
        calibrated = false
    }

    private fun norm(a: Float): Float {
        var d = a
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    fun resetTrip() {
        Hub.ride.update { it.copy(tripKm = 0.0, movingSecs = 0.0, topKmh = 0f, maxLeanDeg = 0f) }
    }

    /** Simulated rides (tests) go through exactly the same path as real GPS. */
    fun feed(l: Location) = onLocation(l)

    private fun onLocation(l: Location) {
        val kmh = if (l.hasSpeed()) l.speed * 3.6f else 0f
        // Very rough fixes (indoors, under flyovers) show 0 rather than a made-up speed.
        val shown = if (kmh < 2f || l.accuracy > 40f) 0f else kmh
        val prev = last
        var addKm = 0.0
        var addSecs = 0.0
        if (prev != null && l.accuracy < 25f) {
            val meters = prev.distanceTo(l)
            val dt = (l.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
            // Count the distance even after a gap in fixes (tunnel, phone busy), as long as it's a speed a bike can do.
            if (dt > 0 && meters / dt < 45.0 && (kmh > 3f || (dt > 5 && meters / dt > 1.5))) {
                addKm = meters / 1000.0
                addSecs = dt
            }
        }
        if (prev == null || l.accuracy < 25f) last = l
        // Keep the map steady: ignore a wild fix if we had a good one moments ago.
        val prevGood = Hub.ride.value
        val keepOld = prevGood.hasFix && l.accuracy > 50f && prevGood.accuracyM < 30f &&
            SystemClock.elapsedRealtime() - prevGood.fixAt < 15_000
        if (addKm > 0) RiderApp.prefs.kmSinceFill = RiderApp.prefs.kmSinceFill + addKm.toFloat()
        Hub.ride.update {
            it.copy(
                speedKmh = shown,
                hasFix = true,
                tripKm = it.tripKm + addKm,
                movingSecs = it.movingSecs + addSecs,
                topKmh = max(it.topKmh, shown),
                altitudeM = l.altitude,
                lat = if (keepOld) it.lat else l.latitude,
                lon = if (keepOld) it.lon else l.longitude,
                bearing = if (l.hasBearing() && shown >= 6f) l.bearing else it.bearing,
                fuelKm = it.fuelKm + addKm.toFloat(),
                fixAt = if (keepOld) it.fixAt else SystemClock.elapsedRealtime(),
                accuracyM = if (keepOld) it.accuracyM else l.accuracy,
            )
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        when (e.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                // Angle of gravity in the screen plane, corrected for how the screen is rotated,
                // so an upright phone reads 0° in portrait or landscape.
                val gx = e.values[0]
                val gy = e.values[1]
                val inPlane = sqrt(gx * gx + gy * gy)
                val rot = try {
                    displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
                } catch (_: Exception) {
                    0
                }
                val raw = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat() - rot * 90f
                if (!calibrated) {
                    leanZero = norm(raw)
                    calibrated = true
                }
                // Phone lying almost flat: lean can't be measured.
                val d = if (inPlane < 4f) 0f else norm(raw - leanZero)
                leanSmooth = leanSmooth * 0.85f + d * 0.15f
                if (now - lastLeanPush > 100) {
                    lastLeanPush = now
                    val lean = leanSmooth.coerceIn(-70f, 70f)
                    val moving = Hub.ride.value.speedKmh > 10f
                    Hub.ride.update {
                        it.copy(leanDeg = lean, maxLeanDeg = if (moving) max(it.maxLeanDeg, abs(lean)) else it.maxLeanDeg)
                    }
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                val g = sqrt(x * x + y * y + z * z) / 9.81f
                if (spikeAt == 0L && g > 4.0f && Hub.ride.value.speedKmh > 15f) {
                    spikeAt = now
                }
                if (spikeAt != 0L && now - spikeAt > 6000) {
                    // Impact while moving, and six seconds later the bike has stopped.
                    if (Hub.ride.value.speedKmh < 5f) onCrashSuspected?.invoke()
                    spikeAt = 0L
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
