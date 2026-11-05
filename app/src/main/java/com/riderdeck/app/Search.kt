package com.riderdeck.app

import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** A search result. Google results come without coordinates ([lat] is NaN) until [PlaceSearch.resolve]. */
data class Place(
    val title: String,
    val sub: String,
    val lat: Double,
    val lon: Double,
    val distanceKm: Float?,
    val id: String? = null,
) {
    val resolved: Boolean get() = !lat.isNaN() && !lon.isNaN()
}

data class Pin(val lat: Double, val lon: Double, val title: String)

/**
 * Place search for the map, best source first:
 *  1. Google Places (same results as the Google Maps app) using the Maps key — needs "Places API (New)" on the key
 *  2. Photon (OpenStreetMap, typo-tolerant), the phone's geocoder and Nominatim when Google isn't available
 */
object PlaceSearch {
    private const val TAG = "RiderSearch"

    enum class Google { UNKNOWN, OK, OFF, NO_KEY }

    /** Whether Google search works with this build's key (the search sheet shows a hint when it's OFF). */
    val google = MutableStateFlow(if (BuildConfig.HAS_MAPS_KEY) Google.UNKNOWN else Google.NO_KEY)
    var googleError = ""
        private set
    private var offAt = 0L
    private var session = UUID.randomUUID().toString()

    /** One search session per opened search sheet (Google bills a typed search + pick as one session). */
    fun newSession() {
        session = UUID.randomUUID().toString()
    }

    suspend fun find(ctx: Context, query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val g = try {
            googleAutocomplete(ctx, q)
        } catch (e: Exception) {
            Log.w(TAG, "google failed: $e")
            emptyList()
        }
        if (g.isNotEmpty()) return@withContext g
        fallback(ctx, q)
    }

