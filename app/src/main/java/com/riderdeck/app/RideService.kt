package com.riderdeck.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Keeps GPS, ride recording, voice turns and crash detection running while you're in Google Maps
 * or the screen is off. Shows a quiet "Riding" notification with a Stop button.
 * Stops by itself when RiderDeck is in the background and you haven't ridden for 10 minutes.
 */
class RideService : Service() {
    companion object {
        private const val CHANNEL = "ride"
        private const val NOTE_ID = 7
        private const val ACTION_STOP = "com.riderdeck.app.STOP_RIDE"

        @Volatile
        var running = false
            private set

        fun start(ctx: Context) {
            if (Demo.active || !Perms.location(ctx)) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, RideService::class.java))
            } catch (e: Exception) {
                Log.w("RideService", "start failed: $e")
                RiderApp.tracker.start() // at least track while the app is open
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var lastMoveAt = SystemClock.elapsedRealtime()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Recorder.endNow()
            Navigator.stop()
            shutDown()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTE_ID, note(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            Log.w("RideService", "foreground failed: $e")
            RiderApp.tracker.start()
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        RiderApp.tracker.start()
        lastMoveAt = SystemClock.elapsedRealtime()
        if (watcher?.isActive != true) watcher = scope.launch { watch() }
        return START_NOT_STICKY
    }

    private suspend fun watch() {
        var lastText = ""
        while (scope.isActive) {
            delay(10_000)
            val r = Hub.ride.value
            if (r.speedKmh > 8f) lastMoveAt = SystemClock.elapsedRealtime()
            Recorder.tick()
            val busy = MainActivity.visible || Recorder.active.value || Navigator.state.value.navigating || Hub.nav.value.active
            if (!busy && SystemClock.elapsedRealtime() - lastMoveAt > 10 * 60_000L) {
                shutDown()
                return
            }
            val text = noteText()
            if (text != lastText) {
                lastText = text
                try {
                    getSystemService(NotificationManager::class.java)?.notify(NOTE_ID, note())
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun shutDown() {
        if (!MainActivity.visible) RiderApp.tracker.stop()
        running = false
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun noteText(): String {
        val l = Recorder.live.value
        return if (l.active) {
            val mins = (l.rideSecs() / 60).toInt()
            String.format(Locale.ROOT, "%.1f km · %d:%02d ride time", l.km, mins / 60, mins % 60)
        } else {
            "Ready · a ride records by itself when you set off"
        }
    }

    private fun note(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm?.getNotificationChannel(CHANNEL) == null) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ride tracking", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while RiderDeck tracks your ride in the background"
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RideService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val riding = Recorder.live.value.active
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_ride)
            .setContentTitle(if (riding) "Riding" else "RiderDeck is on")
            .setContentText(noteText())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, if (riding) "End ride" else "Turn off", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

/** Runs for every GPS/sensor update, whether RiderDeck is on screen or not. */
object RideLoop {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var overSpeed = false
    private var lowFuelWarned = false
    private val beeper by lazy {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        } catch (_: Exception) {
            null
        }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            Hub.ride.collect { r ->
                try {
                    step(r)
                } catch (e: Exception) {
                    Log.e("RideLoop", "step failed", e)
                }
            }
        }
    }

    private fun step(r: RideState) {
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

/** Crash countdown and SOS, independent of the screen so it works in the background too. */
object CrashAlert {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            Speaker.say("Crash detected. Sending S O S in fifteen seconds. Tap I'm OK to cancel.")
            for (i in 15 downTo 1) {
                Hub.crashCountdown.value = i
                vibrate(300)
                delay(1000)
            }
            Hub.crashCountdown.value = null
            Sos.send(RiderApp.ctx, "Possible crash detected")
        }
    }

    fun cancel() {
        job?.cancel()
        Hub.crashCountdown.value = null
        Speaker.stop()
        Hub.toast("Alert cancelled. Ride safe")
    }

    fun sendNow() {
        vibrate(500)
        Sos.send(RiderApp.ctx, "Emergency")
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        try {
            val v = RiderApp.ctx.getSystemService(Vibrator::class.java) ?: return
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }
}
