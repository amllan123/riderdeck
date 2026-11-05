package com.riderdeck.app

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** One saved ride. [points] are lat, lon, speed (km/h) roughly every 25 m. */
data class RideRecord(
    val start: Long,
    val end: Long,
    val km: Double,
    val movingSecs: Double,
    val topKmh: Float,
    val maxLean: Float,
    val zeroTo60: Float?,
    val points: List<FloatArray>,
) {
    val avgKmh: Float get() = if (movingSecs > 10) (km / (movingSecs / 3600.0)).toFloat() else 0f

    fun toJson(): JSONObject {
        val pts = JSONArray()
        points.forEach { pts.put(JSONArray().put(it[0].toDouble()).put(it[1].toDouble()).put(it[2].toDouble())) }
        return JSONObject()
            .put("start", start).put("end", end).put("km", km).put("moving", movingSecs)
            .put("top", topKmh.toDouble()).put("lean", maxLean.toDouble())
            .put("z60", zeroTo60?.toDouble() ?: JSONObject.NULL)
            .put("pts", pts)
    }

    companion object {
        fun fromJson(o: JSONObject): RideRecord {
            val arr = o.optJSONArray("pts") ?: JSONArray()
            val pts = (0 until arr.length()).map {
                val a = arr.getJSONArray(it)
                floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.optDouble(2, 0.0).toFloat())
            }
            return RideRecord(
                o.getLong("start"), o.getLong("end"), o.optDouble("km"), o.optDouble("moving"),
                o.optDouble("top").toFloat(), o.optDouble("lean").toFloat(),
                if (o.isNull("z60")) null else o.optDouble("z60").toFloat(), pts,
            )
        }
    }
}

/** Saved rides on the phone (last 60). */
object Rides {
    val list = MutableStateFlow<List<RideRecord>>(emptyList())
    private val dir: File get() = File(RiderApp.ctx.filesDir, "rides").apply { mkdirs() }

    fun load() {
        list.value = (dir.listFiles { f -> f.name.endsWith(".json") && f.name != "current.json" } ?: emptyArray())
            .mapNotNull { f -> try { RideRecord.fromJson(JSONObject(f.readText())) } catch (_: Exception) { null } }
            .sortedByDescending { it.start }
    }

    fun save(r: RideRecord) {
        try {
            File(dir, "${r.start}.json").writeText(r.toJson().toString())
        } catch (_: Exception) {
        }
        val all = (listOf(r) + list.value.filter { it.start != r.start }).sortedByDescending { it.start }
        all.drop(60).forEach { File(dir, "${it.start}.json").delete() }
        list.value = all.take(60)
    }

    fun delete(r: RideRecord) {
        File(dir, "${r.start}.json").delete()
        list.value = list.value.filter { it.start != r.start }
    }

    val currentFile: File get() = File(dir, "current.json")
}

/**
 * Watches the ride and:
 *  - records rides automatically (starts above 12 km/h, ends after 4 minutes stopped)
 *  - saves where you parked when a ride ends
 *  - times 0–60 km/h launches
 *  - adds to the odometer
 *  - reminds you to take a break after 2 hours and to switch the headlight on at sunset
 */
object Recorder {
    val active = MutableStateFlow(false)
    /** The 0–60 time of the current ride, for the dashboard. */
    val lastZeroTo60 = MutableStateFlow<Float?>(null)

    private var startAt = 0L
    private var km = 0.0
    private var moving = 0.0
    private var top = 0f
    private var maxLean = 0f
    private var z60: Float? = null
    private val points = ArrayList<FloatArray>()
    private var lastTrip = -1.0
    private var lastMoving = -1.0
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var stoppedSince = 0L
    private var lastSave = 0L
    private var odoPending = 0.0

    private var blockStart = 0L
    private var breakWarned = false

    private var armedAt = 0L
    private var launchAt = 0L

    fun init() {
        Rides.load()
        val f = Rides.currentFile
        if (!f.exists()) return
        try {
            val r = RideRecord.fromJson(JSONObject(f.readText()))
            if (System.currentTimeMillis() - f.lastModified() > 10 * 60_000) {
                finishWith(r)
            } else {
                startAt = r.start; km = r.km; moving = r.movingSecs; top = r.topKmh; maxLean = r.maxLean; z60 = r.zeroTo60
                points.clear(); points.addAll(r.points)
                active.value = true
            }
        } catch (_: Exception) {
        }
        if (!active.value) f.delete()
    }

