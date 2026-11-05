package com.riderdeck.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import kotlin.math.cos

/**
 * Demo mode for automated screenshots (adb: am start ... --ez demo true [--es sheet more] [--ez sun true]).
 * Fills the dashboard with a sample ride, song, weather and an active route, and turns off live sensors.
 */
object Demo {
    var active = false
        private set
    var sheet: String? = null
        private set

    fun apply(intent: Intent?) {
        if (intent?.getBooleanExtra("demo", false) != true) return
        active = true
        sheet = intent.getStringExtra("sheet")
        val sun = intent.getBooleanExtra("sun", false)
        RiderApp.prefs.onboarded = true
        RiderApp.prefs.edit { it.copy(sunlight = sun, autoTheme = false) }

        val lat = 12.9716
        val lon = 77.6412
        Hub.ride.value = RideState(
            speedKmh = 47f, hasFix = true, tripKm = 12.4, movingSecs = 1980.0, topKmh = 72f,
            altitudeM = 912.0, leanDeg = 14f, maxLeanDeg = 28f, lat = lat, lon = lon, bearing = 20f, fuelKm = 120f,
        )
        Hub.listenerConnected.value = true
        Hub.media.value = MediaState(
            connected = true, title = "Night Highway", artist = "RiderDeck Mix", art = art(),
            playing = true, positionMs = 64_000, positionAtMs = SystemClock.elapsedRealtime(),
            durationMs = 228_000, appName = "YT Music",
        )
        Weather.state.value = WeatherState(tempC = 26f, code = 2, windKmh = 9f, isDay = true, rainInMin = null)
        Hub.device.value = DeviceState(78, false, 36f)
        Navigator.state.value = route(lat, lon)
        Hub.follow.value = true
    }

    /** A route: 180 m ahead, right turn, 120 m, left turn, then 1.4 km to the destination. */
    private fun route(lat: Double, lon: Double): RouteUi {
        val pts = ArrayList<LL>()
        var la = lat
        var lo = lon
        fun leg(meters: Double, bearingDeg: Double) {
            val steps = (meters / 20).toInt().coerceAtLeast(1)
            val dLat = cos(Math.toRadians(bearingDeg)) * 20 / 111_320.0
            val dLon = kotlin.math.sin(Math.toRadians(bearingDeg)) * 20 / (111_320.0 * cos(Math.toRadians(la)))
            repeat(steps) {
                la += dLat
                lo += dLon
                pts.add(LL(la, lo))
            }
        }
        pts.add(LL(la, lo))
        leg(180.0, 20.0)
        val t1 = pts.size - 1
        leg(120.0, 110.0)
        val t2 = pts.size - 1
        leg(1400.0, 25.0)
        val end = pts.size - 1
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + Navigator.dist(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
        val mans = listOf(
            Maneuver(1, "Head north on 2nd Main Road", "", 0),
            Maneuver(10, "Turn right onto 100 Feet Road", "", t1),
            Maneuver(15, "Turn left onto CMH Road", "", t2),
            Maneuver(4, "You have arrived", "", end),
        )
        val r = Route(pts, cum, mans, cum.last(), 1440.0, Pin(pts.last().lat, pts.last().lon, "Indiranagar Metro"))
        return RouteUi(
            route = r, navigating = true, next = mans[1], toNextM = 180.0,
            remainingM = r.lengthM, remainingS = r.timeS, doneIdx = 0, after = mans[2], heading = 20f,
        )
    }

    private fun art(): Bitmap {
        val n = 320
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, n.toFloat(), n.toFloat(), 0xFFFF7A45.toInt(), 0xFF6D28D9.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
        p.shader = RadialGradient(n * 0.7f, n * 0.75f, n * 0.5f, 0xCC22D3EE.toInt(), 0x0022D3EE, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
        p.shader = null
        p.color = 0x33FFFFFF
        c.drawCircle(n * 0.3f, n * 0.35f, n * 0.18f, p)
        return b
    }
}
