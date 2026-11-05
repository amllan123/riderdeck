package com.riderdeck.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class DocType(val key: String, val title: String, val hasExpiry: Boolean)

/** Bike papers kept on the phone for police checks, with expiry reminders. */
object Docs {
    val types = listOf(
        DocType("rc", "Registration (RC)", false),
        DocType("dl", "Driving licence", true),
        DocType("insurance", "Insurance", true),
        DocType("puc", "PUC certificate", true),
    )

    /** Bumped whenever a photo or date changes, so screens refresh. */
    val version = MutableStateFlow(0)

    private fun dir(ctx: Context) = File(ctx.filesDir, "docs").apply { mkdirs() }
    fun file(ctx: Context, key: String) = File(dir(ctx), "$key.jpg")
    fun has(ctx: Context, key: String) = file(ctx, key).exists()

    fun expiry(key: String): Long = RiderApp.prefs.docExpiry(key)

    fun setExpiry(key: String, at: Long) {
        RiderApp.prefs.setDocExpiry(key, at)
        version.value++
    }

    /** Days until expiry (negative when expired), or null when no date is set. */
    fun daysLeft(key: String): Long? {
        val at = expiry(key)
        if (at == 0L) return null
        return TimeUnit.MILLISECONDS.toDays(at - System.currentTimeMillis())
    }

    /** Copy a picked photo into the app, scaled to at most 2000 px. */
    suspend fun import(ctx: Context, uri: Uri, key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext false
            file(ctx, key).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            version.value++
            true
        } catch (_: Exception) {
            false
        }
    }

    fun delete(ctx: Context, key: String) {
        file(ctx, key).delete()
        RiderApp.prefs.setDocExpiry(key, 0L)
        version.value++
    }

    suspend fun load(ctx: Context, key: String, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(ctx, key)
        if (!f.exists()) return@withContext null
        try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            var sample = 1
            while (b.outWidth / sample > maxPx * 2 || b.outHeight / sample > maxPx * 2) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        }
    }

    /** Once a day: warn about papers that expire within 15 days or already have. */
    fun remindIfExpiring() {
        val soon = types.filter { t -> t.hasExpiry && (daysLeft(t.key)?.let { it <= 15 } ?: false) }
        if (soon.isEmpty() || RiderApp.prefs.remindedToday("docs")) return
        val text = soon.joinToString(", ") { t ->
            val d = daysLeft(t.key) ?: 0
            if (d < 0) "${t.title} expired" else "${t.title} expires in $d days"
        }
        Hub.toast(text)
        Speaker.say("Reminder. $text.")
    }
}