    fun onRide(r: RideState) {
        val now = System.currentTimeMillis()
        val clock = SystemClock.elapsedRealtime()

        // distance and time since the last update (trip can be reset from the app)
        val dKm = if (lastTrip < 0 || r.tripKm < lastTrip) 0.0 else r.tripKm - lastTrip
        val dSecs = if (lastMoving < 0 || r.movingSecs < lastMoving) 0.0 else r.movingSecs - lastMoving
        lastTrip = r.tripKm
        lastMoving = r.movingSecs
        if (dKm > 0) {
            odoPending += dKm
            if (odoPending >= 0.1) {
                RiderApp.prefs.odoKm = RiderApp.prefs.odoKm + odoPending.toFloat()
                odoPending = 0.0
            }
        }
        if (!r.hasFix) return
        val speed = r.speedKmh
        val moved = r.lat != lastLat || r.lon != lastLon

        // ---- 0–60 km/h ----
        if (speed < 3f) {
            armedAt = clock
            launchAt = 0L
        } else if (armedAt > 0L && launchAt == 0L) {
            launchAt = armedAt
        }
        if (launchAt > 0L) {
            val secs = (clock - launchAt) / 1000f
            if (speed >= 60f) {
                if (secs in 2f..20f) onLaunch(secs)
                armedAt = 0L
                launchAt = 0L
            } else if (secs > 20f) {
                armedAt = 0L
                launchAt = 0L
            }
        }

        // ---- break reminder ----
        if (speed >= 5f) {
            if (blockStart == 0L) blockStart = now
            if (!breakWarned && now - blockStart >= 2 * 3600_000L) {
                breakWarned = true
                Speaker.say("You've been riding for two hours. Time for a short chai break.")
                Hub.toast("2 hours of riding. Time for a break")
            }
        }

        // ---- headlight at sunset ----
        val sunset = Weather.state.value.sunsetMs
        if (speed > 10f && sunset > 0 && now in (sunset - 15 * 60_000)..(sunset + 30 * 60_000)) {
            if (!RiderApp.prefs.remindedToday("headlight")) {
                Speaker.say("Sunset is near. Switch your headlight on.")
                Hub.toast("Sunset soon · headlight on")
            }
        }

        // ---- ride recording ----
        if (!active.value) {
            if (speed >= 12f) begin(r)
            return
        }
        km += dKm
        moving += dSecs
        top = max(top, speed)
        if (speed > 10f) maxLean = max(maxLean, abs(r.leanDeg))
        if (moved) {
            val last = points.lastOrNull()
            val far = last == null || Navigator.dist(last[0].toDouble(), last[1].toDouble(), r.lat, r.lon) >= 25
            if (far) points.add(floatArrayOf(r.lat.toFloat(), r.lon.toFloat(), speed))
            lastLat = r.lat
            lastLon = r.lon
        }
        if (speed < 3f) {
            if (stoppedSince == 0L) stoppedSince = now
            if (now - stoppedSince > 10 * 60_000L) {
                blockStart = 0L
                breakWarned = false
            }
            if (now - stoppedSince > 4 * 60_000L) {
                finish(r)
                return
            }
        } else {
            stoppedSince = 0L
        }
        if (now - lastSave > 30_000) {
            lastSave = now
            try {
                Rides.currentFile.writeText(snapshot(now).toJson().toString())
            } catch (_: Exception) {
            }
        }
    }

    private fun begin(r: RideState) {
        active.value = true
        startAt = System.currentTimeMillis()
        km = 0.0; moving = 0.0; top = r.speedKmh; maxLean = 0f; z60 = null
        lastZeroTo60.value = null
        points.clear()
        points.add(floatArrayOf(r.lat.toFloat(), r.lon.toFloat(), r.speedKmh))
        stoppedSince = 0L
        lastSave = 0L
    }

    private fun snapshot(end: Long) = RideRecord(startAt, end, km, moving, top, maxLean, z60, ArrayList(points))

    private fun finish(r: RideState) {
        val rec = snapshot(if (stoppedSince > 0) stoppedSince else System.currentTimeMillis())
        active.value = false
        Rides.currentFile.delete()
        if (rec.km >= 0.3) {
            Rides.save(rec)
            RiderApp.prefs.saveParking(r.lat, r.lon, System.currentTimeMillis())
            Hub.toast("Ride saved · %.1f km · parking spot saved".format(rec.km))
        }
    }

    private fun finishWith(rec: RideRecord) {
        if (rec.km >= 0.3) {
            Rides.save(rec)
            rec.points.lastOrNull()?.let { RiderApp.prefs.saveParking(it[0].toDouble(), it[1].toDouble(), rec.end) }
        }
    }

    private fun onLaunch(secs: Float) {
        val t = (secs * 10).roundToInt() / 10f
        z60 = if (z60 == null) t else minOf(z60!!, t)
        lastZeroTo60.value = z60
        val best = RiderApp.prefs.best060
        val isBest = best == 0f || t < best
        if (isBest) RiderApp.prefs.best060 = t
        Hub.toast("0–60 km/h in %.1f s%s".format(t, if (isBest) " · new best!" else ""))
    }
}

/** Service intervals driven by the odometer. */
object Service {
    data class Item(val key: String, val title: String, val every: Float, val at: Float, val odo: Float) {
        val since: Float get() = (odo - at).coerceAtLeast(0f)
        val left: Float get() = every - since
        val frac: Float get() = (since / every).coerceIn(0f, 1f)
        val due: Boolean get() = left <= 0f
    }

    fun items(): List<Item> {
        val p = RiderApp.prefs
        val odo = p.odoKm
        return listOf(
            Item("oil", "Engine oil", p.oilEveryKm, p.oilAtKm, odo),
            Item("chain", "Chain clean & lube", p.chainEveryKm, p.chainAtKm, odo),
        )
    }

    fun markDone(key: String) {
        val p = RiderApp.prefs
        when (key) {
            "oil" -> p.oilAtKm = p.odoKm
            "chain" -> p.chainAtKm = p.odoKm
        }
    }

    fun remindIfDue() {
        val due = items().filter { it.due }
        if (due.isEmpty() || RiderApp.prefs.remindedToday("service")) return
        val names = due.joinToString(" and ") { it.title.lowercase() }
        Hub.toast("Service due: $names")
        Speaker.say("Reminder. $names is due.")
    }
}
