package io.github.sammeeeeeeee.higiene.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** What Google Maps knows about a business. Shown only while the page is open: Google allows apps to keep only the place ID. */
data class GooglePlace(
    val placeId: String,
    val rating: Double?,
    val ratingCount: Int?,
    val openNow: Boolean?,
    val hours: List<String>,
    val phone: String?,
    val website: String?,
    val mapsUri: String?,
    val permanentlyClosed: Boolean,
    /** Third parties Google got some of this data from; Google requires them to be credited. */
    val attributions: List<Attribution> = emptyList(),
) {
    data class Attribution(val provider: String, val uri: String?)

    val hasDetails get() = rating != null || openNow != null || hours.isNotEmpty() || phone != null || website != null || permanentlyClosed
}

/** A street-level photo of the shopfront, from Google Street View or Mapillary. */
sealed interface StreetPhoto {
    val imageUrl: String

    data class Google(
        override val imageUrl: String,
        val copyright: String?,
        val date: String?,
        /** Opens the same view in Google Maps, which needs no key. */
        val viewUrl: String,
    ) : StreetPhoto

    data class Mapillary(override val imageUrl: String, val id: String, val author: String?, val capturedAt: Long?) : StreetPhoto {
        val viewUrl get() = "https://www.mapillary.com/app/?pKey=$id"
    }
}

/**
 * Google Places (New) and Street View Static API with the user's own key. Each call is counted by
 * [ApiBudget] first, so the built-in daily and monthly caps hold. The place ID is the only Places
 * content kept (https://developers.google.com/maps/documentation/places/web-service/policies);
 * Street View images are never written to disk.
 */
