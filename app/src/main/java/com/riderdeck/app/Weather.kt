package com.riderdeck.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WeatherState(
    val tempC: Float? = null,
    val code: Int = -1,
    val windKmh: Float = 0f,
    val isDay: Boolean = true,
    /** Minutes until rain is likely in the next 2 hours, 0 = raining now, null = dry. */
    val rainInMin: Int? = null,
    /** Chance of that rain (0–100) from the forecast. */
    val rainChance: Int = 0,
    /** Today's sunrise and sunset (epoch ms), 0 when unknown. */
    val sunriseMs: Long = 0L,
    val sunsetMs: Long = 0L,
)

/** Current weather and a 2-hour rain warning from Open-Meteo (free, no key). */
object Weather {
    val state = MutableStateFlow(WeatherState())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastFetch = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var busy = false
    private var rainWarned = false

    /** Refresh every 10 minutes, or sooner after riding 5 km. */
    fun maybeRefresh(r: RideState) {
        if (!r.hasFix || busy) return
        val now = System.currentTimeMillis()
        val moved = lastFetch == 0L || Navigator.dist(r.lat, r.lon, lastLat, lastLon) > 5000
        if (!moved && now - lastFetch < 10 * 60_000) return
        busy = true
        lastFetch = now
        lastLat = r.lat
        lastLon = r.lon
        scope.launch {
            val w = withContext(Dispatchers.IO) { fetch(r.lat, r.lon) }
            busy = false
            if (w == null) {
                lastFetch = now - 8 * 60_000 // try again in ~2 minutes
                return@launch
            }
            state.value = w
            val rain = w.rainInMin
            if (rain != null && rain <= 45 && w.rainChance >= 60 && !rainWarned) {
                rainWarned = true
                val msg = if (rain == 0) "It's raining around you. Ride carefully." else "Rain likely in about $rain minutes."
                Speaker.say(msg)
                Hub.toast(msg)
            } else if (rain == null) {
                rainWarned = false
            }
        }
    }

    /** For the self-test. */
    fun fetchNow(lat: Double, lon: Double): WeatherState? = fetch(lat, lon)

    /**
     * Forecast models often say "drizzle" when it's dry, so "raining" needs rain actually measured in the last
     * 15 minutes, and "rain soon" needs a real amount (not a trace) plus a decent chance in the hourly forecast.
     */
    private fun fetch(lat: Double, lon: Double): WeatherState? = try {
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,wind_speed_10m,is_day,precipitation,rain,showers,cloud_cover" +
                "&minutely_15=precipitation&forecast_minutely_15=8" +
                "&hourly=precipitation_probability&forecast_hours=3" +
                "&daily=sunrise,sunset&forecast_days=1&timezone=auto",
            lat, lon,
        )
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "RiderDeck/0.2")
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val cur = o.getJSONObject("current")
        val probs = o.optJSONObject("hourly")?.optJSONArray("precipitation_probability")
        fun chanceAt(minutes: Int): Int {
            if (probs == null || probs.length() == 0) return 0
            val i = (minutes / 60).coerceIn(0, probs.length() - 1)
            return maxOf(probs.optInt(i, 0), if (i + 1 < probs.length() && minutes % 60 >= 30) probs.optInt(i + 1, 0) else 0)
        }
        val wetNow = cur.optDouble("precipitation", 0.0) >= 0.2 ||
            cur.optDouble("rain", 0.0) + cur.optDouble("showers", 0.0) >= 0.2
        var code = cur.optInt("weather_code", -1)
        val rainyCode = code in 51..67 || code in 80..82 || code in 95..99
        if (rainyCode && !wetNow) {
            // the model's "drizzle" with nothing falling → describe the sky instead
            code = if (cur.optInt("cloud_cover", 100) >= 60) 3 else 2
        }
        var rainIn: Int? = if (wetNow && rainyCode) 0 else null
        var chance = if (rainIn == 0) 100 else 0
        val rainArr = o.optJSONObject("minutely_15")?.optJSONArray("precipitation")
        if (rainIn == null && rainArr != null) {
            for (i in 0 until rainArr.length()) {
                val mm = rainArr.optDouble(i, 0.0)
                val p = chanceAt(i * 15)
                if (mm >= 0.4 && p >= 45) {
                    rainIn = i * 15
                    chance = p
                    break
                }
            }
        }
        val offset = java.time.ZoneOffset.ofTotalSeconds(o.optInt("utc_offset_seconds", 19800))
        fun epoch(iso: String?): Long = try {
            java.time.LocalDateTime.parse(iso).toEpochSecond(offset) * 1000
        } catch (_: Exception) {
            0L
        }
        val daily = o.optJSONObject("daily")
        val sunrise = epoch(daily?.optJSONArray("sunrise")?.optString(0))
        val sunset = epoch(daily?.optJSONArray("sunset")?.optString(0))
        WeatherState(
            tempC = cur.optDouble("temperature_2m").toFloat(),
            code = code,
            windKmh = cur.optDouble("wind_speed_10m", 0.0).toFloat(),
            isDay = cur.optInt("is_day", 1) == 1,
            rainInMin = rainIn,
            rainChance = chance,
            sunriseMs = sunrise,
            sunsetMs = sunset,
        )
    } catch (_: Exception) {
        null
    }

    /** Daylight by today's sunrise/sunset; falls back to 6:30–18:15 before the first fetch. */
    fun isDaylight(now: Long = System.currentTimeMillis()): Boolean {
        val w = state.value
        if (w.sunriseMs > 0 && w.sunsetMs > 0) return now in w.sunriseMs..w.sunsetMs
        val c = java.util.Calendar.getInstance()
        val mins = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
        return mins in (6 * 60 + 30)..(18 * 60 + 15)
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        in 95..99 -> "Thunderstorm"
        else -> "Weather"
    }

    fun spoken(): String {
        val w = state.value
        val t = w.tempC ?: return "Weather isn't loaded yet"
        val rain = when (val r = w.rainInMin) {
            null -> "No rain expected in the next two hours."
            0 -> "It's raining now."
            else -> "${w.rainChance} percent chance of rain in about $r minutes."
        }
        return "${t.toInt()} degrees, ${describe(w.code).lowercase(Locale.ROOT)}. $rain"
    }
}

/** Compass direction for a GPS bearing. */
fun compass(bearing: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val i = (((bearing % 360) + 360) % 360 / 45f + 0.5f).toInt() % 8
    return dirs[i]
}
