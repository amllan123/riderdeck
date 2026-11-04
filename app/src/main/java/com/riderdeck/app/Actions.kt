package com.riderdeck.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.telephony.SmsManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt

/* ---------------- speech out ---------------- */

object Speaker : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()

    fun init(ctx: Context) {
        if (tts == null) tts = TextToSpeech(ctx.applicationContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale("en", "IN")
            pending.forEach { say(it) }
            pending.clear()
        }
    }

    fun say(text: String) {
        if (!ready) {
            pending += text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    fun stop() {
        tts?.stop()
    }
}

/* ---------------- permissions ---------------- */

object Perms {
    fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun location(ctx: Context) = has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    fun listener(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    fun openListenerSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Hub.toast("Open Settings → Notifications → Notification access, and allow RiderDeck")
        }
    }
}

/* ---------------- music ---------------- */

object Media {
    private fun t() = RiderNotificationListener.instance?.transport()
    fun play() { t()?.play() }
    fun pause() { t()?.pause() }
    fun toggle() = if (Hub.media.value.playing) pause() else play()
    fun next() { t()?.skipToNext() }
    fun prev() { t()?.skipToPrevious() }
    fun skipTo(id: Long) { t()?.skipToQueueItem(id) }

    fun openYtMusic(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(PKG_YTM)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com"))
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Hub.toast("YouTube Music isn't installed")
        }
    }
}

/* ---------------- calls ---------------- */

object Calls {
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun answer(ctx: Context) {
        if (Perms.has(ctx, Manifest.permission.ANSWER_PHONE_CALLS)) {
            try {
                ctx.getSystemService(TelecomManager::class.java).acceptRingingCall()
                return
            } catch (_: Exception) {
            }
        }
        if (!fire(listOf("answer", "accept"))) Hub.toast("Allow Phone in More → Permissions to answer calls")
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun decline(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 28 && Perms.has(ctx, Manifest.permission.ANSWER_PHONE_CALLS)) {
            try {
                if (ctx.getSystemService(TelecomManager::class.java).endCall()) return
            } catch (_: Exception) {
            }
        }
        if (!fire(listOf("decline", "reject", "hang up", "end call", "dismiss"))) {
            Hub.toast("Allow Phone in More → Permissions to decline calls")
        }
    }

    private fun fire(words: List<String>): Boolean {
        val action = RiderNotificationListener.instance?.callActions()?.firstOrNull { a ->
            val t = a.title.toString().lowercase()
            words.any { t.contains(it) }
        } ?: return false
        return try {
            action.actionIntent.send()
            true
        } catch (_: Exception) {
            false
        }
    }
}

/* ---------------- maps ---------------- */