    /** Coordinates for a Google result (one Place Details call); OSM results are already resolved. */
    suspend fun resolve(ctx: Context, p: Place): Place? = withContext(Dispatchers.IO) {
        if (p.resolved) return@withContext p
        val id = p.id ?: return@withContext null
        try {
            val url = "https://places.googleapis.com/v1/places/" + Uri.encode(id) +
                "?languageCode=en&sessionToken=" + Uri.encode(session)
            val c = googleConn(ctx, url)
            c.setRequestProperty("X-Goog-FieldMask", "location,formattedAddress")
            val code = c.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "details HTTP $code ${c.errorStream?.bufferedReader()?.use { it.readText() }?.take(300)}")
                return@withContext null
            }
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val loc = o.getJSONObject("location")
            newSession()
            p.copy(
                lat = loc.getDouble("latitude"),
                lon = loc.getDouble("longitude"),
                sub = p.sub.ifBlank { o.optString("formattedAddress") },
            )
        } catch (e: Exception) {
            Log.w(TAG, "details failed: $e")
            null
        }
    }

    fun distanceFromMe(lat: Double, lon: Double): Float? {
        val r = Hub.ride.value
        if (!r.hasFix) return null
        val out = FloatArray(1)
        Location.distanceBetween(r.lat, r.lon, lat, lon, out)
        return out[0] / 1000f
    }

    /* ---------------- Google Places (New) ---------------- */

    private fun googleAutocomplete(ctx: Context, q: String): List<Place> {
        if (!BuildConfig.HAS_MAPS_KEY) return emptyList()
        // Key not set up for Places: don't keep asking on every letter; try again after a while.
        if (google.value == Google.OFF && System.currentTimeMillis() - offAt < 5 * 60_000) return emptyList()
        val r = Hub.ride.value
        val body = JSONObject()
            .put("input", q)
            .put("sessionToken", session)
            .put("languageCode", "en")
            .put("includedRegionCodes", JSONArray().put("in"))
        if (r.hasFix) {
            val center = JSONObject().put("latitude", r.lat).put("longitude", r.lon)
            body.put("locationBias", JSONObject().put("circle", JSONObject().put("center", center).put("radius", 30000.0)))
            body.put("origin", center)
        }
        val c = googleConn(ctx, "https://places.googleapis.com/v1/places:autocomplete")
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        if (code !in 200..299) {
            val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.w(TAG, "autocomplete HTTP $code $err".take(600))
            if (code == 403 || code == 401 || (code == 400 && err.contains("API key", ignoreCase = true))) {
                googleError = try {
                    JSONObject(err).getJSONObject("error").optString("message")
                } catch (_: Exception) {
                    "HTTP $code"
                }
                google.value = Google.OFF
                offAt = System.currentTimeMillis()
            }
            return emptyList()
        }
        google.value = Google.OK
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val arr = o.optJSONArray("suggestions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val pp = arr.getJSONObject(i).optJSONObject("placePrediction") ?: return@mapNotNull null
            val fmt = pp.optJSONObject("structuredFormat")
            val main = fmt?.optJSONObject("mainText")?.optString("text")
                ?: pp.optJSONObject("text")?.optString("text") ?: return@mapNotNull null
            val sub = fmt?.optJSONObject("secondaryText")?.optString("text") ?: ""
            val meters = if (pp.has("distanceMeters")) pp.optInt("distanceMeters") else -1
            Place(
                title = main,
                sub = sub,
                lat = Double.NaN,
                lon = Double.NaN,
                distanceKm = if (meters >= 0) meters / 1000f else null,
                id = pp.optString("placeId").ifBlank { null },
            )
        }
    }

    private fun googleConn(ctx: Context, url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("X-Goog-Api-Key", BuildConfig.MAPS_KEY)
        // Lets a key restricted to "Android apps" accept these calls.
        c.setRequestProperty("X-Android-Package", ctx.packageName)
        certSha1(ctx)?.let { c.setRequestProperty("X-Android-Cert", it) }
        return c
    }

    private var cert: String? = null

    @Suppress("DEPRECATION")
    private fun certSha1(ctx: Context): String? {
        cert?.let { return it }
        return try {
            val pm = ctx.packageManager
            val sig = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            } ?: return null
            MessageDigest.getInstance("SHA-1").digest(sig.toByteArray())
                .joinToString("") { "%02X".format(it) }
                .also { cert = it }
        } catch (_: Exception) {
            null
        }
    }

    /* ---------------- OpenStreetMap & the phone's geocoder ---------------- */

    private suspend fun fallback(ctx: Context, q: String): List<Place> = coroutineScope {
        val r = Hub.ride.value
        val photon = async { photon(q) }
        val geo = async {
            var g = geocode(ctx, q, near = r.hasFix)
            if (g.isEmpty() && r.hasFix) g = geocode(ctx, q, near = false)
            g
        }
        var found = photon.await() + geo.await()
        if (found.isEmpty()) found = nominatim(q)
        found
            .distinctBy { "%.3f,%.3f".format(Locale.ROOT, it.lat, it.lon) }
            .map { it.copy(distanceKm = distanceFromMe(it.lat, it.lon)) }
            // keep relevance order, but drop matches on the other side of the country when closer ones exist
            .let { list ->
                val near = list.filter { (it.distanceKm ?: 0f) < 150f }
                if (near.isNotEmpty()) near + (list - near.toSet()) else list
            }
            .take(10)
    }

    /** Photon: OpenStreetMap search that copes with typos and shop names, biased to where you are. */
    private fun photon(q: String): List<Place> = try {
        val r = Hub.ride.value
        var url = "https://photon.komoot.io/api/?limit=8&lang=en&q=" + Uri.encode(q)
        if (r.hasFix) url += "&lat=${r.lat}&lon=${r.lon}&location_bias_scale=0.4"
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "RiderDeck/0.2 (personal riding app)")
        c.connectTimeout = 7000
        c.readTimeout = 7000
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val feats = o.optJSONArray("features") ?: JSONArray()
        (0 until feats.length()).mapNotNull { i ->
            val f = feats.getJSONObject(i)
            val pr = f.optJSONObject("properties") ?: return@mapNotNull null
            val cc = pr.optString("countrycode")
            if (cc.isNotBlank() && !cc.equals("IN", true)) return@mapNotNull null
            val co = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val street = listOf(pr.optString("housenumber"), pr.optString("street")).filter { it.isNotBlank() }.joinToString(" ")
            val name = pr.optString("name").ifBlank { street }.ifBlank { return@mapNotNull null }
            val sub = listOf(street.takeIf { it != name }, pr.optString("district"), pr.optString("city"), pr.optString("state"))
                .filter { !it.isNullOrBlank() }.distinct().joinToString(", ")
            Place(name, sub, co.getDouble(1), co.getDouble(0), null)
        }
    } catch (e: Exception) {
        Log.w(TAG, "photon failed: $e")
        emptyList()
    }

    private suspend fun geocode(ctx: Context, q: String, near: Boolean): List<Place> {
        if (!Geocoder.isPresent()) return emptyList()
        val g = Geocoder(ctx, Locale("en", "IN"))
        val r = Hub.ride.value
        val box = 0.9 // degrees, roughly 100 km
        val list: List<Address> = try {
            if (Build.VERSION.SDK_INT >= 33) {
                withTimeoutOrNull(7000) {
                    suspendCancellableCoroutine<List<Address>> { cont ->
                        val cb = object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        }
                        if (near) g.getFromLocationName(q, 6, r.lat - box, r.lon - box, r.lat + box, r.lon + box, cb)
                        else g.getFromLocationName(q, 6, cb)
                    }
                } ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                val res = if (near) g.getFromLocationName(q, 6, r.lat - box, r.lon - box, r.lat + box, r.lon + box)
                else g.getFromLocationName(q, 6)
                res ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        return list.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
            val line = a.getAddressLine(0) ?: ""
            fun good(x: String?) = x != null && x.trim().length > 2 && x.count { it.isLetter() } >= 3
            val name = a.featureName?.takeIf { good(it) }
                ?: listOf(a.premises, a.thoroughfare, a.subLocality, a.locality).firstOrNull { good(it) }
                ?: line.split(",").map { it.trim() }.firstOrNull { good(it) }
                ?: q
            Place(name, line, a.latitude, a.longitude, null)
        }
    }

    private fun nominatim(q: String): List<Place> = try {
        val r = Hub.ride.value
        var url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&countrycodes=in&q=" + Uri.encode(q)
        if (r.hasFix) url += "&viewbox=${r.lon - 1},${r.lat + 1},${r.lon + 1},${r.lat - 1}"
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "RiderDeck/0.2 (personal riding app)")
        c.connectTimeout = 8000
        c.readTimeout = 8000
        val body = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONArray(body)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val display = o.optString("display_name")
            Place(
                o.optString("name").ifBlank { display.substringBefore(",") },
                display,
                o.optString("lat").toDouble(),
                o.optString("lon").toDouble(),
                null,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
