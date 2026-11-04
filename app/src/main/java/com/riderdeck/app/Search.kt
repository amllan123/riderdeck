package com.riderdeck.app

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

data class Place(val title: String, val sub: String, val lat: Double, val lon: Double, val distanceKm: Float?)

data class Pin(val lat: Double, val lon: Double, val title: String)

/**
 * Place search for the map. Uses the phone's built-in geocoder (Google's, no API key),
 * biased to ~100 km around you, then falls back to OpenStreetMap's Nominatim.
 */
object PlaceSearch {

    suspend fun find(ctx: Context, query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val r = Hub.ride.value
        var found = geocode(ctx, q, near = r.hasFix)
        if (found.isEmpty() && r.hasFix) found = geocode(ctx, q, near = false)
        if (found.isEmpty()) found = nominatim(q)
        found
            .distinctBy { "%.4f,%.4f".format(Locale.ROOT, it.lat, it.lon) }
            .map { it.copy(distanceKm = distanceFromMe(it.lat, it.lon)) }
            .sortedBy { it.distanceKm ?: Float.MAX_VALUE }
    }

    fun distanceFromMe(lat: Double, lon: Double): Float? {
        val r = Hub.ride.value
        if (!r.hasFix) return null
        val out = FloatArray(1)
        Location.distanceBetween(r.lat, r.lon, lat, lon, out)
        return out[0] / 1000f
    }

    private suspend fun geocode(ctx: Context, q: String, near: Boolean): List<Place> {
        if (!Geocoder.isPresent()) return emptyList()
        val g = Geocoder(ctx, Locale("en", "IN"))
        val r = Hub.ride.value
        val box = 0.9 // degrees, roughly 100 km
        val list: List<Address> = try {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    val cb = object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(emptyList())
                        }
                    }
                    if (near) g.getFromLocationName(q, 8, r.lat - box, r.lon - box, r.lat + box, r.lon + box, cb)
                    else g.getFromLocationName(q, 8, cb)
                }
            } else {
                @Suppress("DEPRECATION")
                val res = if (near) g.getFromLocationName(q, 8, r.lat - box, r.lon - box, r.lat + box, r.lon + box)
                else g.getFromLocationName(q, 8)
                res ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        return list.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
            val line = a.getAddressLine(0) ?: ""
            val name = a.featureName?.takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } }
                ?: line.substringBefore(",").ifBlank { q }
            Place(name, line, a.latitude, a.longitude, null)
        }
    }

    private fun nominatim(q: String): List<Place> = try {
        val r = Hub.ride.value
        var url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&countrycodes=in&q=" + Uri.encode(q)
        if (r.hasFix) url += "&viewbox=${r.lon - 1},${r.lat + 1},${r.lon + 1},${r.lat - 1}"
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "RiderDeck/0.1 (personal riding app)")
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
