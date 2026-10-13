package io.github.sammeeeeeeee.higiene.data

import android.database.Cursor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.util.zip.GZIPInputStream

/** Details volunteers have added to OpenStreetMap, linked to the FSA record by the fhrs:id tag. */
data class OsmDetails(
    /** OSM element, type letter plus id: "n123", "w45" or "r6". */
    val element: String,
    val openingHours: String? = null,
    private val cuisineTag: String? = null,
    private val websiteTag: String? = null,
    val phone: String? = null,
    val email: String? = null,
    private val menuTag: String? = null,
    val brandQid: String? = null,
    val wheelchair: String? = null,
    val takeaway: String? = null,
    val delivery: String? = null,
    val outdoorSeating: String? = null,
    private val dietsTag: String? = null,
) {
    val cuisine get() = cuisineTag?.split(';')?.map { it.trim().replace('_', ' ') }?.filter { it.isNotEmpty() }.orEmpty()
    val website get() = websiteTag?.let { webUrl(it) }
    val menu get() = menuTag?.let { webUrl(it) }
    val diets get() = dietsTag?.split(',')?.map { it.trim().replace('_', '-') }?.filter { it.isNotEmpty() }.orEmpty()

    val osmUrl: String
        get() {
            val type = when (element.firstOrNull()) { 'w' -> "way"; 'r' -> "relation"; else -> "node" }
            return "https://www.openstreetmap.org/$type/${element.drop(1)}"
        }

    /** True when there is something worth showing. */
    val hasDetails: Boolean
        get() = openingHours != null || cuisine.isNotEmpty() || diets.isNotEmpty() || website != null || phone != null ||
            email != null || menu != null || wheelchair != null || takeaway != null || delivery != null || outdoorSeating != null

    internal companion object {
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

        fun from(c: Cursor) = OsmDetails(
            element = c.getString(0),
            openingHours = c.getStringOrNull(1),
            cuisineTag = c.getStringOrNull(2),
            websiteTag = c.getStringOrNull(3),
            phone = c.getStringOrNull(4),
            email = c.getStringOrNull(5),
            menuTag = c.getStringOrNull(6),
            brandQid = c.getStringOrNull(7),
            wheelchair = c.getStringOrNull(8),
            takeaway = c.getStringOrNull(9),
            delivery = c.getStringOrNull(10),
            outdoorSeating = c.getStringOrNull(11),
            dietsTag = c.getStringOrNull(12),
        )

        const val COLUMNS = "el, hours, cuisine, website, phone, email, menu, brand, wheelchair, takeaway, delivery, outdoor, diets"
    }
}

/**
 * OpenStreetMap details for UK food businesses, from a small weekly file that
 * .github/workflows/osm-data.yml builds from the Geofabrik extract and publishes as a release asset.
 * The public Overpass servers do not allow apps to query them per business
 * (https://dev.overpass-api.de/overpass-doc/en/preface/commons.html), and a file also keeps which
 * businesses you look at on the phone.
 */
class OsmData(private val db: Db, private val http: OkHttpClient, private val url: String = URL) {

    sealed interface Outcome {
        data object Unchanged : Outcome
        data class Updated(val rows: Int) : Outcome
        /** The file has not been published yet. */
        data object Missing : Outcome
    }

    fun refresh(isStopped: () -> Boolean = { false }): Outcome {
        val etag = if (rows() > 0) db.meta(Db.META_OSM_ETAG) else null
        val request = Request.Builder().url(url).apply { if (etag != null) header("If-None-Match", etag) }.build()
        http.newCall(request).execute().use { r ->
            when {
                r.code == 304 -> {
                    db.setMeta(db.writableDatabase, Db.META_OSM_CHECKED_AT, System.currentTimeMillis().toString())
                    return Outcome.Unchanged
                }
                r.code == 404 -> return Outcome.Missing
                !r.isSuccessful -> throw IOException("OpenStreetMap file: HTTP ${r.code}")
            }
            val reader = GZIPInputStream(r.body.byteStream(), 64 * 1024).bufferedReader(Charsets.UTF_8)
            val rows = import(reader, isStopped) { w ->
                db.setMeta(w, Db.META_OSM_ETAG, r.header("ETag"))
                db.setMeta(w, Db.META_OSM_CHECKED_AT, System.currentTimeMillis().toString())
            }
            return Outcome.Updated(rows)
        }
    }

