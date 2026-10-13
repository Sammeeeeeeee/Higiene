package io.github.sammeeeeeeee.higiene.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Services that use the user's own key, with built-in caps kept below each free allowance so a key
 * pasted into Higiene never runs up a bill. Google's free allowances reset on the 1st of each month
 * (US Pacific time) and are shared by every project on the same billing account, hence the margin.
 * https://developers.google.com/maps/billing-and-pricing/pricing
 */
enum class Api(val perDay: Int, val perMonth: Int) {
    /** Text Search (New), Pro fields (id, name, location): 5,000 free a month. Once per business. */
    GOOGLE_SEARCH(perDay = 100, perMonth = 4_500),

    /** Place Details (New), Enterprise fields (rating, hours, phone, website): 1,000 free a month. */
    GOOGLE_DETAILS(perDay = 30, perMonth = 900),

    /** Place Photos (New) images of the business: 1,000 free a month. */
    GOOGLE_PHOTOS(perDay = 30, perMonth = 900),

    /** Street View Static API images: 10,000 free a month. (The metadata check is free.) */
    GOOGLE_STREETVIEW(perDay = 300, perMonth = 9_000),

    /** Mapillary image search: free; a polite cap far below Mapillary's per-app limits. */
    MAPILLARY(perDay = 500, perMonth = 10_000),
}

class ApiBudget(private val userDb: UserDb, private val clock: () -> Long = System::currentTimeMillis) {

    data class Usage(val today: Int, val month: Int)

    /** Records one request and returns true, or returns false if today's or this month's cap is used up. */
    @Synchronized
    fun take(api: Api): Boolean {
        val now = clock()
        val u = usage(api, now)
        if (u.today >= api.perDay || u.month >= api.perMonth) return false
        val w = userDb.writableDatabase
        w.execSQL("INSERT INTO api_calls(api, at) VALUES(?, ?)", arrayOf<Any>(api.name, now))
        w.execSQL("DELETE FROM api_calls WHERE at < ?", arrayOf<Any>(now - 40L * DAY))
        return true
    }

    fun usage(api: Api, now: Long = clock()): Usage {
        val r = userDb.readableDatabase
        fun count(since: Long) = r.rawQuery("SELECT count(*) FROM api_calls WHERE api=? AND at >= ?", arrayOf(api.name, since.toString()))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return Usage(today = count(startOfDay(now)), month = count(startOfMonth(now)))
    }

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
        private val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

        /** Days follow the phone's clock. */
        fun startOfDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

        /** Months follow Google's billing month, which starts at midnight US Pacific time. */
        fun startOfMonth(now: Long): Long {
            val d: LocalDate = Instant.ofEpochMilli(now).atZone(PACIFIC).toLocalDate().withDayOfMonth(1)
            return d.atStartOfDay(PACIFIC).toInstant().toEpochMilli()
        }
    }
}
