package com.riderdeck.app

import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        Recorder.live.value = LiveRide(
            active = true, startAt = System.currentTimeMillis() - 41 * 60_000L, endAt = 0L,
            km = 12.4, movingSecs = 1980.0, topKmh = 72f, maxLean = 28f,
        )
        Hub.follow.value = true
    }

    /** A route: 180 m ahead, right turn, 120 m, left turn, then 1.4 km to the destination. */
    fun route(lat: Double, lon: Double): RouteUi {
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

/**
 * Simulated ride for automated tests (adb: am start ... --ez sim true [--ez selftest true]).
 * Feeds noisy fake GPS along the demo route through the real tracker, recorder, navigator and map:
 * set off, ride, stop 20 s in "traffic", ride on.
 */
object Sim {
    private const val TAG = "RiderSim"
    var active = false
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun apply(ctx: Context, intent: Intent?) {
        if (intent?.getBooleanExtra("sim", false) != true || active) return
        active = true
        RiderApp.prefs.onboarded = true
        RiderApp.prefs.edit { it.copy(sunlight = false, autoTheme = false, speedBeep = false, readMessages = false) }
        val ru = Demo.route(12.9716, 77.6412)
        Navigator.state.value = ru.copy(navigating = true, doneIdx = 0)
        Hub.follow.value = true
        scope.launch { drive(ru.route!!) }
        if (intent.getBooleanExtra("selftest", false)) SelfTest.run(ctx.applicationContext)
    }

    private suspend fun drive(r: Route) {
        val rnd = java.util.Random(7)
        var d = 0.0
        var v = 0.0
        var t = 0
        var stoppedAt = -1
        while (d < r.lengthM - 40 && t < 240) {
            // 0–4 s parked, then ride at ~36 km/h; at 330 m stop for 20 s; then ride on
            val target = when {
                t < 4 -> 0.0
                d >= 330 && stoppedAt < 0 -> { stoppedAt = t; 0.0 }
                stoppedAt >= 0 && t - stoppedAt < 20 -> 0.0
                else -> 10.0
            }
            v = if (target > v) minOf(target, v + 2.2) else maxOf(target, v - 3.5)
            d += v
            var i = 0
            while (i < r.cum.size - 2 && r.cum[i + 1] < d) i++
            val a = r.points[i]
            val b = r.points[i + 1]
            val seg = (r.cum[i + 1] - r.cum[i]).coerceAtLeast(0.1)
            val f = ((d - r.cum[i]) / seg).coerceIn(0.0, 1.0)
            val noiseN = rnd.nextGaussian() * 2.5
            val noiseE = rnd.nextGaussian() * 2.5
            val lat = a.lat + (b.lat - a.lat) * f + noiseN / 111_320.0
            val lon = a.lon + (b.lon - a.lon) * f + noiseE / (111_320.0 * Math.cos(Math.toRadians(a.lat)))
            val brg = Location("").apply { latitude = a.lat; longitude = a.lon }
                .bearingTo(Location("").apply { latitude = b.lat; longitude = b.lon })
            val loc = Location("sim").apply {
                latitude = lat
                longitude = lon
                accuracy = 5f
                altitude = 910.0
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                speed = if (v > 0) (v + rnd.nextGaussian() * 0.4).toFloat().coerceAtLeast(0f) else 0f
                bearing = (((brg + rnd.nextGaussian() * 6).toFloat()) + 360f) % 360f
            }
            RiderApp.tracker.feed(loc)
            if (t % 5 == 0) {
                val l = Recorder.live.value
                val n = Navigator.state.value
                Log.i(TAG, "t=$t speed=${"%.0f".format(v * 3.6)} km/h dist=${d.toInt()} m · ride active=${l.active} km=${"%.2f".format(l.km)} rideSecs=${l.rideSecs().toInt()} moving=${l.movingSecs.toInt()} · nav next=${n.next?.instruction} in ${n.toNextM.toInt()} m")
            }
            t++
            delay(1000)
        }
        Log.i(TAG, "sim done after $t s")
    }
}

/** Checks the network features from the test emulator and writes the results to the log (tag RiderSelfTest). */
object SelfTest {
    private const val TAG = "RiderSelfTest"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun run(ctx: Context) {
        scope.launch {
            delay(4000)
            withContext(Dispatchers.IO) {
                fun log(m: String) = Log.i(TAG, m)
                try {
                    log("maps key in build: ${BuildConfig.HAS_MAPS_KEY}")
                    for (q in listOf("motorizzz", "Motorizzz Bangalore", "indiranagar metro", "petrol bunk")) {
                        val res = PlaceSearch.find(ctx, q)
                        log("search \"$q\" google=${PlaceSearch.google.value} ${PlaceSearch.googleError.take(160)} → ${res.size}: " +
                            res.take(5).joinToString(" | ") { "${it.title} [${it.sub.take(50)}] ${it.distanceKm?.let { k -> "%.1f km".format(k) } ?: ""}" })
                        res.firstOrNull()?.let {
                            val p = PlaceSearch.resolve(ctx, it)
                            log("   first → ${p?.lat}, ${p?.lon}")
                        }
                    }
                    log("weather: ${Weather.fetchNow(12.9716, 77.6412)}")
                    val shares = listOf(
                        "Motorizzz\nhttps://www.google.com/maps/place/Motorizzz/@12.9784,77.6408,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d12.97845!4d77.64091",
                        "geo:12.9716,77.6412?q=12.9716,77.6412(Test pin)",
                        "geo:0,0?q=Indiranagar+Metro+Station",
                        "https://maps.google.com/?q=Cubbon+Park,+Bengaluru",
                    )
                    for (t in shares) log("share \"${t.replace("\n", " / ").take(60)}\" → ${SharedPlace.parse(ctx, t)}")
                    val route = Routing.route(12.9716, 77.6412, Pin(12.9784, 77.6408, "test"))
                    log("route: ${route?.lengthM?.toInt()} m, ${route?.maneuvers?.size} turns")
                } catch (e: Exception) {
                    Log.e(TAG, "selftest crashed", e)
                }
                log("selftest done")
            }
        }
    }
}
