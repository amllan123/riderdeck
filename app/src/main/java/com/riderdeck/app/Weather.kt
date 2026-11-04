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
    /** Minutes until rain starts in the next 2 hours, 0 = raining now, null = dry. */
    val rainInMin: Int? = null,
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
            if (rain != null && rain <= 45 && !rainWarned) {
                rainWarned = true
                val msg = if (rain == 0) "It's raining around you. Ride carefully." else "Rain expected in about $rain minutes."
                Speaker.say(msg)
                Hub.toast(msg)
            } else if (rain == null) {
                rainWarned = false
            }
        }
    }

    private fun fetch(lat: Double, lon: Double): WeatherState? = try {
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,wind_speed_10m,is_day" +
                "&minutely_15=precipitation&forecast_minutely_15=8&timezone=auto",
            lat, lon,
        )
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "RiderDeck/0.1")
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val cur = o.getJSONObject("current")
        val rainArr = o.optJSONObject("minutely_15")?.optJSONArray("precipitation")
        var rainIn: Int? = null
        if (rainArr != null) {
            for (i in 0 until rainArr.length()) {
                if (rainArr.optDouble(i, 0.0) >= 0.2) {
                    rainIn = i * 15
                    break
                }
            }
        }
        WeatherState(
            tempC = cur.optDouble("temperature_2m").toFloat(),
            code = cur.optInt("weather_code", -1),
            windKmh = cur.optDouble("wind_speed_10m", 0.0).toFloat(),
            isDay = cur.optInt("is_day", 1) == 1,
            rainInMin = rainIn,
        )
    } catch (_: Exception) {
        null
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
            else -> "Rain expected in about $r minutes."
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
