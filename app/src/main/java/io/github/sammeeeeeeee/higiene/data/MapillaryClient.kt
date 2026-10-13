package io.github.sammeeeeeeee.higiene.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos

/**
 * Street-level photos from Mapillary (https://www.mapillary.com/developer/api-documentation) with
 * the user's own client token. Photos are CC BY-SA 4.0 and are credited to their author.
 */
class MapillaryClient(
    private val http: OkHttpClient,
    private val budget: ApiBudget,
    private val base: String = "https://graph.mapillary.com",
) {
    /** The nearest photo facing the business (or a 360° one), within about 50 m. */
    fun photo(e: Establishment, token: String): StreetPhoto.Mapillary? {
        // Postcode-centre positions are too rough to aim a photo.
        if (e.lat == null || e.lon == null || e.loc?.approximate == true) return null
        if (!budget.take(Api.MAPILLARY)) return null
        val dLat = RADIUS / 111_320.0
        val dLon = RADIUS / (111_320.0 * cos(Math.toRadians(e.lat)))
        val url = "$base/images".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id,computed_geometry,geometry,computed_compass_angle,compass_angle,is_pano,captured_at,thumb_1024_url,creator")
            .addQueryParameter("bbox", "%.6f,%.6f,%.6f,%.6f".format(java.util.Locale.US, e.lon - dLon, e.lat - dLat, e.lon + dLon, e.lat + dLat))
            .addQueryParameter("limit", "50")
            .build()
        val o = http.newCall(Request.Builder().url(url).header("Authorization", "OAuth $token").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Mapillary: HTTP ${r.code}")
            JSONObject(r.body.string())
        }
        return choose(e.lat, e.lon, parse(o))
    }

    /** Checks the token with a one-photo search. Returns null when it works, else the reason. */
    fun checkToken(token: String): String? {
        val url = "$base/images".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id")
            .addQueryParameter("bbox", "-0.1260,51.5000,-0.1230,51.5015")
            .addQueryParameter("limit", "1")
            .build()
        http.newCall(Request.Builder().url(url).header("Authorization", "OAuth $token").build()).execute().use { r ->
            if (r.isSuccessful) return null
            val msg = runCatching { JSONObject(r.body.string()).optJSONObject("error")?.optString("message") }.getOrNull()
            return msg?.ifEmpty { null } ?: "HTTP ${r.code}"
        }
    }

    internal data class Image(
        val id: String,
        val lat: Double,
        val lon: Double,
        val compass: Double?,
        val pano: Boolean,
        val capturedAt: Long?,
        val url: String,
        val author: String?,
    )

    companion object {
        private const val RADIUS = 50.0

        /** How far off the camera may point from the business and still show it. */
        private const val FACING = 50.0

        internal fun parse(o: JSONObject): List<Image> {
            val data = o.optJSONArray("data") ?: return emptyList()
            return (0 until data.length()).mapNotNull { i ->
                val d = data.optJSONObject(i) ?: return@mapNotNull null
                val geo = (d.optJSONObject("computed_geometry") ?: d.optJSONObject("geometry"))?.optJSONArray("coordinates") ?: return@mapNotNull null
                val url = d.optString("thumb_1024_url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                val compass = listOf("computed_compass_angle", "compass_angle").firstNotNullOfOrNull { k ->
                    d.optDouble(k).takeUnless { it.isNaN() }
                }
                Image(
                    id = d.optString("id").ifEmpty { return@mapNotNull null },
                    lat = geo.optDouble(1),
                    lon = geo.optDouble(0),
                    compass = compass,
                    pano = d.optBoolean("is_pano"),
                    capturedAt = d.optLong("captured_at").takeIf { it > 0 },
                    url = url,
                    author = d.optJSONObject("creator")?.optString("username")?.ifEmpty { null },
                )
            }
        }

        /** Keeps photos that look at the business (or are 360°), nearest first, then newest. */
        internal fun choose(lat: Double, lon: Double, images: List<Image>): StreetPhoto.Mapillary? =
            images
                .filter { it.lat.isFinite() && it.lon.isFinite() }
                .filter { img ->
                    img.pano || img.compass?.let { c ->
                        val want = GoogleClient.bearing(img.lat, img.lon, lat, lon)
                        val diff = abs(((c - want + 540) % 360) - 180)
                        diff <= FACING
                    } == true
                }
                .map { it to Repository.distance(it.lat, it.lon, lat, lon) }
                .filter { (_, d) -> d <= RADIUS * 1.5 }
                // Within ~10 m counts as equally close; then prefer the newest.
                .sortedWith(compareBy<Pair<Image, Double>> { (it.second / 10).toInt() }.thenByDescending { it.first.capturedAt ?: 0L })
                .firstOrNull()?.first
                ?.let { StreetPhoto.Mapillary(it.url, it.id, it.author, it.capturedAt) }

        fun capturedDate(ms: Long): java.time.LocalDate = Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }
}
