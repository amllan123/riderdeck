package com.riderdeck.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

const val PKG_MAPS = "com.google.android.apps.maps"
const val PKG_YTM = "com.google.android.apps.youtube.music"

class RiderApp : Application() {
    companion object {
        lateinit var prefs: Prefs
            private set
        lateinit var ctx: Context
            private set
        /** Lite edition has no notification access (installs where Play Protect blocks it). */
        val isLite: Boolean get() = BuildConfig.FLAVOR == "lite"
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        prefs = Prefs(this)
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@RiderApp, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        }
    }
}

/* ---------------- live state shared by the screen, sensors and the notification listener ---------------- */

data class RideState(
    val speedKmh: Float = 0f,
    val hasFix: Boolean = false,
    val tripKm: Double = 0.0,
    val movingSecs: Double = 0.0,
    val topKmh: Float = 0f,
    val altitudeM: Double = 0.0,
    val leanDeg: Float = 0f,
    val maxLeanDeg: Float = 0f,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val bearing: Float = 0f,
    val fuelKm: Float = 0f,
) {
    val avgKmh: Float get() = if (movingSecs > 10) (tripKm / (movingSecs / 3600.0)).toFloat() else 0f
}

data class MediaState(
    val connected: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val positionAtMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val appName: String = "",
)

data class NavState(
    val active: Boolean = false,
    val distance: String = "",
    val instruction: String = "",
    val extra: String = "",
    val icon: Bitmap? = null,
)

data class CallState(
    val ringing: Boolean = false,
    val active: Boolean = false,
    val caller: String = "",
    val startedAt: Long = 0,
)

data class MessageAlert(val id: Long, val app: String, val sender: String, val text: String)

data class DeviceState(val batteryPct: Int = 0, val charging: Boolean = false, val tempC: Float = 0f)

data class VoiceState(val listening: Boolean, val text: String)

object Hub {
    val ride = MutableStateFlow(RideState())
    val media = MutableStateFlow(MediaState())
    val nav = MutableStateFlow(NavState())
    val call = MutableStateFlow(CallState())
    val message = MutableStateFlow<MessageAlert?>(null)
    var lastMessage: MessageAlert? = null
    val device = MutableStateFlow(DeviceState())
    val crashCountdown = MutableStateFlow<Int?>(null)
    val voice = MutableStateFlow<VoiceState?>(null)
    val listenerConnected = MutableStateFlow(false)
    val toast = MutableStateFlow<Pair<Long, String>?>(null)

    fun toast(msg: String) {
        toast.value = System.nanoTime() to msg
    }
}

/* ---------------- settings ---------------- */

data class Contact(val name: String, val number: String)

data class RiderSettings(
    val home: String = "",
    val office: String = "",
    val contacts: List<Contact> = emptyList(),
    val readMessages: Boolean = true,
    val crashDetection: Boolean = true,
    val heatGuard: Boolean = true,
    val sunlight: Boolean = false,
    val gloves: Boolean = false,
    val rideLock: Boolean = true,
    val speedBeep: Boolean = true,
    val alertSpeed: Int = 60,
    val tankL: Float = 10f,
    val mileage: Float = 45f,
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("riderdeck", Context.MODE_PRIVATE)
    val s = MutableStateFlow(load())

    var kmSinceFill: Float
        get() = sp.getFloat("kmSinceFill", 0f)
        set(v) {
            sp.edit().putFloat("kmSinceFill", v).apply()
        }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) {
            sp.edit().putBoolean("onboarded", v).apply()
        }

    fun edit(change: (RiderSettings) -> RiderSettings) {
        val next = change(s.value)
        s.value = next
        save(next)
    }

    fun rangeKm(fuelKm: Float): Int {
        val v = s.value
        return (v.tankL * v.mileage - fuelKm).coerceAtLeast(0f).toInt()
    }

    private fun load(): RiderSettings {
        val raw = sp.getString("settings", null) ?: return RiderSettings()
        return try {
            val o = JSONObject(raw)
            val d = RiderSettings()
            val arr = o.optJSONArray("contacts") ?: JSONArray()
            RiderSettings(
                home = o.optString("home", d.home),
                office = o.optString("office", d.office),
                contacts = (0 until arr.length()).map {
                    val c = arr.getJSONObject(it)
                    Contact(c.optString("name"), c.optString("number"))
                },
                readMessages = o.optBoolean("readMessages", d.readMessages),
                crashDetection = o.optBoolean("crashDetection", d.crashDetection),
                heatGuard = o.optBoolean("heatGuard", d.heatGuard),
                sunlight = o.optBoolean("sunlight", d.sunlight),
                gloves = o.optBoolean("gloves", d.gloves),
                rideLock = o.optBoolean("rideLock", d.rideLock),
                speedBeep = o.optBoolean("speedBeep", d.speedBeep),
                alertSpeed = o.optInt("alertSpeed", d.alertSpeed),
                tankL = o.optDouble("tankL", d.tankL.toDouble()).toFloat(),
                mileage = o.optDouble("mileage", d.mileage.toDouble()).toFloat(),
            )
        } catch (e: Exception) {
            RiderSettings()
        }
    }

    private fun save(x: RiderSettings) {
        val arr = JSONArray()
        x.contacts.forEach { arr.put(JSONObject().put("name", it.name).put("number", it.number)) }
        val o = JSONObject()
            .put("home", x.home)
            .put("office", x.office)
            .put("contacts", arr)
            .put("readMessages", x.readMessages)
            .put("crashDetection", x.crashDetection)
            .put("heatGuard", x.heatGuard)
            .put("sunlight", x.sunlight)
            .put("gloves", x.gloves)
            .put("rideLock", x.rideLock)
            .put("speedBeep", x.speedBeep)
            .put("alertSpeed", x.alertSpeed)
            .put("tankL", x.tankL.toDouble())
            .put("mileage", x.mileage.toDouble())
        sp.edit().putString("settings", o.toString()).apply()
    }
}
