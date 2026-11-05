package com.riderdeck.app

import android.content.Context
import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** A plain latitude/longitude pair. */
data class LL(val lat: Double, val lon: Double)

/** One instruction on the route. [type] uses Valhalla's maneuver numbers (10 = right, 15 = left, 4 = arrive…). */
data class Maneuver(val type: Int, val instruction: String, val verbal: String, val begin: Int)

class Route(
    val points: List<LL>,
    val cum: DoubleArray, // metres from the start to each point
    val maneuvers: List<Maneuver>,
    val lengthM: Double,
    val timeS: Double,
    val dest: Pin,
)

data class RouteUi(
    val loading: Boolean = false,
    val route: Route? = null,
    val navigating: Boolean = false,
    val next: Maneuver? = null,
    val toNextM: Double = 0.0,
    val remainingM: Double = 0.0,
    val remainingS: Double = 0.0,
    /** Index of the route point you've reached; the line behind it is drawn grey. */
    val doneIdx: Int = 0,
    /** The turn after the next one, when it comes quickly ("Then ↰"). */
    val after: Maneuver? = null,
    /** Direction of the road ahead, for heading-up while stopped. */
    val heading: Float? = null,
)

/** In-app routing and turn-by-turn guidance. */
object Navigator {
    val state = MutableStateFlow(RouteUi())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var idx = 0
    private var offRoute = 0
    private var lastLat = 0.0
    private var lastLon = 0.0
    private val spoken = HashSet<String>()

    /** Plan a route to [dest] and show it on the map. */
    fun preview(dest: Pin, autoStart: Boolean = false) {
        job?.cancel()
        Hub.pin.value = dest
        val r = Hub.ride.value
        if (!r.hasFix) {
            state.value = RouteUi()
            Hub.toast("Waiting for GPS before planning the route")
            return
        }
        val wasNavigating = state.value.navigating
        state.value = state.value.copy(loading = true)
        job = scope.launch {
            val route = withContext(Dispatchers.IO) { Routing.route(r.lat, r.lon, dest) }
            if (route == null) {
                state.value = RouteUi()
                Hub.toast("Couldn't plan a route. Check your internet and try again")
                if (wasNavigating) Speaker.guide("Couldn't find a new route")
                return@launch
            }
            idx = 0
            offRoute = 0
            spoken.clear()
            val first = route.maneuvers.firstOrNull { it.begin > 0 } ?: route.maneuvers.firstOrNull()
            state.value = RouteUi(
                route = route, next = first,
                toNextM = first?.let { route.cum[it.begin.coerceAtMost(route.cum.size - 1)] } ?: route.lengthM,
                remainingM = route.lengthM, remainingS = route.timeS,
            )
            if (autoStart || wasNavigating) start(announce = !wasNavigating)
        }
    }

    /** Find [query] (an address, "home", a shop name…) and plan a route to the best match. */
    fun previewQuery(ctx: Context, query: String, autoStart: Boolean) {
        if (query.isBlank()) return
        state.value = state.value.copy(loading = true)
        scope.launch {
            val hit = PlaceSearch.find(ctx, query).firstOrNull()
            if (hit == null) {
                state.value = state.value.copy(loading = false)
                Hub.toast("Couldn't find “$query”")
                Speaker.guide("Sorry, I couldn't find that place")
                return@launch
            }
            preview(Pin(hit.lat, hit.lon, hit.title), autoStart)
        }
    }

    fun start(announce: Boolean = true) {
        val s = state.value
        val r = s.route ?: return
        state.value = s.copy(navigating = true, loading = false)
        Hub.follow.value = true
        if (announce) {
            val mins = (r.timeS / 60).roundToInt().coerceAtLeast(1)
            Speaker.guide("Starting route to ${r.dest.title}. About $mins minutes.")
        }
        update(Hub.ride.value, force = true)
    }

    fun stop() {
        job?.cancel()
        state.value = RouteUi()
        Hub.pin.value = null
        Hub.follow.value = true
    }

    /** Called for every ride update; only does work when the position changed. */
    fun onFix(ride: RideState) {
        if (!state.value.navigating || !ride.hasFix) return
        if (ride.lat == lastLat && ride.lon == lastLon) return
        update(ride, force = false)
    }

