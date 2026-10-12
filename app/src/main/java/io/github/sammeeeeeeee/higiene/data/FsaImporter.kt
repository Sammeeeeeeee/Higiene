package io.github.sammeeeeeeee.higiene.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Properties
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads the FSA's daily all-UK CSV to a local file (resumable, MD5-checked), then merges it into
 * the database in one transaction.
 *
 * Each row's hash is compared with the stored hash so unchanged rows (normally >99%) are skipped.
 * Nothing touches the database until the whole file is on disk and matches the server's length and
 * Content-MD5, so a cut-off download can never delete or corrupt data, and the write transaction
 * lasts only as long as the local merge.
 */
class FsaImporter(
    private val db: Db,
    private val http: OkHttpClient,
    private val postcodeIndex: () -> PostcodeIndex?,
    /** Where the CSV is downloaded to (app cache). */
    private val workDir: File,
    /** Ids of the user's favourites, to report their rating changes. */
    private val favouriteIds: () -> Set<Long> = { emptySet() },
    /** A real UK file has ~600k rows; anything far smaller is treated as a broken download. */
    private val minRows: Int = 100_000,
    private val url: String = CSV_URL,
) {
    enum class Stage { CHECKING, DOWNLOADING, IMPORTING, LOCATING }

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

    private val part get() = File(workDir, "fsa.csv.part")
    private val partInfo get() = File(workDir, "fsa.csv.part.properties")

    fun refresh(force: Boolean, onProgress: (Progress) -> Unit, isStopped: () -> Boolean): Outcome {
        onProgress(Progress(Stage.CHECKING, 0, 0))
        workDir.mkdirs()
        val previousRows = db.meta(Db.META_ROWS)?.toIntOrNull() ?: 0
        val info = download(force, previousRows, onProgress, isStopped) ?: run {
            db.setMeta(db.writableDatabase, Db.META_CHECKED_AT, System.currentTimeMillis().toString())
            return Outcome.Unchanged
        }
        onProgress(Progress(Stage.IMPORTING, 0, info.length))
        val meta = mapOf(Db.META_ETAG to info.etag, Db.META_LAST_MODIFIED to info.lastModified)
        val outcome = try {
            CountingInputStream(part.inputStream().buffered(1 shl 16)).use { input ->
                merge(CsvReader(input), previousRows, isStopped, meta) { rows ->
                    if (rows < 0) onProgress(Progress(Stage.LOCATING, 0, 0))
                    else onProgress(Progress(Stage.IMPORTING, input.count, info.length))
                }
            }
        } catch (e: CancellationException) {
            throw e // keep the verified file: the next run merges it without downloading again
        } catch (e: Exception) {
            discard() // a file that can't be imported must not be retried forever; fetch afresh next time
            throw e
        }
        discard()
        return outcome
    }

    private fun discard() {
        part.delete()
        partInfo.delete()
    }

    private data class FileInfo(val etag: String?, val lastModified: String?, val md5: String?, val length: Long)

    private fun loadInfo(): FileInfo? = runCatching {
        val p = Properties().apply { partInfo.inputStream().use { load(it) } }
        FileInfo(p.getProperty("etag"), p.getProperty("lastModified"), p.getProperty("md5"), p.getProperty("length").toLong())
    }.getOrNull()

    private fun saveInfo(i: FileInfo) {
        val p = Properties()
        i.etag?.let { p.setProperty("etag", it) }
        i.lastModified?.let { p.setProperty("lastModified", it) }
        i.md5?.let { p.setProperty("md5", it) }
        p.setProperty("length", i.length.toString())
        partInfo.outputStream().use { p.store(it, null) }
    }

    /**
     * Brings [part] up to date with the server. Returns null when the server says the data we
     * already imported is current (304). Resumes a partial file with Range/If-Range.
     */
    private fun download(force: Boolean, previousRows: Int, onProgress: (Progress) -> Unit, isStopped: () -> Boolean, retried: Boolean = false): FileInfo? {
        var info = loadInfo()
        if (info == null || !part.exists()) {
            part.delete()
            info = null
        }
        val have = if (info != null) part.length() else 0L
        if (info != null && have == info.length && info.length > 0) {
            // Finished earlier but the merge didn't complete (stopped or killed): reuse it.
            verifyMd5(info)
            return info
        }
        val importedEtag = db.meta(Db.META_ETAG)
        val request = Request.Builder()
            .url(url)
            // Count raw bytes ourselves; no transparent gzip.
            .header("Accept-Encoding", "identity")
            .apply {
                if (info != null && have > 0 && info.etag != null) {
                    header("Range", "bytes=$have-")
                    // If-Match (not If-Range, which Azure Blob doesn't document): 412 if the file changed.
                    header("If-Match", info.etag)
                } else if (!force && previousRows > 0 && importedEtag != null) {
                    header("If-None-Match", importedEtag)
                }
            }
            .build()

        http.newCall(request).execute().use { response ->
            if (response.code == 304) return null
            val append = response.code == 206
            // The file changed since the partial download, or the range is no longer valid: start again.
            val stalePart = response.code == 412 || response.code == 416 ||
                (append && !resumeMatches(response, have, info!!))
            if (stalePart) {
                discard()
                if (retried) throw IOException("FSA download failed: HTTP ${response.code}")
                response.close()
                return download(force, previousRows, onProgress, isStopped, retried = true)
            }
            if (!append && response.code != 200) throw IOException("FSA download failed: HTTP ${response.code}")
            if (!append) {
                val length = response.body.contentLength()
                info = FileInfo(response.header("ETag"), response.header("Last-Modified"), response.header("Content-MD5")?.trim(), length)
                part.delete()
                saveInfo(info!!)
            }
            val total = info!!.length
            var done = if (append) have else 0L
            val buffer = ByteArray(1 shl 16)
            response.body.byteStream().use { input ->
                FileOutputStream(part, append).use { out ->
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - lastReport > 512 * 1024) {
                            lastReport = done
                            onProgress(Progress(Stage.DOWNLOADING, done, total))
                            // Keep what we have; the next run resumes from here.
                            if (isStopped()) throw CancellationException("Refresh stopped")
                        }
                    }
                }
            }
            onProgress(Progress(Stage.DOWNLOADING, done, total))
            if (total >= 0 && part.length() > total) {
                discard()
                throw IOException("Download larger than expected; starting again next time")
            }
            // Shorter: keep it, the next run resumes from here.
            if (total >= 0 && part.length() != total) throw IOException("Download incomplete: ${part.length()} of $total bytes")
            if (total < 0) {
                info = info!!.copy(length = part.length())
                saveInfo(info!!)
            }
            verifyMd5(info!!)
            return info
        }
    }

    /** A 206 continues our file only if it starts where we stopped and is the same version and size. */
    private fun resumeMatches(response: okhttp3.Response, have: Long, info: FileInfo): Boolean {
        val m = Regex("""^bytes (\d+)-(\d+)/(\d+)$""").find(response.header("Content-Range").orEmpty().trim()) ?: return false
        val start = m.groupValues[1].toLong()
        val total = m.groupValues[3].toLong()
        val etag = response.header("ETag")
        return start == have && (info.length < 0 || total == info.length) && (etag == null || etag == info.etag)
    }

    private fun verifyMd5(info: FileInfo) {
        val expected = info.md5 ?: return
        val md5 = MessageDigest.getInstance("MD5")
        part.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md5.update(buffer, 0, n)
            }
        }
        val actual = Base64.encodeToString(md5.digest(), Base64.NO_WRAP)
        if (actual != expected) {
            part.delete()
            partInfo.delete()
            throw IOException("Download corrupted (MD5 mismatch)")
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
        val favourites = favouriteIds()
        // Take the snapshot inside the transaction so it matches what we modify.
        w.beginTransactionNonExclusive()
        // Existing ids and hashes, sorted by id for binary search.
        val (ids, hashes) = try {
            loadHashes(w)
        } catch (e: Throwable) {
            w.endTransaction()
            throw e
        }
        val seen = BooleanArray(ids.size)
        val firstImport = ids.isEmpty()
        val touched = LongHashSet()
        val types = HashMap<Int, String>()
        val favChanges = ArrayList<FavouriteChange>()
        val now = System.currentTimeMillis()
        var rows = 0
        var added = 0
        var changed = 0
        var removed = 0

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
                // Read everything first: updating rows the open cursor selects could make it skip rows.
                val todo = w.rawQuery("SELECT id, pc FROM est WHERE lat IS NULL AND pc IS NOT NULL", null).use { cur ->
                    buildList { while (cur.moveToNext()) add(cur.getLong(0) to cur.getString(1)) }
                }
                val update = w.compileStatement("UPDATE est SET lat=?, lon=?, loc=${LocSource.POSTCODE_OS.code} WHERE id=?")
                for ((id, pc) in todo) {
                    val ll = index.lookup(pc) ?: continue
                    update.bindLong(1, e6(ll[0]))
                    update.bindLong(2, e6(ll[1]))
                    update.bindLong(3, id)
                    update.executeUpdateDelete()
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
