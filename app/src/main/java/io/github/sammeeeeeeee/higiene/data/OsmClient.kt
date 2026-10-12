package io.github.sammeeeeeeee.higiene.data

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** Extra details volunteers have added to OpenStreetMap, linked by the fhrs:id tag. */
data class OsmDetails(val type: String, val osmId: Long, val tags: Map<String, String>) {
    val openingHours get() = tags["opening_hours"]
    val cuisine get() = tags["cuisine"]?.split(';')?.map { it.trim().replace('_', ' ') }?.filter { it.isNotEmpty() }.orEmpty()
    val website get() = (tags["website"] ?: tags["contact:website"] ?: tags["url"])?.let { webUrl(it) }
    val phone get() = tags["phone"] ?: tags["contact:phone"]
    val email get() = tags["email"] ?: tags["contact:email"]
    val menu get() = tags["website:menu"]?.let { webUrl(it) }
    val brandQid get() = tags["brand:wikidata"]
    val wheelchair get() = tags["wheelchair"]
    val takeaway get() = tags["takeaway"]
    val delivery get() = tags["delivery"]
    val outdoorSeating get() = tags["outdoor_seating"]
    val diets: List<String>
        get() = listOf("vegetarian", "vegan", "halal", "kosher", "gluten_free")
            .filter { tags["diet:$it"] == "yes" || tags["diet:$it"] == "only" }
            .map { it.replace('_', '-') }
    val osmUrl get() = "https://www.openstreetmap.org/$type/$osmId"

    /** True when there is something worth showing; a bare match with only a name is not. */
    val hasDetails: Boolean
        get() = openingHours != null || cuisine.isNotEmpty() || diets.isNotEmpty() || website != null || phone != null ||
            email != null || menu != null || wheelchair != null || takeaway != null || delivery != null || outdoorSeating != null

    private companion object {
        /** Anyone can edit OSM: accept only web links, adding https:// when the scheme is missing. */
        fun webUrl(raw: String): String? {
            val v = raw.trim().substringBefore(';').trim()
            val lower = v.lowercase()
            return when {
                lower.startsWith("https://") || lower.startsWith("http://") -> v
                lower.contains("://") || lower.startsWith("javascript:") || lower.startsWith("file:") || v.isEmpty() -> null
                else -> "https://$v"
            }
        }
    }
}

sealed interface OsmResult {
    data class Found(val details: OsmDetails) : OsmResult
    data object NotMapped : OsmResult
    /** Daily fair-use cap reached. */
    data object DailyLimit : OsmResult

    /** The server is busy or asked us to back off; try again in a minute. */
    data object Busy : OsmResult
    data class Failed(val message: String) : OsmResult
}

/**
 * One Overpass query per business, only when its detail page is opened, cached for 30 days and
 * capped per day to respect the public server's fair-use policy
 * (https://dev.overpass-api.de/overpass-doc/en/preface/commons.html).
 */
class OsmClient(private val http: OkHttpClient, private val repo: Repository) {

    @Volatile
    private var backoffUntil = 0L

    fun details(fhrsId: Long): OsmResult {
        val now = System.currentTimeMillis()
        repo.cachedOsm(fhrsId)?.let { cached ->
            val ttl = if (cached.json != null) FOUND_TTL else MISSING_TTL
            if (now - cached.fetchedAt < ttl) return cached.json?.let { OsmResult.Found(parse(it)) } ?: OsmResult.NotMapped
        }
        val stale = repo.cachedOsm(fhrsId)?.json?.let { OsmResult.Found(parse(it)) }
        if (now < backoffUntil) return stale ?: OsmResult.Busy
        if (repo.osmCallsSince(now - DAY) >= DAILY_CAP) return stale ?: OsmResult.DailyLimit
        // Every attempt counts towards the cap, failed or not.
        repo.recordOsmCall()
        return try {
            val query = "[out:json][timeout:20];nwr[\"fhrs:id\"=\"$fhrsId\"];out tags;"
            val request = Request.Builder().url(ENDPOINT).post(FormBody.Builder().add("data", query).build()).build()
            http.newCall(request).execute().use { r ->
                if (r.code == 429 || r.code == 504 || r.code == 503) {
                    backoffUntil = System.currentTimeMillis() + BACKOFF
                    return stale ?: OsmResult.Busy
                }
                if (!r.isSuccessful) {
                    backoffUntil = System.currentTimeMillis() + BACKOFF
                    throw IOException("HTTP ${r.code}")
                }
                val reply = JSONObject(r.body.string())
                // Overpass reports timeouts and overload as HTTP 200 with a "remark"; don't cache those.
                val remark = reply.optString("remark")
                if (remark.contains("error", ignoreCase = true) || remark.contains("timed out", ignoreCase = true)) {
                    backoffUntil = System.currentTimeMillis() + BACKOFF
                    return stale ?: OsmResult.Busy
                }
                val elements = reply.optJSONArray("elements")
                // Prefer the element with the most tags if a business is mapped twice.
                var best: JSONObject? = null
                if (elements != null) {
                    for (i in 0 until elements.length()) {
                        val e = elements.getJSONObject(i)
                        val n = e.optJSONObject("tags")?.length() ?: 0
                        if (best == null || n > (best.optJSONObject("tags")?.length() ?: 0)) best = e
                    }
                }
                val stored = best?.let {
                    JSONObject().put("type", it.optString("type")).put("id", it.optLong("id")).put("tags", it.optJSONObject("tags") ?: JSONObject()).toString()
                }
                repo.cacheOsm(fhrsId, stored)
                stored?.let { OsmResult.Found(parse(it)) } ?: OsmResult.NotMapped
            }
        } catch (e: IOException) {
            backoffUntil = System.currentTimeMillis() + BACKOFF
            stale ?: OsmResult.Failed(e.message ?: "Network error")
        } catch (e: org.json.JSONException) {
            OsmResult.Failed("Unexpected reply from OpenStreetMap")
        }
    }

    private fun parse(json: String): OsmDetails {
        val o = JSONObject(json)
        val t = o.optJSONObject("tags") ?: JSONObject()
        val tags = buildMap { for (k in t.keys()) put(k, t.optString(k)) }
        return OsmDetails(o.optString("type", "node"), o.optLong("id"), tags)
    }

    companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val DAILY_CAP = 40
        private const val DAY = 24L * 60 * 60 * 1000
        private const val FOUND_TTL = 30 * DAY
        private const val MISSING_TTL = 7 * DAY
        private const val BACKOFF = 60_000L
    }
}