    private fun update(ride: RideState, force: Boolean) {
        val s = state.value
        val r = s.route ?: return
        if (!ride.hasFix) return
        lastLat = ride.lat
        lastLon = ride.lon

        // Closest route point in a window just behind and ahead of where we were.
        var best = idx
        var bestD = Double.MAX_VALUE
        val from = (idx - 3).coerceAtLeast(0)
        val to = (idx + 120).coerceAtMost(r.points.size - 1)
        for (i in from..to) {
            val d = dist(ride.lat, ride.lon, r.points[i].lat, r.points[i].lon)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        if (bestD > 60 && !force) {
            offRoute++
            if (offRoute >= 3 && s.navigating && !s.loading) {
                offRoute = 0
                Hub.toast("Rerouting…")
                Speaker.guide("Rerouting")
                preview(r.dest, autoStart = true)
                return
            }
        } else {
            offRoute = 0
            idx = best
        }

        val done = r.cum[idx]
        val remaining = (r.lengthM - done).coerceAtLeast(0.0)
        val toDest = dist(ride.lat, ride.lon, r.dest.lat, r.dest.lon)
        if (toDest < 35 || remaining < 25) {
            Speaker.guide("You have arrived at ${r.dest.title}")
            Hub.toast("You've arrived")
            stop()
            return
        }
        val next = r.maneuvers.firstOrNull { it.begin > idx } ?: r.maneuvers.lastOrNull()
        val toNext = next?.let { (r.cum[it.begin.coerceAtMost(r.cum.size - 1)] - done).coerceAtLeast(0.0) } ?: remaining
        val remS = if (r.lengthM > 0) r.timeS * remaining / r.lengthM else 0.0
        val nextPos = next?.let { r.maneuvers.indexOf(it) } ?: -1
        val after = if (nextPos >= 0) r.maneuvers.getOrNull(nextPos + 1)?.takeIf {
            it.type != 4 && r.cum[it.begin.coerceAtMost(r.cum.size - 1)] - r.cum[next!!.begin.coerceAtMost(r.cum.size - 1)] < 250
        } else null
        var ahead = idx
        while (ahead < r.points.size - 1 && r.cum[ahead] - r.cum[idx] < 30) ahead++
        val heading = if (ahead > idx) {
            val a = Location("").apply { latitude = r.points[idx].lat; longitude = r.points[idx].lon }
            val b = Location("").apply { latitude = r.points[ahead].lat; longitude = r.points[ahead].lon }
            (a.bearingTo(b) + 360f) % 360f
        } else null
        state.value = s.copy(
            next = next, toNextM = toNext, remainingM = remaining, remainingS = remS,
            doneIdx = idx, after = after, heading = heading,
        )

        if (next != null) {
            val say = next.verbal.ifBlank { next.instruction }
            if (toNext in 220.0..480.0 && spoken.add("${next.begin}-far")) {
                Speaker.guide("In ${spokenDistance(toNext)}, ${say.replaceFirstChar { it.lowercase(Locale.ROOT) }}")
            }
            if (toNext <= 110 && spoken.add("${next.begin}-near")) {
                Speaker.guide(say)
            }
        }
    }

    private fun spokenDistance(m: Double): String =
        if (m < 1000) "${((m / 50).roundToInt() * 50)} metres" else "%.1f kilometres".format(Locale.ROOT, m / 1000)

    fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0].toDouble()
    }
}

/** Route planning: Valhalla with motorcycle costing (free OSM server), OSRM as a fallback. */
object Routing {
    private const val UA = "RiderDeck/0.1 (personal riding app)"

    fun route(fromLat: Double, fromLon: Double, dest: Pin): Route? =
        valhalla(fromLat, fromLon, dest) ?: osrm(fromLat, fromLon, dest)