object Nav {
    fun navigateTo(ctx: Context, place: String) {
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place) + "&mode=l"))
            .setPackage(PKG_MAPS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(nav)
        } catch (_: ActivityNotFoundException) {
            web(ctx, "https://www.google.com/maps/dir/?api=1&travelmode=two-wheeler&destination=" + Uri.encode(place))
        }
    }

    fun searchNearby(ctx: Context, what: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(what)))
            .setPackage(PKG_MAPS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (_: ActivityNotFoundException) {
            web(ctx, "https://www.google.com/maps/search/" + Uri.encode(what))
        }
    }

    fun openMaps(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(PKG_MAPS) ?: return web(ctx, "https://maps.google.com")
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun dial(ctx: Context, number: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun web(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

/* ---------------- SOS ---------------- */

object Sos {
    @Suppress("DEPRECATION")
    fun send(ctx: Context, reason: String) {
        val r = Hub.ride.value
        val where = if (r.hasFix) "https://maps.google.com/?q=${r.lat},${r.lon}" else "location not available yet"
        val msg = "RiderDeck SOS: $reason. My location: $where"
        val contacts = RiderApp.prefs.s.value.contacts.filter { it.number.isNotBlank() }
        if (contacts.isEmpty()) {
            Hub.toast("No emergency contacts. Add them in More → Settings")
            Speaker.say("No emergency contacts are set up")
            return
        }
        if (Perms.has(ctx, Manifest.permission.SEND_SMS)) {
            val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            var sent = 0
            contacts.forEach {
                try {
                    sms.sendMultipartTextMessage(it.number, null, sms.divideMessage(msg), null, null)
                    sent++
                } catch (_: Exception) {
                }
            }
            Hub.toast("SOS sent to $sent contact${if (sent == 1) "" else "s"}")
            Speaker.say("S O S sent")
        } else {
            val to = contacts.joinToString(";") { it.number }
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to")).putExtra("sms_body", msg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
}

/* ---------------- voice commands ---------------- */

object Commands {
    private val NAV_TO = Regex("^(navigate to|navigate|go to|take me to|directions to|route to|ride to)\\s+(.+)$")

    fun handle(act: MainActivity, raw: String) {
        val t = raw.lowercase(Locale.ROOT).trim()
        if (t.isBlank()) return
        val s = RiderApp.prefs.s.value
        fun reply(x: String) {
            Speaker.say(x)
            Hub.toast(x)
        }
        val wantsRoute = Regex("\\b(navigate|go|take|route|ride|directions)\\b").containsMatchIn(t)
        val navMatch = NAV_TO.find(t)
        when {
            wantsRoute && t.contains("home") ->
                if (s.home.isBlank()) reply("Set your home address in Settings first") else {
                    reply("Starting route home"); act.navigate(s.home)
                }
            wantsRoute && (t.contains("office") || t.contains("work")) ->
                if (s.office.isBlank()) reply("Set your office address in Settings first") else {
                    reply("Starting route to the office"); act.navigate(s.office)
                }
            t.contains("petrol") || t.contains("fuel") || t.contains("pump") -> {
                reply("Finding petrol pumps"); Nav.searchNearby(act, "petrol pump")
            }
            t.contains("puncture") || t.contains("tyre") || t.contains("tire") -> {
                reply("Finding puncture shops"); Nav.searchNearby(act, "puncture repair shop")
            }
            t.contains("hospital") -> {
                reply("Finding hospitals"); Nav.searchNearby(act, "hospital")
            }
            navMatch != null -> {
                val dest = navMatch.groupValues[2]
                reply("Starting route to $dest"); act.navigate(dest)
            }
            t.contains("next") || t.contains("skip") -> Media.next()
            t.contains("previous") || t.contains("last song") || t.contains("go back") -> Media.prev()
            t.contains("pause") || t.contains("stop music") || t.contains("stop the music") -> Media.pause()
            t.contains("play") || t.contains("resume") -> Media.play()
            t.contains("answer") || t.contains("pick up") -> Calls.answer(act)
            t.contains("decline") || t.contains("reject") || t.contains("cut") -> Calls.decline(act)
            t.contains("message") -> {
                val m = Hub.lastMessage
                if (m == null) reply("No new messages") else Speaker.say("${m.sender} said ${m.text}")
            }
            t.contains("speed") -> reply("You're doing ${Hub.ride.value.speedKmh.roundToInt()} kilometres per hour")
            t.contains("range") || t.contains("how far") || t.contains("fuel left") ->
                reply("About ${RiderApp.prefs.rangeKm(Hub.ride.value.fuelKm)} kilometres of fuel left")
            else -> reply("Sorry, I didn't get that")
        }
    }
}

/* ---------------- dark map ---------------- */

fun darkMapFilter(): ColorMatrixColorFilter {
    val invert = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
    val desat = ColorMatrix().apply { setSaturation(0.25f) }
    val dim = ColorMatrix().apply { setScale(0.78f, 0.82f, 0.95f, 1f) }
    invert.postConcat(desat)
    invert.postConcat(dim)
    return ColorMatrixColorFilter(invert)
}
