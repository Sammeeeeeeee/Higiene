package io.github.sammeeeeeeee.higiene.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads the FSA's daily all-UK CSV and merges it into the local database in one transaction.
 *
 * Each row's hash is compared with the stored hash so unchanged rows (normally >99%) are skipped.
 * The transaction commits only if the byte count and the server's Content-MD5 match, so a cut-off
 * download can never delete or corrupt data.
 */
class FsaImporter(
    private val db: Db,
    private val http: OkHttpClient,
    private val postcodeIndex: () -> PostcodeIndex?,
    /** A real UK file has ~600k rows; anything far smaller is treated as a broken download. */
    private val minRows: Int = 100_000,
) {
    enum class Stage { CHECKING, DOWNLOADING, LOCATING }

    data class Progress(val stage: Stage, val done: Long, val total: Long)

    sealed interface Outcome {
        data object Unchanged : Outcome
        data class Updated(
            val rows: Int,
            val added: Int,
            val changed: Int,
            val removed: Int,
            val favouriteChanges: List<FavouriteChange>,
        ) : Outcome
    }

    fun refresh(force: Boolean, onProgress: (Progress) -> Unit, isStopped: () -> Boolean): Outcome {
        onProgress(Progress(Stage.CHECKING, 0, 0))
        val previousRows = db.meta(Db.META_ROWS)?.toIntOrNull() ?: 0
        val etag = db.meta(Db.META_ETAG)
        val request = Request.Builder()
            .url(CSV_URL)
            // Count raw bytes ourselves; no transparent gzip.
            .header("Accept-Encoding", "identity")
            .apply { if (!force && previousRows > 0 && etag != null) header("If-None-Match", etag) }
            .build()

        http.newCall(request).execute().use { response ->
            if (response.code == 304) {
                db.writableDatabase.let { w -> db.setMeta(w, Db.META_CHECKED_AT, System.currentTimeMillis().toString()) }
                return Outcome.Unchanged
            }
            if (!response.isSuccessful) throw IOException("FSA download failed: HTTP ${response.code}")
            val body = response.body
            val expectedLength = body.contentLength()
            val expectedMd5 = response.header("Content-MD5")
            val counting = CountingInputStream(body.byteStream())
            val md5 = MessageDigest.getInstance("MD5")
            val reader = CsvReader(DigestInputStream(counting, md5))
            val meta = mapOf(Db.META_ETAG to response.header("ETag"), Db.META_LAST_MODIFIED to response.header("Last-Modified"))
            return merge(reader, previousRows, isStopped, meta) { rows ->
                onProgress(Progress(Stage.DOWNLOADING, counting.count, expectedLength))
                if (rows < 0) {
                    // End of file reached: verify before the transaction is allowed to commit.
                    if (expectedLength >= 0 && counting.count != expectedLength) {
                        throw IOException("Download incomplete: ${counting.count} of $expectedLength bytes")
                    }
                    if (expectedMd5 != null) {
                        val actual = Base64.encodeToString(md5.digest(), Base64.NO_WRAP)
                        if (actual != expectedMd5.trim()) throw IOException("Download corrupted (MD5 mismatch)")
                    }
                    onProgress(Progress(Stage.LOCATING, 0, 0))
                }
            }
        }
    }

    /**
     * Merges rows from [reader] into the database. [checkpoint] is called periodically with the row
     * count, and once with -1 at end of input (it may throw to abort and roll back).
     */
    internal fun merge(
        reader: CsvReader,
        previousRows: Int,
        isStopped: () -> Boolean,
        meta: Map<String, String?> = emptyMap(),
        checkpoint: (Int) -> Unit,
    ): Outcome.Updated {
        if (!reader.next()) throw IOException("Empty file")
        val col = HashMap<String, Int>()
        for (i in 0 until reader.fieldCount) col[reader.string(i).trim()] = i
        for (required in REQUIRED) if (required !in col) throw IOException("FSA file format changed: missing $required")
        fun c(name: String) = col[name] ?: -1
        val cId = c("FHRSID")
        val cName = c("BusinessName")
        val cA1 = c("AddressLine1")
        val cA2 = c("AddressLine2")
        val cA3 = c("AddressLine3")
        val cA4 = c("AddressLine4")
        val cPc = c("PostCode")
        val cTypeId = c("BusinessTypeID")
        val cType = c("BusinessType")
        val cLa = c("LocalAuthorityCode")
        val cRating = c("RatingValue")
        val cKey = c("RatingKey")
        val cScheme = c("SchemeType")
        val cDate = c("RatingDate")
        val cHyg = c("Hygiene")
        val cStr = c("Structural")
        val cCim = c("ConfidenceInManagement")
        val cLat = c("Latitude")
        val cLon = c("Longitude")
        val cPending = c("NewRatingPending")
        val cReply = c("RightToReply")

        val w = db.writableDatabase
        // Existing ids and hashes, sorted by id for binary search.
        val (ids, hashes) = loadHashes(w)
        val seen = BooleanArray(ids.size)
        val favourites = loadFavourites(w)
        val firstImport = ids.isEmpty()
        val touched = LongHashSet()
        val types = HashMap<Int, String>()
        val favChanges = ArrayList<FavouriteChange>()
        val now = System.currentTimeMillis()
        var rows = 0
        var added = 0
        var changed = 0
        var removed = 0

        w.beginTransactionNonExclusive()
        try {
            val upsert = w.compileStatement(
                "INSERT OR REPLACE INTO est(id,name,address,pc,site,type,la,rating,rnum,rdate,hyg,str,cim,lat,lon,loc,pending,reply,welsh,fhis,h) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            )
            val ftsDelete = w.compileStatement("DELETE FROM est_fts WHERE docid=?")
            val ftsInsert = w.compileStatement("INSERT INTO est_fts(docid,name,address,pc) VALUES(?,?,?,?)")
            val history = w.compileStatement("INSERT INTO history(id,seen,old_rating,new_rating,old_date,new_date) VALUES(?,?,?,?,?,?)")

            while (reader.next()) {
                val id = reader.long(cId) ?: continue
                rows++
                if (rows % 2000 == 0) {
                    if (isStopped()) throw CancellationException("Refresh stopped")
                    checkpoint(rows)
                }
                typeOf(reader, cTypeId, cType, types)
                val idx = ids.binarySearch(id)
                if (idx >= 0) {
                    seen[idx] = true
                    if (hashes[idx] == reader.hash) continue
                }
                val name = reader.stringOrNull(cName) ?: "(No name)"
                val rating = Rating.fromCsv(reader.string(cRating))
                val date = parseDate(reader.stringOrNull(cDate))
                val existed = idx >= 0 || id in touched
                if (existed) {
                    val old = w.rawQuery("SELECT rating, rdate FROM est WHERE id=?", arrayOf(id.toString())).use {
                        if (it.moveToFirst()) Pair(Rating.fromCode(it.getString(0)), if (it.isNull(1)) null else it.getLong(1)) else null
                    }
                    if (old != null && (old.first != rating || old.second != date?.toEpochDay())) {
                        history.bindAll(id, now, old.first.code, rating.code, old.second, date?.toEpochDay())
                        history.executeInsert()
                        if (id in favourites) favChanges += FavouriteChange(id, name, old.first, rating, date)
                    }
                    ftsDelete.bindLong(1, id)
                    ftsDelete.executeUpdateDelete()
                    changed++
                } else {
                    added++
                }
                val address = listOf(cA1, cA2, cA3, cA4).mapNotNull { reader.stringOrNull(it) }.joinToString(", ").ifEmpty { null }
                val pc = Postcodes.key(reader.stringOrNull(cPc))
                val site = siteKey(reader.stringOrNull(cA1), pc)
                val rkey = reader.string(cKey).lowercase()
                val lat = reader.double(cLat)
                val lon = reader.double(cLon)
                val located = lat != null && lon != null && lat in 49.0..61.5 && lon in -9.0..2.5
                upsert.clearBindings()
                upsert.bindLong(1, id)
                upsert.bindString(2, name)
                upsert.bindNullable(3, address)
                upsert.bindNullable(4, pc)
                upsert.bindNullable(5, site)
                upsert.bindLong(6, reader.long(cTypeId) ?: 0)
                upsert.bindLong(7, reader.long(cLa) ?: 0)
                upsert.bindString(8, rating.code)
                upsert.bindNullable(9, rating.numeric?.toLong())
                upsert.bindNullable(10, date?.toEpochDay())
                upsert.bindNullable(11, reader.long(cHyg))
                upsert.bindNullable(12, reader.long(cStr))
                upsert.bindNullable(13, reader.long(cCim))
                if (located) {
                    upsert.bindLong(14, e6(lat))
                    upsert.bindLong(15, e6(lon))
                    upsert.bindLong(16, LocSource.FSA.code.toLong())
                } else {
                    upsert.bindNull(14)
                    upsert.bindNull(15)
                    upsert.bindNull(16)
                }
                upsert.bindLong(17, if (reader.string(cPending).trim().equals("true", true)) 1 else 0)
                upsert.bindNullable(18, reader.stringOrNull(cReply))
                upsert.bindLong(19, if (rkey.endsWith("cy-gb")) 1 else 0)
                upsert.bindLong(20, if (rating.isFhis || rkey.startsWith("fhis") || reader.string(cScheme).trim().equals("FHIS", true)) 1 else 0)
                upsert.bindLong(21, reader.hash)
                upsert.executeInsert()
                if (!firstImport) {
                    ftsInsert.bindLong(1, id)
                    ftsInsert.bindNullable(2, name)
                    ftsInsert.bindNullable(3, address)
                    ftsInsert.bindNullable(4, pc)
                    ftsInsert.executeInsert()
                    touched.add(id)
                }
            }
            checkpoint(-1)

            if (rows < minRows || (previousRows > 0 && rows < previousRows / 2)) {
                throw IOException("FSA file looks incomplete ($rows rows, previously $previousRows)")
            }

            // Businesses no longer in the file.
            for (i in ids.indices) {
                if (seen[i]) continue
                val id = ids[i]
                val old = w.rawQuery("SELECT rating, rdate, name FROM est WHERE id=?", arrayOf(id.toString())).use {
                    if (it.moveToFirst()) Triple(Rating.fromCode(it.getString(0)), if (it.isNull(1)) null else it.getLong(1), it.getString(2)) else null
                }
                if (old != null) {
                    history.bindAll(id, now, old.first.code, null, old.second, null)
                    history.executeInsert()
                    if (id in favourites) favChanges += FavouriteChange(id, old.third, old.first, null, null)
                }
                ftsDelete.bindLong(1, id)
                ftsDelete.executeUpdateDelete()
                w.delete("est", "id=?", arrayOf(id.toString()))
                removed++
            }

            if (firstImport) {
                w.execSQL("INSERT INTO est_fts(est_fts) VALUES('rebuild')")
                w.execSQL("INSERT INTO est_fts(est_fts) VALUES('optimize')")
            }

            for ((tid, tname) in types) {
                w.insertWithOnConflict("btype", null, ContentValues().apply { put("id", tid); put("name", tname) }, SQLiteDatabase.CONFLICT_REPLACE)
            }

            locate(w, postcodeIndex())

            for ((k, v) in meta) db.setMeta(w, k, v)
            db.setMeta(w, Db.META_ROWS, rows.toString())
            db.setMeta(w, Db.META_IMPORTED_AT, now.toString())
            db.setMeta(w, Db.META_CHECKED_AT, now.toString())
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        runCatching { w.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() } }
        return Outcome.Updated(rows, added, changed, removed, favChanges)
    }

    private fun typeOf(reader: CsvReader, cTypeId: Int, cType: Int, types: HashMap<Int, String>) {
        val tid = reader.long(cTypeId)?.toInt() ?: return
        if (tid !in types) reader.stringOrNull(cType)?.let { types[tid] = it }
    }

    private fun loadHashes(w: SQLiteDatabase): Pair<LongArray, LongArray> {
        w.rawQuery("SELECT id, h FROM est ORDER BY id", null).use { cur ->
            val ids = LongArray(cur.count)
            val hs = LongArray(cur.count)
            var i = 0
            while (cur.moveToNext()) {
                ids[i] = cur.getLong(0)
                hs[i] = cur.getLong(1)
                i++
            }
            return ids to hs
        }
    }

    private fun loadFavourites(w: SQLiteDatabase): Set<Long> =
        w.rawQuery("SELECT id FROM fav", null).use { cur -> buildSet { while (cur.moveToNext()) add(cur.getLong(0)) } }

    companion object {
        const val CSV_URL = "https://safhrsprodstorage.blob.core.windows.net/opendatafileblobstorage/FHRS_All_en-GB.csv"
        private val REQUIRED = listOf("FHRSID", "BusinessName", "BusinessTypeID", "LocalAuthorityCode", "RatingValue")

        fun parseDate(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

        /** Microdegrees, as stored. */
        fun e6(deg: Double): Long = Math.round(deg * 1_000_000)

        /**
         * Same first address line and postcode = same premises, for "Who's really cooking?".
         * Stored as a 64-bit hash of the normalised text to keep the index small.
         */
        fun siteKey(line1: String?, pcKey: String?): Long? {
            if (line1 == null || pcKey == null) return null
            val norm = buildString { for (ch in line1) if (ch.isLetterOrDigit()) append(ch.uppercaseChar()) }
            if (norm.isEmpty()) return null
            var h = -0x340d631b7bdddcdbL
            for (ch in "$norm|$pcKey") h = (h xor ch.code.toLong()) * 0x100000001b3L
            return h
        }

        /**
         * Gives approximate positions to businesses the FSA publishes without one: first the OS
         * postcode centre, else the average of FSA-located neighbours with the same postcode.
         */
        fun locate(w: SQLiteDatabase, index: PostcodeIndex?) {
            if (index != null) {
                val update = w.compileStatement("UPDATE est SET lat=?, lon=?, loc=${LocSource.POSTCODE_OS.code} WHERE id=?")
                w.rawQuery("SELECT id, pc FROM est WHERE lat IS NULL AND pc IS NOT NULL", null).use { cur ->
                    while (cur.moveToNext()) {
                        val ll = index.lookup(cur.getString(1)) ?: continue
                        update.bindLong(1, e6(ll[0]))
                        update.bindLong(2, e6(ll[1]))
                        update.bindLong(3, cur.getLong(0))
                        update.executeUpdateDelete()
                    }
                }
            }
            w.execSQL(
                """
                UPDATE est SET
                  lat = (SELECT CAST(round(avg(e2.lat)) AS INTEGER) FROM est e2 WHERE e2.pc = est.pc AND e2.loc = ${LocSource.FSA.code}),
                  lon = (SELECT CAST(round(avg(e2.lon)) AS INTEGER) FROM est e2 WHERE e2.pc = est.pc AND e2.loc = ${LocSource.FSA.code}),
                  loc = ${LocSource.POSTCODE_FSA.code}
                WHERE lat IS NULL AND pc IS NOT NULL
                  AND EXISTS (SELECT 1 FROM est e2 WHERE e2.pc = est.pc AND e2.loc = ${LocSource.FSA.code})
                """.trimIndent(),
            )
        }
    }
}

private fun SQLiteStatement.bindNullable(index: Int, value: String?) = if (value == null) bindNull(index) else bindString(index, value)

private fun SQLiteStatement.bindNullable(index: Int, value: Long?) = if (value == null) bindNull(index) else bindLong(index, value)

private fun SQLiteStatement.bindAll(id: Long, seen: Long, oldRating: String?, newRating: String?, oldDate: Long?, newDate: Long?) {
    clearBindings()
    bindLong(1, id)
    bindLong(2, seen)
    bindNullable(3, oldRating)
    bindNullable(4, newRating)
    bindNullable(5, oldDate)
    bindNullable(6, newDate)
}

class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var count = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) count++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }

    override fun skip(n: Long): Long = super.skip(n).also { count += it }
}

/** Minimal open-addressing set of longs (ids touched in one refresh). */
class LongHashSet {
    private var keys = LongArray(64)
    private var used = BooleanArray(64)
    private var size = 0

    operator fun contains(v: Long): Boolean {
        var i = slot(v, keys.size)
        while (used[i]) {
            if (keys[i] == v) return true
            i = (i + 1) and (keys.size - 1)
        }
        return false
    }

    fun add(v: Long) {
        if ((size + 1) * 2 > keys.size) grow()
        var i = slot(v, keys.size)
        while (used[i]) {
            if (keys[i] == v) return
            i = (i + 1) and (keys.size - 1)
        }
        used[i] = true
        keys[i] = v
        size++
    }

    private fun grow() {
        val oldKeys = keys
        val oldUsed = used
        keys = LongArray(oldKeys.size * 2)
        used = BooleanArray(oldKeys.size * 2)
        size = 0
        for (i in oldKeys.indices) if (oldUsed[i]) add(oldKeys[i])
    }

    private fun slot(v: Long, n: Int): Int {
        var h = v * -0x61c8864680b583ebL
        h = h xor (h ushr 32)
        return (h.toInt()) and (n - 1)
    }
}