    private fun valhalla(fromLat: Double, fromLon: Double, dest: Pin): Route? = try {
        val body = JSONObject()
            .put(
                "locations",
                JSONArray()
                    .put(JSONObject().put("lat", fromLat).put("lon", fromLon))
                    .put(JSONObject().put("lat", dest.lat).put("lon", dest.lon)),
            )
            .put("costing", "motorcycle")
            .put("directions_options", JSONObject().put("units", "kilometers").put("language", "en-US"))
        val trip = JSONObject(http("https://valhalla1.openstreetmap.de/route", body.toString())).getJSONObject("trip")
        val leg = trip.getJSONArray("legs").getJSONObject(0)
        val pts = decode(leg.getString("shape"), 6)
        val arr = leg.getJSONArray("maneuvers")
        val mans = (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            Maneuver(
                type = m.optInt("type"),
                instruction = m.optString("instruction"),
                verbal = m.optString("verbal_pre_transition_instruction").ifBlank { m.optString("instruction") },
                begin = m.optInt("begin_shape_index"),
            )
        }
        val summary = trip.getJSONObject("summary")
        build(pts, mans, summary.getDouble("length") * 1000, summary.getDouble("time"), dest)
    } catch (_: Exception) {
        null
    }

    private fun osrm(fromLat: Double, fromLon: Double, dest: Pin): Route? = try {
        val url = "https://router.project-osrm.org/route/v1/driving/$fromLon,$fromLat;${dest.lon},${dest.lat}" +
            "?overview=full&geometries=polyline6&steps=true"
        val route = JSONObject(http(url, null)).getJSONArray("routes").getJSONObject(0)
        val pts = decode(route.getString("geometry"), 6)
        val steps = route.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
        var search = 0
        val mans = (0 until steps.length()).map {
            val st = steps.getJSONObject(it)
            val man = st.getJSONObject("maneuver")
            val loc = man.getJSONArray("location")
            val lon = loc.getDouble(0)
            val lat = loc.getDouble(1)
            var bi = search
            var bd = Double.MAX_VALUE
            for (i in search until pts.size) {
                val d = Navigator.dist(lat, lon, pts[i].lat, pts[i].lon)
                if (d < bd) {
                    bd = d
                    bi = i
                }
                if (d > bd + 300) break
            }
            search = bi
            val type = man.optString("type")
            val mod = man.optString("modifier")
            val name = st.optString("name")
            val code = when {
                type == "arrive" -> 4
                type == "depart" -> 1
                type == "roundabout" || type == "rotary" -> 26
                mod == "right" -> 10
                mod == "left" -> 15
                mod == "slight right" -> 9
                mod == "slight left" -> 16
                mod == "sharp right" -> 11
                mod == "sharp left" -> 14
                mod == "uturn" -> 13
                else -> 8
            }
            val verb = when (code) {
                4 -> "Arrive at your destination"
                1 -> "Head out"
                26 -> "Take the roundabout"
                10 -> "Turn right"
                15 -> "Turn left"
                9 -> "Bear right"
                16 -> "Bear left"
                11 -> "Turn sharp right"
                14 -> "Turn sharp left"
                13 -> "Make a U-turn"
                else -> "Continue"
            }
            val text = if (name.isNotBlank() && code != 4) "$verb onto $name" else verb
            Maneuver(code, text, text, bi)
        }
        build(pts, mans, route.getDouble("distance"), route.getDouble("duration"), dest)
    } catch (_: Exception) {
        null
    }

    private fun build(pts: List<LL>, mans: List<Maneuver>, lengthM: Double, timeS: Double, dest: Pin): Route? {
        if (pts.size < 2) return null
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) {
            cum[i] = cum[i - 1] + Navigator.dist(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
        }
        return Route(pts, cum, mans, if (lengthM > 0) lengthM else cum.last(), timeS, dest)
    }

    private fun http(url: String, jsonBody: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", UA)
        if (jsonBody != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(jsonBody.toByteArray()) }
        }
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** Google's encoded polyline format, at the given precision (6 for Valhalla/OSRM polyline6). */
    private fun decode(s: String, precision: Int): List<LL> {
        val factor = 10.0.pow(precision)
        val out = ArrayList<LL>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < s.length) {
            for (axis in 0..1) {
                var result = 0L
                var shift = 0
                var b: Int
                do {
                    b = s[index++].code - 63
                    result = result or ((b and 0x1f).toLong() shl shift)
                    shift += 5
                } while (b >= 0x20 && index < s.length)
                val delta = if ((result and 1L) != 0L) (result shr 1).inv() else (result shr 1)
                if (axis == 0) lat += delta else lon += delta
            }
            out.add(LL(lat / factor, lon / factor))
        }
        return out
    }
}
