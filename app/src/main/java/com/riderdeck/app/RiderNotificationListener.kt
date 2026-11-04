package com.riderdeck.app

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap

/**
 * Reads other apps' notifications (with the user's permission) to:
 *  - control whatever music is playing (YouTube Music first)
 *  - show Google Maps' next turn
 *  - detect incoming calls
 *  - read WhatsApp / SMS messages aloud
 */
class RiderNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: RiderNotificationListener? = null
            private set
        private val DISTANCE = Regex("^\\s*[0-9][0-9.,]*\\s*(m|km|ft|mi|yd)\\b", RegexOption.IGNORE_CASE)
        private val MESSAGE_APPS = setOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging", "com.android.mms")
    }

    private var sessions: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var callNotification: StatusBarNotification? = null
    private var lastMessageKey = ""
    private val main = Handler(Looper.getMainLooper())

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onSessionDestroyed() {
            controller = null
            pickSession()
        }
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pickSession() }

    override fun onListenerConnected() {
        instance = this
        Hub.listenerConnected.value = true
        sessions = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            sessions?.addOnActiveSessionsChangedListener(sessionsChanged, me())
        } catch (_: SecurityException) {
        }
        pickSession()
        try {
            activeNotifications?.forEach { handle(it, initial = true) }
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        try {
            sessions?.removeOnActiveSessionsChangedListener(sessionsChanged)
        } catch (_: Exception) {
        }
        controller?.unregisterCallback(mediaCallback)
        controller = null
        instance = null
        Hub.listenerConnected.value = false
    }

    private fun me() = ComponentName(this, RiderNotificationListener::class.java)

    /* ---------------- music ---------------- */

    private fun pickSession() {
        val list: List<MediaController> = try {
            sessions?.getActiveSessions(me()) ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
        fun playing(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val pick = list.firstOrNull { it.packageName == PKG_YTM && playing(it) }
            ?: list.firstOrNull { playing(it) }
            ?: list.firstOrNull { it.packageName == PKG_YTM }
            ?: list.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(mediaCallback)
            controller = pick
            pick?.registerCallback(mediaCallback, main)
        }
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller
        if (c == null) {
            Hub.media.value = MediaState()
            return
        }
        val md = c.metadata
        val st = c.playbackState
        val art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        Hub.media.value = MediaState(
            connected = true,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            art = art,
            playing = st?.state == PlaybackState.STATE_PLAYING,
            positionMs = st?.position ?: 0L,
            positionAtMs = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            speed = st?.playbackSpeed ?: 1f,
            appName = appName(c.packageName),
        )
    }

    fun transport(): MediaController.TransportControls? = controller?.transportControls

    fun queue(): List<MediaSession.QueueItem> = try {
        controller?.queue ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun appName(pkg: String): String = when (pkg) {
        PKG_YTM -> "YT Music"
        else -> try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            "Music"
        }
    }

    /* ---------------- notifications ---------------- */

    fun callActions(): Array<Notification.Action>? = callNotification?.notification?.actions

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, initial = false)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.packageName == PKG_MAPS && isNavigation(sbn)) Hub.nav.value = NavState()
        if (n.category == Notification.CATEGORY_CALL && sbn.key == callNotification?.key) {
            callNotification = null
            Hub.call.value = CallState()
        }
    }

    private fun isNavigation(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        return n.category == "navigation" || (sbn.isOngoing && DISTANCE.containsMatchIn(title))
    }

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val n = sbn.notification
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString() ?: ""

        when {
            sbn.packageName == PKG_MAPS && isNavigation(sbn) -> {
                val icon: Bitmap? = try {
                    n.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96)
                } catch (_: Exception) {
                    null
                }
                val extra = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                    ?: ex.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
                Hub.nav.value = NavState(true, title, text, extra, icon)
            }

            n.category == Notification.CATEGORY_CALL -> {
                callNotification = sbn
                val actions = n.actions?.map { it.title.toString().lowercase() } ?: emptyList()
                val ringing = actions.any { "answer" in it || "accept" in it } || n.fullScreenIntent != null &&
                    actions.none { "hang" in it || "end" in it }
                val who = title.ifBlank { "Incoming call" }
                Hub.call.value = if (ringing) {
                    CallState(ringing = true, caller = who)
                } else {
                    val prev = Hub.call.value
                    CallState(active = true, caller = who, startedAt = if (prev.active) prev.startedAt else System.currentTimeMillis())
                }
            }

            !initial && (sbn.packageName in MESSAGE_APPS || n.category == Notification.CATEGORY_MESSAGE) -> {
                if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
                if (title.isBlank() || text.isBlank()) return
                val key = sbn.packageName + title + text
                if (key == lastMessageKey) return
                lastMessageKey = key
                val alert = MessageAlert(System.currentTimeMillis(), appName(sbn.packageName), title, text)
                Hub.message.value = alert
                Hub.lastMessage = alert
                val s = RiderApp.prefs.s.value
                if (s.readMessages && !Hub.call.value.active && !Hub.call.value.ringing) {
                    Speaker.say("Message from $title. $text")
                }
            }
        }
    }
}
