package com.riderdeck.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.riderdeck.app.ui.RiderRoot
import com.riderdeck.app.ui.RiderTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var tracker: RideTracker
        private set
    private var crashJob: Job? = null
    private var recognizer: SpeechRecognizer? = null
    private var dimmed = false
    private var overSpeed = false
    private var lowFuelWarned = false
    private var telephonyCallback: Any? = null
    private val beeper by lazy { try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) } catch (_: Exception) { null } }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            Hub.device.value = DeviceState(if (level >= 0) level * 100 / scale else 0, plugged, temp)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        Speaker.init(this)
        tracker = RideTracker(this)
        tracker.onCrashSuspected = {
            if (RiderApp.prefs.s.value.crashDetection) startCrashCountdown()
        }

        Demo.apply(intent)
        if (!Demo.active) Recorder.init()
        setContent {
            val s by RiderApp.prefs.s.collectAsState()
            val w by Weather.state.collectAsState()
            var minute by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(60_000)
                    minute = System.currentTimeMillis()
                }
            }
            val bright by Hub.bright.collectAsState()
            val sun = when {
                !s.autoTheme -> s.sunlight
                Hub.hasLightSensor -> bright
                else -> remember(w, minute) { Weather.isDaylight(minute) }
            }
            RiderTheme(sun = sun, gloves = s.gloves) {
                RiderRoot(this)
            }
        }

        lifecycleScope.launch { Hub.device.collect { applyHeatGuard(it.tempC) } }
        lifecycleScope.launch {
            delay(5_000)
            if (Demo.active) return@launch
            Docs.remindIfExpiring()
            delay(4_000)
            Service.remindIfDue()
        }
        lifecycleScope.launch {
            Hub.ride.collect { r ->
                Navigator.onFix(r)
                if (!Demo.active) {
                    Weather.maybeRefresh(r)
                    Recorder.onRide(r)
                }
                val s = RiderApp.prefs.s.value
                // over-speed beep, once per crossing
                if (s.speedBeep && r.speedKmh > s.alertSpeed + 2 && !overSpeed) {
                    overSpeed = true
                    beeper?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                } else if (r.speedKmh < s.alertSpeed - 3) {
                    overSpeed = false
                }
                // low fuel, once per fill-up
                val range = RiderApp.prefs.rangeKm(r.fuelKm)
                if (range < 25 && !lowFuelWarned) {
                    lowFuelWarned = true
                    Speaker.say("Low fuel. About $range kilometres left.")
                    Hub.toast("Low fuel · about $range km left")
                } else if (range >= 25) {
                    lowFuelWarned = false
                }
            }
        }
    }

    private var brightSince = 0L
    private var dimSince = 0L
    private val lightListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            val lux = e.values[0]
            val now = android.os.SystemClock.elapsedRealtime()
            when {
                lux > 6000f -> {
                    dimSince = 0L
                    if (brightSince == 0L) brightSince = now
                    if (!Hub.bright.value && now - brightSince > 3000) Hub.bright.value = true
                }
                lux < 1500f -> {
                    brightSince = 0L
                    if (dimSince == 0L) dimSince = now
                    if (Hub.bright.value && now - dimSince > 8000) Hub.bright.value = false
                }
                else -> {
                    brightSince = 0L
                    dimSince = 0L
                }
            }
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    override fun onStart() {
        super.onStart()
        val sm = getSystemService(android.hardware.SensorManager::class.java)
        sm?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)?.let {
            Hub.hasLightSensor = true
            sm.registerListener(lightListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        }
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        startTracking()
    }

    override fun onStop() {
        super.onStop()
        getSystemService(android.hardware.SensorManager::class.java)?.unregisterListener(lightListener)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        tracker.stop()
        stopCallMonitor()
        recognizer?.destroy()
        super.onDestroy()
    }

    /** Called on start and again after the user grants permissions. */
    fun startTracking() {
        if (Demo.active) return
        tracker.start()
        startCallMonitor()
    }

    /** Voice "navigate to …": plan the route in RiderDeck and start guiding straight away. */
    fun navigate(place: String) {
        Navigator.previewQuery(this, place, autoStart = true)
    }

    /* ---------------- crash & SOS ---------------- */

    fun startCrashCountdown() {
        if (crashJob?.isActive == true) return
        crashJob = lifecycleScope.launch {
            Speaker.say("Crash detected. Sending S O S in fifteen seconds. Tap I'm OK to cancel.")
            for (i in 15 downTo 1) {
                Hub.crashCountdown.value = i
                vibrate(300)
                delay(1000)
            }
            Hub.crashCountdown.value = null
            Sos.send(this@MainActivity, "Possible crash detected")
        }
    }

    fun cancelCrash() {
        crashJob?.cancel()
        Hub.crashCountdown.value = null
        Speaker.stop()
        Hub.toast("Alert cancelled. Ride safe")
    }

    fun sendSosNow() {
        vibrate(500)
        Sos.send(this, "Emergency")
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        try {
            val v = getSystemService(Vibrator::class.java) ?: return
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    /* ---------------- heat guard ---------------- */

    private var hotWarned = false

    /** Hot phone: warn by voice. Never dims — a dim screen is unreadable in sunlight. */
    private fun applyHeatGuard(tempC: Float) {
        val guard = RiderApp.prefs.s.value.heatGuard
        if (guard && tempC >= 45f && !hotWarned) {
            hotWarned = true
            Speaker.say("Phone is getting hot. Keep it out of direct sun and unplug the charger if you can.")
            Hub.toast("Phone hot · ${tempC.toInt()}°C · shade it and unplug the charger")
        } else if (tempC < 41f) {
            hotWarned = false
        }
    }

    /* ---------------- call state (backup to the notification listener) ---------------- */

    @Suppress("DEPRECATION")
    private val legacyListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
    }

    private fun onCallState(state: Int) {
        runOnUiThread {
            val cur = Hub.call.value
            when (state) {
                TelephonyManager.CALL_STATE_RINGING ->
                    if (!cur.ringing) Hub.call.value = CallState(ringing = true, caller = cur.caller.ifBlank { "Incoming call" })
                TelephonyManager.CALL_STATE_OFFHOOK ->
                    if (!cur.active) Hub.call.value = CallState(active = true, caller = cur.caller, startedAt = System.currentTimeMillis())
                TelephonyManager.CALL_STATE_IDLE -> Hub.call.value = CallState()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startCallMonitor() {
        if (telephonyCallback != null || !Perms.has(this, Manifest.permission.READ_PHONE_STATE)) return
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onCallState(state)
                }
                tm.registerTelephonyCallback(mainExecutor, cb)
                telephonyCallback = cb
            } else {
                tm.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
                telephonyCallback = legacyListener
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun stopCallMonitor() {
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        val cb = telephonyCallback ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31 && cb is TelephonyCallback) tm.unregisterTelephonyCallback(cb)
            else tm.listen(legacyListener, PhoneStateListener.LISTEN_NONE)
        } catch (_: Exception) {
        }
        telephonyCallback = null
    }

    /* ---------------- voice ---------------- */

    fun startVoice() {
        if (!Perms.has(this, Manifest.permission.RECORD_AUDIO)) {
            Hub.toast("Allow the microphone in More → Permissions")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Hub.toast("Voice recognition isn't available on this phone")
            return
        }
        Speaker.stop()
        recognizer?.destroy()
        Hub.voice.value = VoiceState(true, "")
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partial: Bundle?) {
                    val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    Hub.voice.value = VoiceState(true, t)
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    Hub.voice.value = VoiceState(false, t)
                    Commands.handle(this@MainActivity, t)
                    lifecycleScope.launch {
                        delay(900)
                        Hub.voice.value = null
                    }
                }
                override fun onError(error: Int) {
                    Hub.voice.value = VoiceState(false, "Didn't catch that")
                    lifecycleScope.launch {
                        delay(1200)
                        Hub.voice.value = null
                    }
                }
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            )
        }
    }

    fun stopVoice() {
        recognizer?.cancel()
        Hub.voice.value = null
    }
}
