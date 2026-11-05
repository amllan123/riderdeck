package com.riderdeck.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * "Share → RiderDeck" from Google Maps (or any geo: link): turns the shared place into a route on the RiderDeck map.
 * Google Maps shares short links (maps.app.goo.gl/…); those are followed to the full link, which carries the spot.
 */
object SharedPlace {
    private const val TAG = "RiderShare"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val URL_RE = Regex("https?://[^\\s]+")
    private val PLACE_3D4D = Regex("!3d(-?\\d{1,2}\\.\\d+)!4d(-?\\d{1,3}\\.\\d+)")
    private val QUERY_LL = Regex("[?&](?:q|query|ll|destination|daddr|center|sll)=(-?\\d{1,2}\\.\\d+)(?:,|%2C)\\s*\\+?(-?\\d{1,3}\\.\\d+)")
    private val AT_LL = Regex("@(-?\\d{1,2}\\.\\d+),(-?\\d{1,3}\\.\\d+)")
    private val BODY_CENTER = Regex("center=(-?\\d{1,2}\\.\\d+)%2C(-?\\d{1,3}\\.\\d+)")
    private val BODY_PAIR = Regex("\\[null,null,(-?\\d{1,2}\\.\\d{4,}),(-?\\d{1,3}\\.\\d{4,})\\]")

    /** True when [intent] carried a place we're now opening. */
    fun handle(ctx: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (text.isNullOrBlank()) return false
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        Hub.toast("Opening the shared place…")
        Navigator.busy(true)
        scope.launch {
            val pin = withContext(Dispatchers.IO) {
                try {
                    parse(ctx, text, subject)
                } catch (e: Exception) {
                    Log.w(TAG, "parse failed: $e")
                    null
                }
            }
            if (pin == null) {
                Navigator.busy(false)
                Hub.toast("Couldn't read that place. Search for it in RiderDeck instead")
                return@launch
            }
            Navigator.preview(pin)
        }
        return true
    }

    /** Works out the place from shared text: geo: URIs, Google Maps links (short or long), or a plain address. */
    suspend fun parse(ctx: Context, text: String, subject: String? = null): Pin? {
        val t = text.trim()
        if (t.startsWith("geo:", ignoreCase = true)) return geo(ctx, t)
        val url = URL_RE.find(t)?.value?.trimEnd(')', '.', ',', ';', '"', '\'')
        val label = label(if (url != null) t.substringBefore(url) else "", subject)
        if (url == null) return search(ctx, t.lines().joinToString(", ") { it.trim() }.take(200))

        var cur: String = url
        for (hop in 0 until 6) {
            coords(cur)?.let { (la, lo) -> return Pin(la, lo, label ?: nameFromUrl(cur) ?: "Shared place") }
            val c = URL(cur).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            c.setRequestProperty("Accept-Language", "en-IN,en")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: break
                cur = URL(URL(cur), loc).toString()
                c.disconnect()
                continue
            }
            if (code in 200..299) {
                val body = c.inputStream.bufferedReader().use { r ->
                    val sb = StringBuilder()
                    val buf = CharArray(16_384)
                    while (sb.length < 600_000) {
                        val n = r.read(buf)
                        if (n < 0) break
                        sb.append(buf, 0, n)
                    }
                    sb.toString()
                }
                val m = BODY_CENTER.find(body) ?: BODY_PAIR.find(body)
                if (m != null) {
                    val la = m.groupValues[1].toDouble()
                    val lo = m.groupValues[2].toDouble()
                    if (ok(la, lo)) return Pin(la, lo, label ?: nameFromUrl(cur) ?: "Shared place")
                }
            }
            break
        }
        // No coordinates in the link: look up the name/address it carries.
        val q = queryFromUrl(cur) ?: label ?: nameFromUrl(cur) ?: return null
        return search(ctx, q)?.let { if (label != null) it.copy(title = label) else it }
    }

    private suspend fun geo(ctx: Context, uri: String): Pin? {
        // geo:12.97,77.64?q=12.97,77.64(Motorizzz)  or  geo:0,0?q=Motorizzz+Indiranagar
        // geo: is an opaque URI, so read ?q= by hand
        val q = uri.substringAfter("?", "").split("&")
            .firstOrNull { it.startsWith("q=") }?.removePrefix("q=")?.let { decode(it.replace("+", "%20")) }
        val name = q?.let { Regex("\\(([^)]+)\\)").find(it)?.groupValues?.get(1) }
        val qLL = q?.let { Regex("^\\s*(-?\\d+\\.?\\d*)\\s*,\\s*(-?\\d+\\.?\\d*)").find(it) }
        if (qLL != null) {
            val la = qLL.groupValues[1].toDouble()
            val lo = qLL.groupValues[2].toDouble()
            if (ok(la, lo)) return Pin(la, lo, name ?: "Shared place")
        }
        val parts = uri.substringAfter("geo:").substringBefore("?").split(",")
        val la = parts.getOrNull(0)?.toDoubleOrNull()
        val lo = parts.getOrNull(1)?.substringBefore(";")?.toDoubleOrNull()
        val pathPin = if (la != null && lo != null && ok(la, lo)) Pin(la, lo, name ?: "Shared place") else null
        if (pathPin != null && (q.isNullOrBlank() || name != null)) return pathPin
        if (!q.isNullOrBlank()) search(ctx, q.replace(Regex("\\([^)]*\\)"), "").trim())?.let { return it }
        return pathPin
    }

    private suspend fun search(ctx: Context, q: String): Pin? {
        if (q.isBlank()) return null
        val hit = PlaceSearch.find(ctx, q).firstOrNull() ?: return null
        val p = PlaceSearch.resolve(ctx, hit) ?: return null
        return Pin(p.lat, p.lon, p.title)
    }

    fun coords(url: String): Pair<Double, Double>? {
        for (re in listOf(PLACE_3D4D, QUERY_LL, AT_LL)) {
            val m = re.find(url) ?: continue
            val la = m.groupValues[1].toDoubleOrNull() ?: continue
            val lo = m.groupValues[2].toDoubleOrNull() ?: continue
            if (ok(la, lo)) return la to lo
        }
        return null
    }

    private fun ok(la: Double, lo: Double) = la in -90.0..90.0 && lo in -180.0..180.0 && !(la == 0.0 && lo == 0.0)

    /** "Motorizzz · 4.6★ · Motorcycle parts store\n…" → "Motorizzz". */
    private fun label(before: String, subject: String?): String? {
        val first = before.lines().map { it.trim() }.firstOrNull { it.isNotBlank() }
            ?: subject?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        return first.substringBefore(" · ").substringBefore(" - Google Maps").trim().trimEnd(',', ':').take(80).ifBlank { null }
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }

    private fun nameFromUrl(url: String): String? =
        Regex("/maps/place/([^/@?]+)").find(url)?.groupValues?.get(1)?.let { decode(it).replace('+', ' ').trim() }?.ifBlank { null }

    private fun queryFromUrl(url: String): String? = try {
        val u = Uri.parse(url)
        (u.getQueryParameter("q") ?: u.getQueryParameter("query") ?: u.getQueryParameter("destination") ?: u.getQueryParameter("daddr"))
            ?.trim()?.ifBlank { null }
            ?: Regex("/maps/search/([^/@?]+)").find(url)?.groupValues?.get(1)?.let { decode(it).replace('+', ' ') }
    } catch (_: Exception) {
        null
    }
}
