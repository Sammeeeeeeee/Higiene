package io.github.sammeeeeeeee.higiene.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads OS Code-Point Open (all GB postcodes, quarterly, ~14 MB zip, Open Government Licence)
 * and builds the offline [PostcodeIndex] plus outcode/sector centres for postcode search.
 * Northern Ireland postcodes aren't in Code-Point Open; they fall back to FSA-derived positions.
 */
class CodePointImporter(
    private val db: Db,
    private val http: OkHttpClient,
    private val indexFile: File,
) {
    /** Returns true if a new index was built. */
    fun refreshIfNeeded(force: Boolean, isStopped: () -> Boolean): Boolean {
        val listing = http.newCall(Request.Builder().url(LISTING_URL).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("OS listing failed: HTTP ${r.code}")
            JSONArray(r.body.string())
        }
        var md5: String? = null
        var url: String? = null
        for (i in 0 until listing.length()) {
            val o = listing.getJSONObject(i)
            if (o.optString("area") == "GB" && o.optString("format") == "CSV") {
                md5 = o.optString("md5").ifEmpty { null }
                url = o.optString("url").ifEmpty { null }
            }
        }
        if (url == null) throw IOException("OS listing has no GB CSV download")
        if (!force && md5 != null && md5 == db.meta(Db.META_CODEPOINT_MD5) && indexFile.exists()) {
            db.setMeta(db.writableDatabase, Db.META_CODEPOINT_AT, System.currentTimeMillis().toString())
            return false
        }

        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Code-Point download failed: HTTP ${r.code}")
            importZip(r.body.byteStream(), md5, isStopped)
        }
        return true
    }

    /** Builds the index and area centres from a Code-Point Open CSV zip, then re-places businesses. */
    fun importZip(input: java.io.InputStream, md5: String?, isStopped: () -> Boolean) {
        val builder = PostcodeIndex.Builder()
        // Sums for outcode and sector centres, in grid metres.
        val areas = HashMap<String, LongArray>()
        val digest = MessageDigest.getInstance("MD5")
        var copyrightYear: String? = null
        val digesting = DigestInputStream(input, digest)
        ZipInputStream(digesting).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.lowercase()
                if (name.endsWith("doc/metadata.txt")) {
                    // "COPYRIGHT DATE: 20260720" -> year for the attribution statements.
                    val text = zip.readBytes().toString(Charsets.ISO_8859_1)
                    copyrightYear = Regex("COPYRIGHT DATE:\\s*(\\d{4})").find(text)?.groupValues?.get(1)
                    continue
                }
                if (!name.startsWith("data/csv/") || !name.endsWith(".csv")) continue
                val reader = CsvReader(zip.nonClosing())
                while (reader.next()) {
                    if (builder.size % 5000 == 0 && isStopped()) throw CancellationException("Stopped")
                    // Postcode, Positional_quality_indicator, Eastings, Northings, ...
                    val key = Postcodes.key(reader.string(0)) ?: continue
                    val quality = reader.long(1) ?: continue
                    val e = reader.long(2)?.toInt() ?: continue
                    val n = reader.long(3)?.toInt() ?: continue
                    if (quality >= 90 || e <= 0 || n <= 0) continue // 90 = no coordinates
                    if (!Postcodes.isFull(key)) continue
                    if (builder.add(key, e, n)) {
                        accumulate(areas, Postcodes.outcodeOf(key), e, n)
                        accumulate(areas, Postcodes.sectorOf(key), e, n)
                    }
                }
            }
            // ZipInputStream stops at the central directory; read the rest so the MD5 covers the whole file.
            val sink = ByteArray(8192)
            while (digesting.read(sink) >= 0) { /* drain */ }
        }
        if (md5 != null) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(md5, ignoreCase = true)) throw IOException("Code-Point download corrupted (MD5 mismatch)")
        }
        if (builder.size < MIN_POSTCODES) throw IOException("Code-Point file looks incomplete (${builder.size} postcodes)")

        builder.writeTo(indexFile)
        val index = PostcodeIndex.open(indexFile) ?: throw IOException("Could not open postcode index")

        val w = db.writableDatabase
        w.beginTransactionNonExclusive()
        try {
            w.execSQL("DELETE FROM area")
            val insert = w.compileStatement("INSERT OR REPLACE INTO area(code, lat, lon) VALUES(?,?,?)")
            for ((code, sum) in areas) {
                val ll = Osgb.gridToWgs84(sum[0].toDouble() / sum[2], sum[1].toDouble() / sum[2])
                insert.bindString(1, code)
                insert.bindDouble(2, ll[0])
                insert.bindDouble(3, ll[1])
                insert.executeInsert()
            }
            // Prefer OS centres over FSA-derived averages now that they're available.
            w.execSQL("UPDATE est SET lat=NULL, lon=NULL, loc=NULL WHERE loc=${LocSource.POSTCODE_FSA.code}")
            FsaImporter.locate(w, index)
            db.setMeta(w, Db.META_CODEPOINT_MD5, md5)
            db.setMeta(w, Db.META_CODEPOINT_AT, System.currentTimeMillis().toString())
            copyrightYear?.let { db.setMeta(w, Db.META_CODEPOINT_YEAR, it) }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    private fun accumulate(areas: HashMap<String, LongArray>, code: String, e: Int, n: Int) {
        val s = areas.getOrPut(code) { LongArray(3) }
        s[0] += e.toLong()
        s[1] += n.toLong()
        s[2]++
    }

    companion object {
        const val LISTING_URL = "https://api.os.uk/downloads/v1/products/CodePointOpen/downloads"
        private const val MIN_POSTCODES = 1_000_000
    }
}

private fun ZipInputStream.nonClosing() = object : java.io.FilterInputStream(this) {
    override fun close() { /* keep the zip open for the next entry */ }
}
