package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline brand recognition for chain logos, from a table of UK food brands built from the OSM
 * name-suggestion-index (tools/build_brands.py). Only logo images are fetched over the network.
 */
class Brands(private val context: Context, private val http: OkHttpClient) {

    data class Brand(val name: String, val qid: String, val commonsFile: String?, val facebook: String?) {
        /** Logo URLs to try in order: Wikimedia Commons first, Facebook second. */
        val logoUrls: List<String>
            get() = listOfNotNull(commonsFile?.let { commonsThumb(it) }, facebook?.let { "https://graph.facebook.com/$it/picture?type=large" })
    }

    private class Table(val brands: List<Brand>, val names: Map<String, Int>, val byQid: Map<String, Brand>)

    @Volatile
    private var table: Table? = null
    private val matchCache = ConcurrentHashMap<String, Int>()
    private val wikidataCache = ConcurrentHashMap<String, Brand>()

    private fun table(): Table = table ?: synchronized(this) {
        table ?: load().also { table = it }
    }

    private fun load(): Table {
        val json = JSONObject(context.assets.open("brands.json").bufferedReader().use { it.readText() })
        val arr = json.getJSONArray("brands")
        val brands = List(arr.length()) { i ->
            val b = arr.getJSONArray(i)
            Brand(b.getString(0), b.getString(1), b.optStringOrNull(2), b.optStringOrNull(3))
        }
        val namesJson = json.getJSONObject("names")
        val names = HashMap<String, Int>(namesJson.length() * 2)
        for (k in namesJson.keys()) names[k] = namesJson.getInt(k)
        return Table(brands, names, brands.associateBy { it.qid })
    }

    /** Recognises a chain from an FSA business name, or null. */
    fun match(businessName: String): Brand? {
        val t = table()
        val idx = matchCache.getOrPut(businessName) { findIndex(t, normalise(businessName)) }
        return if (idx >= 0) t.brands[idx] else null
    }

    private fun findIndex(t: Table, name: String): Int = findIndex(t.names, name)

    /** Brand by Wikidata id (from OSM brand:wikidata). Falls back to Wikidata's own logo claims. */
    fun byQid(qid: String): Brand? {
        table().byQid[qid]?.let { return it }
        wikidataCache[qid]?.let { return it }
        val file = runCatching { wikidataLogo(qid) }.getOrNull() ?: return null
        return Brand(qid, qid, file, null).also { wikidataCache[qid] = it }
    }

    private fun wikidataLogo(qid: String): String? {
        if (!QID.matches(qid)) return null
        for (prop in listOf("P8972", "P154")) {
            val url = "https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&entity=$qid&property=$prop"
            val body = http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body.string() else null } ?: continue
            val claims = JSONObject(body).optJSONObject("claims")?.optJSONArray(prop) ?: continue
            var best: String? = null
            for (i in 0 until claims.length()) {
                val c = claims.getJSONObject(i)
                if (c.optJSONObject("qualifiers")?.has("P582") == true) continue // ended
                val v = c.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")?.ifEmpty { null } ?: continue
                if (c.optString("rank") == "preferred") return v
                if (best == null && c.optString("rank") != "deprecated") best = v
            }
            if (best != null) return best
        }
        return null
    }

    companion object {
        private val QID = Regex("^Q[0-9]+$")

        private val GENERIC = setOf(
            "ltd", "limited", "plc", "llp", "uk", "gb", "the", "and", "co", "company", "group", "retail", "franchise",
            "restaurant", "restaurants", "store", "stores", "shop", "supermarket", "supermarkets", "superstore", "local",
            "express", "extra", "metro", "cafe", "coffee", "bar", "pub", "hotel", "kitchen", "food", "foods", "bakery",
            "pizza", "takeaway", "drive", "thru", "outlet", "services", "station", "petrol", "garage", "unit",
        )

        /** Index of the brand whose name best matches an already [normalise]d business name, or -1. */
        internal fun findIndex(names: Map<String, Int>, name: String): Int {
            if (name.isEmpty()) return -1
            names[name]?.let { return it }
            val tokens = name.split(' ')
            // Longest multi-word key that is a whole-word prefix of the name.
            for (n in tokens.size - 1 downTo 2) {
                names[tokens.subList(0, n).joinToString(" ")]?.let { return it }
            }
            // Single-word keys ("costa", "paul") only when everything after them is generic,
            // so "Costa Coffee Ltd" matches but "Costa Del Sol" doesn't.
            val first = names[tokens[0]] ?: return -1
            return if (tokens.drop(1).all { it in GENERIC || it.all(Char::isDigit) }) first else -1
        }

        /** Same rules as norm() in tools/build_brands.py. */
        fun normalise(s: String): String {
            var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
            t = t.replace("&", " and ").replace("'", "").replace("’", "")
            t = t.replace(Regex("[^a-z0-9]+"), " ")
            return t.trim().replace(Regex(" +"), " ")
        }

        /** 120 px is a Wikimedia standard thumbnail size (non-standard sizes are rejected). */
        fun commonsThumb(file: String): String =
            "https://commons.wikimedia.org/wiki/Special:FilePath/" + URLEncoder.encode(file.replace(' ', '_'), "UTF-8").replace("+", "%20") + "?width=120"

        private fun org.json.JSONArray.optStringOrNull(i: Int): String? = if (isNull(i)) null else optString(i).ifEmpty { null }
    }
}