class GoogleClient(
    private val http: OkHttpClient,
    private val userDb: UserDb,
    private val budget: ApiBudget,
    private val identity: AppIdentity,
    private val placesBase: String = "https://places.googleapis.com/v1",
    private val mapsBase: String = "https://maps.googleapis.com/maps/api",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // Places --------------------------------------------------------------------------------------

    fun place(e: Establishment, key: String): GooglePlace? {
        val placeId = placeIdFor(e, key) ?: return null
        if (!budget.take(Api.GOOGLE_DETAILS)) return null
        val request = Request.Builder()
            .url("$placesBase/places/$placeId?languageCode=en-GB&regionCode=gb")
            .headers(key, DETAIL_FIELDS)
            .build()
        http.newCall(request).execute().use { r ->
            // A place ID can expire; search again next time.
            if (r.code == 404) {
                forgetPlace(e.id)
                return null
            }
            if (!r.isSuccessful) throw IOException("Google Places: HTTP ${r.code}")
            return parsePlace(JSONObject(r.body.string()))
        }
    }

    private fun placeIdFor(e: Establishment, key: String): String? {
        // With neither a position nor a postcode, a name-only search across Great Britain can't be
        // checked against anything.
        if ((e.lat == null || e.lon == null) && e.postcode == null) return null
        val now = clock()
        userDb.readableDatabase.rawQuery("SELECT place, matched, at FROM gplace WHERE id=?", arrayOf(e.id.toString())).use { c ->
            if (c.moveToFirst()) {
                val matched = c.getInt(1) == 1
                val age = now - c.getLong(2)
                // Google suggests refreshing stored place IDs after 12 months; retry a failed match after a month.
                if (matched && age < YEAR) return c.getString(0)
                if (!matched && age < MONTH) return null
            }
        }
        if (!budget.take(Api.GOOGLE_SEARCH)) return null
        val body = JSONObject()
            .put("textQuery", listOfNotNull(e.name, e.postcode ?: e.address).joinToString(", "))
            .put("regionCode", "gb")
            .put("languageCode", "en-GB")
            .put("pageSize", 5)
        if (e.lat != null && e.lon != null) {
            body.put(
                "locationBias",
                JSONObject().put("circle", JSONObject().put("center", JSONObject().put("latitude", e.lat).put("longitude", e.lon)).put("radius", 300.0)),
            )
        }
        val request = Request.Builder()
            .url("$placesBase/places:searchText")
            .headers(key, SEARCH_FIELDS)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val candidates = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Google Places: HTTP ${r.code}")
            parseCandidates(JSONObject(r.body.string()))
        }
        val match = bestMatch(e, candidates)
        userDb.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO gplace(id, place, matched, at) VALUES(?,?,?,?)",
            arrayOf<Any?>(e.id, match?.id, if (match != null) 1 else 0, now),
        )
        return match?.id
    }

    private fun forgetPlace(id: Long) {
        userDb.writableDatabase.execSQL("DELETE FROM gplace WHERE id=?", arrayOf<Any>(id))
    }

    private fun Request.Builder.headers(key: String, fields: String): Request.Builder {
        header("X-Goog-Api-Key", key)
        header("X-Goog-FieldMask", fields)
        identity.headers.forEach { (k, v) -> header(k, v) }
        return this
    }

    // Street View -----------------------------------------------------------------------------------

    /** Checks for a photo with the free metadata call, then builds the (counted) image request. */
    fun streetPhoto(e: Establishment, key: String): StreetPhoto.Google? {
        val exact = e.lat != null && e.lon != null && e.loc?.approximate != true
        val address = listOfNotNull(e.address, e.postcode).joinToString(", ").ifBlank { null }
        val location = if (exact) "%.6f,%.6f".format(java.util.Locale.US, e.lat, e.lon) else address ?: return null
        val meta = "$mapsBase/streetview/metadata".toHttpUrl().newBuilder()
            .addQueryParameter("location", location)
            .addQueryParameter("radius", "50")
            .addQueryParameter("source", "outdoor")
            .addQueryParameter("key", key)
            .build()
        val m = http.newCall(Request.Builder().url(meta).apply { identity.headers.forEach { (k, v) -> header(k, v) } }.build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Street View: HTTP ${r.code}")
            JSONObject(r.body.string())
        }
        if (m.optString("status") != "OK") return null
        val pano = m.optString("pano_id").ifEmpty { return null }
        val panoLoc = m.optJSONObject("location")
        if (!budget.take(Api.GOOGLE_STREETVIEW)) return null

        val image = "$mapsBase/streetview".toHttpUrl().newBuilder()
            .addQueryParameter("size", "640x400")
            .addQueryParameter("fov", "80")
            .addQueryParameter("pitch", "5")
            .addQueryParameter("return_error_code", "true")
            .addQueryParameter("key", key)
        val view = "https://www.google.com/maps/@".toHttpUrl().newBuilder()
            .addQueryParameter("api", "1")
            .addQueryParameter("map_action", "pano")
            .addQueryParameter("pano", pano)
        if (exact && panoLoc != null) {
            // Aim from where the photo was taken at the business.
            val heading = bearing(panoLoc.optDouble("lat"), panoLoc.optDouble("lng"), e.lat!!, e.lon!!)
            val h = "%.0f".format(java.util.Locale.US, heading)
            image.addQueryParameter("pano", pano).addQueryParameter("heading", h)
            view.addQueryParameter("heading", h)
        } else {
            // Without an exact position Google aims the camera at the address itself.
            image.addQueryParameter("location", location).addQueryParameter("source", "outdoor")
        }
        return StreetPhoto.Google(
            imageUrl = image.build().toString(),
            copyright = m.optString("copyright").ifEmpty { null },
            date = m.optString("date").ifEmpty { null },
            viewUrl = view.build().toString(),
        )
    }

    // Key check (free calls only) --------------------------------------------------------------------

    sealed interface KeyCheck {
        data object Ok : KeyCheck
        data class Problem(val api: String, val message: String) : KeyCheck
    }

    /**
     * Tries the key on calls Google does not charge for: Street View metadata and an IDs-only
     * Text Search. Reports the first API that refuses it, with Google's reason.
     */
    fun checkKey(key: String): KeyCheck {
        val meta = "$mapsBase/streetview/metadata".toHttpUrl().newBuilder()
            .addQueryParameter("location", "51.5007,-0.1246")
            .addQueryParameter("key", key)
            .build()
        http.newCall(Request.Builder().url(meta).apply { identity.headers.forEach { (k, v) -> header(k, v) } }.build()).execute().use { r ->
            val o = runCatching { JSONObject(r.body.string()) }.getOrNull()
            val status = o?.optString("status")
            if (!r.isSuccessful || status == "REQUEST_DENIED" || status == "OVER_QUERY_LIMIT") {
                return KeyCheck.Problem("Street View Static API", o?.optString("error_message")?.ifEmpty { null } ?: status ?: "HTTP ${r.code}")
            }
        }
        val search = Request.Builder()
            .url("$placesBase/places:searchText")
            .headers(key, "places.id")
            .post(JSONObject().put("textQuery", "Food Standards Agency, London").put("pageSize", 1).toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(search).execute().use { r ->
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(r.body.string()).optJSONObject("error")?.optString("message") }.getOrNull()
                return KeyCheck.Problem("Places API (New)", msg?.ifEmpty { null } ?: "HTTP ${r.code}")
            }
        }
        return KeyCheck.Ok
    }

    internal data class Candidate(val id: String, val name: String, val lat: Double?, val lon: Double?, val address: String? = null)

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
        private const val MONTH = 30 * DAY
        private const val YEAR = 365 * DAY

        /** Pro tier: enough to check the match before paying for details. */
        const val SEARCH_FIELDS = "places.id,places.displayName,places.location,places.formattedAddress"

        /** Enterprise tier (rating, hours, phone, website). Reviews are left out: they cost more and need author credits. */
        const val DETAIL_FIELDS = "id,businessStatus,googleMapsUri,rating,userRatingCount,currentOpeningHours,regularOpeningHours,nationalPhoneNumber,websiteUri,attributions"

        internal fun parseCandidates(o: JSONObject): List<Candidate> {
            val places = o.optJSONArray("places") ?: return emptyList()
            return (0 until places.length()).mapNotNull { i ->
                val p = places.optJSONObject(i) ?: return@mapNotNull null
                val id = p.optString("id").ifEmpty { return@mapNotNull null }
                val loc = p.optJSONObject("location")
                Candidate(
                    id = id,
                    name = p.optJSONObject("displayName")?.optString("text").orEmpty(),
                    lat = loc?.optDouble("latitude")?.takeUnless { it.isNaN() },
                    lon = loc?.optDouble("longitude")?.takeUnless { it.isNaN() },
                    address = p.optString("formattedAddress").ifEmpty { null },
                )
            }
        }

        internal fun parsePlace(o: JSONObject): GooglePlace {
            fun JSONArray?.strings() = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).ifEmpty { null } }
            val current = o.optJSONObject("currentOpeningHours")
            val regular = o.optJSONObject("regularOpeningHours")
            return GooglePlace(
                placeId = o.optString("id"),
                rating = o.optDouble("rating").takeUnless { it.isNaN() || it <= 0 },
                ratingCount = if (o.has("userRatingCount")) o.optInt("userRatingCount") else null,
                openNow = current?.takeIf { it.has("openNow") }?.optBoolean("openNow"),
                hours = (regular ?: current)?.optJSONArray("weekdayDescriptions").strings(),
                phone = o.optString("nationalPhoneNumber").ifEmpty { null },
                website = o.optString("websiteUri").ifEmpty { null }?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                mapsUri = o.optString("googleMapsUri").ifEmpty { null }?.takeIf { it.startsWith("https://") },
                permanentlyClosed = o.optString("businessStatus") == "CLOSED_PERMANENTLY",
                attributions = o.optJSONArray("attributions")?.let { a ->
                    (0 until a.length()).mapNotNull { i ->
                        val x = a.optJSONObject(i) ?: return@mapNotNull null
                        val provider = x.optString("provider").ifBlank { return@mapNotNull null }
                        GooglePlace.Attribution(provider, x.optString("providerUri").takeIf { it.startsWith("https://") || it.startsWith("http://") })
                    }
                }.orEmpty(),
            )
        }

        /**
         * Picks the search result that is this business: a similar name close to the FSA position.
         * Postcode-centre positions are looser. With no position at all, the name must match fully and
         * Google's address must carry the same postcode.
         */
        internal fun bestMatch(e: Establishment, candidates: List<Candidate>): Candidate? {
            val maxDistance = if (e.loc?.approximate == true) 400.0 else 150.0
            val postcode = e.pcKey
            return candidates
                .map { c ->
                    val d = if (e.lat != null && e.lon != null && c.lat != null && c.lon != null) Repository.distance(e.lat, e.lon, c.lat, c.lon) else null
                    Triple(c, nameSimilarity(e.name, c.name), d)
                }
                .filter { (c, sim, d) ->
                    when {
                        d != null -> d <= maxDistance && sim >= 0.5
                        e.lat != null -> false
                        else -> sim >= 0.99 && postcode != null && c.address?.uppercase()?.replace(" ", "")?.contains(postcode) == true
                    }
                }
                .sortedWith(compareByDescending<Triple<Candidate, Double, Double?>> { it.second }.thenBy { it.third ?: Double.MAX_VALUE })
                .firstOrNull()?.first
        }

        private val STOP = setOf("ltd", "limited", "the", "and", "plc", "llp", "co", "uk", "t", "a", "of", "at", "on", "in", "by")

        /**
         * Words that say what kind of place it is, or where, rather than which one. Two different cafés
         * on Station Road share "station", "road" and "cafe", so these never count as a name match.
         */
        private val GENERIC = setOf(
            "cafe", "caffe", "coffee", "tea", "restaurant", "bistro", "brasserie", "bar", "pub", "inn", "hotel", "grill", "kitchen",
            "house", "express", "takeaway", "take", "away", "food", "foods", "store", "stores", "shop", "market", "supermarket",
            "convenience", "newsagent", "newsagents", "off", "licence", "wine", "wines", "bakery", "bakers", "deli", "butchers", "butcher",
            "pizza", "pizzeria", "kebab", "kebabs", "fish", "chips", "chip", "chicken", "burger", "burgers", "indian", "chinese",
            "thai", "italian", "tandoori", "balti", "curry", "noodle", "noodles", "sushi", "catering", "services", "centre", "center",
            "club", "station", "road", "street", "lane", "high", "square", "park", "london", "local", "news",
        )

        private fun tokens(s: String): List<String> =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .replace("&", " and ").replace(Regex("['’`]"), "")
                .split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in STOP }

        /**
         * 1.0 when one name, ignoring spaces and filler words, is the other or a run of whole words
         * inside it ("72 China" in "72 China Restaurant", "McDonald's" and "Mc Donalds"). Otherwise the
         * share of distinctive words they have in common. Kind-of-place words never count.
         */
        internal fun nameSimilarity(a: String, b: String): Double {
            val ta = tokens(a)
            val tb = tokens(b)
            if (ta.isEmpty() || tb.isEmpty()) return 0.0
            if (ta.joinToString("") == tb.joinToString("")) return 1.0
            val (short, long) = if (ta.joinToString("").length <= tb.joinToString("").length) ta to tb else tb to ta
            if (short.any { it !in GENERIC } && containsOnWordBoundaries(long, short.joinToString(""))) return 1.0
            val da = ta.filter { it !in GENERIC }.toSet()
            val db = tb.filter { it !in GENERIC }.toSet()
            if (da.isEmpty() || db.isEmpty()) return 0.0
            return da.intersect(db).size.toDouble() / minOf(da.size, db.size)
        }

        /** Whether [needle] (4+ letters) equals some run of consecutive whole tokens of [words], joined. */
        private fun containsOnWordBoundaries(words: List<String>, needle: String): Boolean {
            if (needle.length < 4) return words.joinToString("") == needle
            for (start in words.indices) {
                val sb = StringBuilder()
                for (end in start until words.size) {
                    sb.append(words[end])
                    if (sb.length == needle.length && sb.toString() == needle) return true
                    if (sb.length >= needle.length) break
                }
            }
            return false
        }

        /** Compass bearing in degrees from one point to another. */
        fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dl = Math.toRadians(lon2 - lon1)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            return (Math.toDegrees(atan2(y, x)) + 360) % 360
        }
    }
}
