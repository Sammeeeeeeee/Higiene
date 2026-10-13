package io.github.sammeeeeeeee.higiene.data

import android.database.Cursor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** All reads (and the user's own writes) against the local database. Call from a background thread. */
class Repository(private val db: Db, private val userDb: UserDb, private val postcodeIndex: () -> PostcodeIndex?) {

    private val _version = MutableStateFlow(0L)

    /** Bumped whenever data or favourites change, so screens can reload. */
    val version: StateFlow<Long> = _version

    fun notifyChanged() {
        _version.value = _version.value + 1
    }

    data class DataInfo(
        val rows: Int,
        val importedAt: Long?,
        val checkedAt: Long?,
        val lastModified: String?,
        val hasPostcodes: Boolean,
        val postcodesAt: Long?,
    )

    fun dataInfo() = DataInfo(
        rows = db.meta(Db.META_ROWS)?.toIntOrNull() ?: 0,
        importedAt = db.meta(Db.META_IMPORTED_AT)?.toLongOrNull(),
        checkedAt = db.meta(Db.META_CHECKED_AT)?.toLongOrNull(),
        lastModified = db.meta(Db.META_LAST_MODIFIED),
        hasPostcodes = postcodeIndex() != null,
        postcodesAt = db.meta(Db.META_CODEPOINT_AT)?.toLongOrNull(),
    )

    fun hasData() = (db.meta(Db.META_ROWS)?.toIntOrNull() ?: 0) > 0

    fun types(): List<BusinessType> = db.readableDatabase.rawQuery("SELECT id, name FROM btype ORDER BY name", null).use { c ->
        buildList { while (c.moveToNext()) add(BusinessType(c.getInt(0), c.getString(1))) }
    }

    data class Nearby(val listings: List<Listing>, val radiusM: Double, val unlocated: Unlocated?)

    /** Businesses in the same council with no published position (often home caterers and vans). */
    data class Unlocated(val la: LocalAuthority, val count: Int)

    fun nearby(centre: Centre, filters: Filters, limit: Int = 400): Nearby {
        var radius = 600.0
        var found: List<Listing>
        while (true) {
            val dLat = radius / 111_320.0
            val dLon = radius / (111_320.0 * cos(Math.toRadians(centre.lat)))
            val (where, args) = filterSql(filters)
            val sql = "$SELECT_EST WHERE e.lat BETWEEN ? AND ? AND e.lon BETWEEN ? AND ?$where"
            val all = arrayOf(
                FsaImporter.e6(centre.lat - dLat).toString(), FsaImporter.e6(centre.lat + dLat).toString(),
                FsaImporter.e6(centre.lon - dLon).toString(), FsaImporter.e6(centre.lon + dLon).toString(),
            ) + args
            found = db.readableDatabase.rawQuery(sql, all).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val e = c.toEstablishment()
                        val d = distance(centre.lat, centre.lon, e.lat!!, e.lon!!)
                        if (d <= radius) add(Listing(e, d))
                    }
                }
            }
            if (found.size >= MIN_NEARBY || radius >= MAX_RADIUS) break
            radius = (radius * 2).coerceAtMost(MAX_RADIUS)
        }
        val sorted = sort(found, filters.sort).take(limit)
        val nearestLa = found.minByOrNull { it.distanceM ?: Double.MAX_VALUE }?.est?.laCode
        val unlocated = nearestLa?.let { la ->
            val count = db.readableDatabase.rawQuery("SELECT count(*) FROM est WHERE la=? AND lat IS NULL", arrayOf(la.toString()))
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (count > 0) authority(la)?.let { Unlocated(it, count) } else null
        }
        return Nearby(sorted, radius, unlocated)
    }

    /**
     * Text search over names, addresses and postcodes. Every match is considered (filters applied in
     * SQL), then the best [limit] are kept: name matches first, then by the chosen sort.
     */
    fun search(query: String, centre: Centre?, filters: Filters, limit: Int = 300): List<Listing> {
        // Lower-case so words like "and"/"or" are never read as FTS operators.
        val tokens = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        // Prefix-match words of 3+ letters; shorter ones must match whole words (keeps "ab*" cheap).
        fun term(t: String) = if (t.length >= 3) "$t*" else t
        val all = tokens.joinToString(" ") { term(it) }
        val nameOnly = tokens.joinToString(" ") { "name:" + term(it) }
        val (where, args) = filterSql(filters)
        val r = db.readableDatabase
        val fts = "JOIN (SELECT docid FROM est_fts WHERE est_fts MATCH ?) f ON e.id = f.docid"

        if (centre == null || filters.sort != SortOrder.DISTANCE) {
            // Order in SQL: name matches first, then the rest, each capped after filtering.
            val order = when (filters.sort) {
                // Same order as rank(): Scottish Pass/Pass and Eat Safe sit among the 0-5 ratings.
                SortOrder.RATING -> "CASE e.rating WHEN '5' THEN 10 WHEN 'PASS_EAT_SAFE' THEN 9 WHEN '4' THEN 8 WHEN 'PASS' THEN 7 " +
                    "WHEN '3' THEN 6 WHEN '2' THEN 4 WHEN 'IMPROVEMENT_REQUIRED' THEN 2.5 WHEN '1' THEN 2 WHEN '0' THEN 0 ELSE -1 END DESC, e.name"
                SortOrder.RECENT -> "e.rdate IS NULL, e.rdate DESC, e.name"
                else -> "e.name COLLATE NOCASE"
            }
            fun page(match: String, n: Int, exclude: Set<Long>) =
                r.rawQuery("$SELECT_EST $fts WHERE 1=1$where ORDER BY $order LIMIT ${n + exclude.size}", arrayOf(match) + args).use { c ->
                    buildList {
                        while (c.moveToNext() && size < n) {
                            val e = c.toEstablishment()
                            if (e.id in exclude) continue
                            add(Listing(e, if (centre != null && e.lat != null && e.lon != null) distance(centre.lat, centre.lon, e.lat, e.lon) else null))
                        }
                    }
                }
            val byName = page(nameOnly, limit, emptySet())
            if (byName.size >= limit) return byName
            return byName + page(all, limit - byName.size, byName.mapTo(HashSet()) { it.est.id })
        }

        // Nearest first: rank every match by (name match, distance) with a bounded heap.
        val nameIds = LongHashSet()
        r.rawQuery("SELECT docid FROM est_fts WHERE est_fts MATCH ?", arrayOf(nameOnly)).use { c -> while (c.moveToNext()) nameIds.add(c.getLong(0)) }
        val heap = java.util.PriorityQueue<Pair<Double, Long>>(limit + 1, compareByDescending { it.first })
        r.rawQuery("SELECT e.id, e.lat, e.lon FROM est e $fts WHERE 1=1$where", arrayOf(all) + args).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val d = if (c.isNull(1)) UNLOCATED else distance(centre.lat, centre.lon, c.getLong(1) / 1e6, c.getLong(2) / 1e6)
                val score = (if (id in nameIds) 0.0 else NOT_NAME) + d
                if (heap.size < limit) heap.add(score to id)
                else if (score < heap.peek()!!.first) {
                    heap.poll()
                    heap.add(score to id)
                }
            }
        }
        if (heap.isEmpty()) return emptyList()
        val ranked = heap.sortedBy { it.first }.map { it.second }
        val byId = HashMap<Long, Establishment>(ranked.size * 2)
        ranked.chunked(500).forEach { chunk ->
            r.rawQuery("$SELECT_EST WHERE e.id IN (${chunk.joinToString(",")})", null).use { c ->
                while (c.moveToNext()) c.toEstablishment().let { byId[it.id] = it }
            }
        }
        return ranked.mapNotNull { id ->
            byId[id]?.let { e -> Listing(e, if (e.lat != null && e.lon != null) distance(centre.lat, centre.lon, e.lat, e.lon) else null) }
        }
    }

    /** Resolves a full postcode, sector ("SW1A 1") or outcode ("SW1A") to a point, fully offline. */
    fun resolvePlace(raw: String): Centre? {
        val q = Postcodes.parse(raw) ?: return null
        val label = if (q is Postcodes.Query.Full) Postcodes.format(q.key) else q.key
        if (q is Postcodes.Query.Full) {
            postcodeIndex()?.lookup(q.key)?.let { return Centre(it[0], it[1], label, fromGps = false) }
            fsaAverage("e.pc = ?", arrayOf(q.key))?.let { return Centre(it[0], it[1], label, fromGps = false, approximate = true) }
            // Unknown full postcode: fall back to its sector.
            return areaCentre(Postcodes.sectorOf(q.key))?.let { Centre(it[0], it[1], label, fromGps = false, approximate = true) }
        }
        return areaCentre(q.key)?.let { Centre(it[0], it[1], label, fromGps = false, approximate = true) }
    }

    /** Centre of an outcode ("E16") or sector ("E1 6"). */
    private fun areaCentre(code: String): DoubleArray? {
        db.readableDatabase.rawQuery("SELECT lat, lon FROM area WHERE code=?", arrayOf(code)).use {
            if (it.moveToFirst()) return doubleArrayOf(it.getDouble(0), it.getDouble(1))
        }
        // Not in Code-Point (Northern Ireland, or postcodes not downloaded yet): average FSA positions.
        // Full keys are the outcode plus 3 characters, so the length separates "E1 6" from "E16".
        val outcode = code.substringBefore(' ')
        val prefix = code.replace(" ", "")
        // length() has no column affinity, so the number is inlined rather than bound as text.
        return fsaAverage("e.pc >= ? AND e.pc < ? AND length(e.pc) = ${outcode.length + 3}", arrayOf(prefix, prefix + "\uFFFF"))
    }

    private fun fsaAverage(where: String, args: Array<String>): DoubleArray? =
        db.readableDatabase.rawQuery("SELECT avg(e.lat), avg(e.lon), count(*) FROM est e WHERE $where AND e.loc = ${LocSource.FSA.code}", args).use {
            if (it.moveToFirst() && it.getInt(2) > 0) doubleArrayOf(it.getDouble(0) / 1e6, it.getDouble(1) / 1e6) else null
        }

    fun establishment(id: Long): Establishment? =
        db.readableDatabase.rawQuery("$SELECT_EST WHERE e.id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.toEstablishment() else null }

    /** Every business registered at the same first address line and postcode. */
    fun sameSite(est: Establishment): List<Establishment> {
        val site = est.siteKey ?: return emptyList()
        return db.readableDatabase.rawQuery("$SELECT_EST WHERE e.site = ? AND e.id != ? ORDER BY e.name", arrayOf(site.toString(), est.id.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(c.toEstablishment()) } }
    }

    /** Other businesses at the same postcode but a different first address line. */
    fun samePostcode(est: Establishment, limit: Int = 100): List<Establishment> {
        val pc = est.pcKey ?: return emptyList()
        return db.readableDatabase.rawQuery(
            "$SELECT_EST WHERE e.pc = ? AND e.id != ? AND (e.site IS NULL OR e.site != ?) ORDER BY e.name LIMIT $limit",
            arrayOf(pc, est.id.toString(), (est.siteKey ?: 0L).toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toEstablishment()) } }
    }

    /** "Who's really cooking?" for a typed address: everything at a postcode, grouped by first address line. */
    fun atPostcode(pcKey: String): List<Establishment> =
        db.readableDatabase.rawQuery("$SELECT_EST WHERE e.pc = ? ORDER BY e.site, e.name", arrayOf(pcKey))
            .use { c -> buildList { while (c.moveToNext()) add(c.toEstablishment()) } }

    fun unlocated(la: Int, filters: Filters): List<Establishment> {
        val (where, args) = filterSql(filters)
        return db.readableDatabase.rawQuery("$SELECT_EST WHERE e.la = ? AND e.lat IS NULL$where ORDER BY e.name", arrayOf(la.toString()) + args)
            .use { c -> buildList { while (c.moveToNext()) add(c.toEstablishment()) } }
    }

    // Favourites --------------------------------------------------------------------------------

    data class Favourite(val id: Long, val name: String, val listing: Listing?)

    fun favourites(centre: Centre?): List<Favourite> {
        val favs = userDb.readableDatabase.rawQuery("SELECT id, name FROM fav ORDER BY added DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
        }
        return favs.map { (id, name) ->
            val e = establishment(id)
            Favourite(id, e?.name ?: name, e?.let { Listing(it, if (centre != null && it.lat != null && it.lon != null) distance(centre.lat, centre.lon, it.lat, it.lon) else null) })
        }
    }

    fun isFavourite(id: Long): Boolean =
        userDb.readableDatabase.rawQuery("SELECT 1 FROM fav WHERE id=?", arrayOf(id.toString())).use { it.moveToFirst() }

    /** Stored name of a favourite, which outlives the business's FSA record. */
    fun favouriteName(id: Long): String? =
        userDb.readableDatabase.rawQuery("SELECT name FROM fav WHERE id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else null }

    fun setFavourite(id: Long, name: String, on: Boolean) {
        val w = userDb.writableDatabase
        if (on) w.execSQL("INSERT OR REPLACE INTO fav(id, name, added) VALUES(?,?,?)", arrayOf<Any>(id, name, System.currentTimeMillis()))
        else w.execSQL("DELETE FROM fav WHERE id=?", arrayOf(id))
        notifyChanged()
    }

    fun history(id: Long): List<HistoryEntry> =
        db.readableDatabase.rawQuery("SELECT seen, old_rating, new_rating, old_date, new_date FROM history WHERE id=? ORDER BY seen DESC", arrayOf(id.toString()))
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            HistoryEntry(
                                seenAtMillis = c.getLong(0),
                                oldRating = c.getStringOrNull(1)?.let { Rating.fromCode(it) },
                                newRating = c.getStringOrNull(2)?.let { Rating.fromCode(it) },
                                oldDate = c.getLongOrNull(3)?.let { LocalDate.ofEpochDay(it) },
                                newDate = c.getLongOrNull(4)?.let { LocalDate.ofEpochDay(it) },
                            ),
                        )
                    }
                }
            }

    fun authority(code: Int): LocalAuthority? =
        db.readableDatabase.rawQuery("SELECT code, name, email, url FROM la WHERE code=?", arrayOf(code.toString())).use {
            if (it.moveToFirst()) LocalAuthority(it.getInt(0), it.getString(1), it.getStringOrNull(2), it.getStringOrNull(3)) else null
        }

    // Helpers ------------------------------------------------------------------------------------

    private fun filterSql(f: Filters): Pair<String, Array<String>> {
        val sb = StringBuilder()
        val args = ArrayList<String>()
        if (f.typeIds.isNotEmpty()) sb.append(" AND e.type IN (").append(f.typeIds.joinToString(",")).append(")")
        if (f.minRating != null) {
            sb.append(" AND (e.rnum >= ? OR e.rating IN ('${Rating.PASS.code}','${Rating.PASS_EAT_SAFE.code}'))")
            args += f.minRating.toString()
        }
        if (f.hideExempt) sb.append(" AND e.rating != '${Rating.EXEMPT.code}'")
        if (f.hideAwaiting) sb.append(" AND e.rating NOT IN ('${Rating.AWAITING_INSPECTION.code}','${Rating.AWAITING_PUBLICATION.code}')")
        if (f.inspectedWithinMonths != null) {
            sb.append(" AND e.rdate >= ?")
            args += LocalDate.now().minusMonths(f.inspectedWithinMonths.toLong()).toEpochDay().toString()
        }
        return sb.toString() to args.toTypedArray()
    }

    private fun sort(list: List<Listing>, order: SortOrder): List<Listing> = when (order) {
        SortOrder.DISTANCE -> list.sortedBy { it.distanceM ?: Double.MAX_VALUE }
        SortOrder.RATING -> list.sortedWith(compareByDescending<Listing> { rank(it.est.rating) }.thenBy { it.distanceM ?: Double.MAX_VALUE })
        SortOrder.RECENT -> list.sortedWith(compareByDescending<Listing> { it.est.ratingDate?.toEpochDay() ?: Long.MIN_VALUE }.thenBy { it.distanceM ?: Double.MAX_VALUE })
        SortOrder.NAME -> list.sortedBy { it.est.name.lowercase() }
    }

    companion object {
        private const val MIN_NEARBY = 60
        private const val UNLOCATED = 5_000_000.0
        private const val NOT_NAME = 10_000_000.0
        private const val MAX_RADIUS = 64_000.0

        private const val SELECT_EST =
            "SELECT e.id, e.name, e.address, e.pc, e.type, b.name, e.la, e.rating, e.rdate, " +
                "e.hyg, e.str, e.cim, e.lat, e.lon, e.loc, e.pending, e.reply, e.welsh, e.fhis, e.site " +
                "FROM est e LEFT JOIN btype b ON b.id = e.type"

        /** Sort rank for mixed FHRS/FHIS lists: higher is better. */
        fun rank(r: Rating): Double = when (r) {
            Rating.PASS_EAT_SAFE -> 9.0
            Rating.PASS -> 7.0
            Rating.IMPROVEMENT_REQUIRED -> 2.5
            Rating.EXEMPT, Rating.AWAITING_INSPECTION, Rating.AWAITING_PUBLICATION, Rating.UNKNOWN -> -1.0
            else -> r.numeric!! * 2.0
        }

        /** Great-circle distance in metres. */
        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * 6_371_008.8 * asin(sqrt(max(0.0, a)).coerceAtMost(1.0))
        }

        private fun Cursor.getLongOrNull(i: Int) = if (isNull(i)) null else getLong(i)
        private fun Cursor.getIntOrNull(i: Int) = if (isNull(i)) null else getInt(i)
        private fun Cursor.getE6OrNull(i: Int) = if (isNull(i)) null else getLong(i) / 1e6

        private fun Cursor.toEstablishment() = Establishment(
            id = getLong(0),
            name = getString(1),
            address = getStringOrNull(2),
            pcKey = getStringOrNull(3),
            typeId = getInt(4),
            typeName = getStringOrNull(5) ?: "",
            laCode = getInt(6),
            rating = Rating.fromCode(getString(7)),
            ratingDate = getLongOrNull(8)?.let { LocalDate.ofEpochDay(it) },
            hygiene = getIntOrNull(9),
            structural = getIntOrNull(10),
            confidence = getIntOrNull(11),
            lat = getE6OrNull(12),
            lon = getE6OrNull(13),
            loc = LocSource.from(getIntOrNull(14)),
            newRatingPending = getInt(15) != 0,
            reply = getStringOrNull(16),
            welsh = getInt(17) != 0,
            fhis = getInt(18) != 0,
            siteKey = getLongOrNull(19),
        )
    }
}