    /** Replaces the whole table from [reader] in one transaction; nothing changes if it fails. */
    internal fun import(reader: BufferedReader, isStopped: () -> Boolean, meta: (android.database.sqlite.SQLiteDatabase) -> Unit = {}): Int {
        val first = reader.readLine() ?: throw IOException("OpenStreetMap file is empty")
        val parts = first.split(' ')
        if (parts.size < 3 || parts[0] != "#" || parts[1] != "higiene-osm" || parts[2] != FORMAT) throw IOException("Unsupported OpenStreetMap file")
        val dataDate = parts.getOrNull(3)?.takeIf { it != "unknown" }
        if (reader.readLine() != HEADER) throw IOException("Unsupported OpenStreetMap file")

        val w = db.writableDatabase
        w.beginTransactionNonExclusive()
        try {
            w.execSQL("DELETE FROM osm")
            val insert = w.compileStatement("INSERT OR REPLACE INTO osm(id, $COLUMNS_SQL) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
            var n = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty() || line.startsWith("#")) continue
                val f = line.split('\t')
                if (f.size != FIELDS) continue
                val id = f[0].toLongOrNull() ?: continue
                if (f[1].isEmpty()) continue
                insert.clearBindings()
                insert.bindLong(1, id)
                for (i in 1 until FIELDS) if (f[i].isNotEmpty()) insert.bindString(i + 1, f[i])
                insert.executeInsert()
                if (++n % 4096 == 0 && isStopped()) throw kotlin.coroutines.cancellation.CancellationException("Stopped")
            }
            if (n < MIN_ROWS) throw IOException("OpenStreetMap file looks incomplete ($n rows)")
            db.setMeta(w, Db.META_OSM_ROWS, n.toString())
            db.setMeta(w, Db.META_OSM_DATE, dataDate)
            meta(w)
            w.setTransactionSuccessful()
            return n
        } finally {
            w.endTransaction()
        }
    }

    fun rows(): Int = db.meta(Db.META_OSM_ROWS)?.toIntOrNull() ?: 0

    fun details(id: Long): OsmDetails? =
        db.readableDatabase.rawQuery("SELECT ${OsmDetails.COLUMNS} FROM osm WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) OsmDetails.from(it) else null
        }

    /** Drops the downloaded details, e.g. when the user switches them off. */
    fun clear() {
        val w = db.writableDatabase
        w.beginTransactionNonExclusive()
        try {
            w.execSQL("DELETE FROM osm")
            listOf(Db.META_OSM_ROWS, Db.META_OSM_ETAG, Db.META_OSM_DATE, Db.META_OSM_CHECKED_AT).forEach { db.setMeta(w, it, null) }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    companion object {
        const val URL = "https://github.com/Sammeeeeeeee/Higiene/releases/download/osm-data/osm-fhrs.tsv.gz"
        const val FORMAT = "1"
        const val HEADER = "fhrs_id\tosm\topening_hours\tcuisine\twebsite\tphone\temail\tmenu\tbrand_wikidata\twheelchair\ttakeaway\tdelivery\toutdoor_seating\tdiets"
        private const val FIELDS = 14
        private const val COLUMNS_SQL = "el, hours, cuisine, website, phone, email, menu, brand, wheelchair, takeaway, delivery, outdoor, diets"

        /** Far below the ~126k rows of a real file; guards against a truncated or broken one. */
        internal var MIN_ROWS = 10_000
    }
}
