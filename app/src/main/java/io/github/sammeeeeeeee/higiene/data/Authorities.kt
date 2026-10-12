package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Council contact details (name, email, website). The CSV has only the council code and name, so
 * the details ship with the app (assets/authorities.json, from the FSA API) and refresh weekly.
 */
class Authorities(private val context: Context, private val db: Db, private val http: OkHttpClient) {

    fun ensureLoaded() {
        val count = db.readableDatabase.rawQuery("SELECT count(*) FROM la", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        if (count > 0) return
        val json = context.assets.open("authorities.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        store(List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            LocalAuthority(o.getInt("code"), o.getString("name"), o.optStringOrNull("email"), o.optStringOrNull("url"))
        })
    }

    /** Fetches the latest list from the FSA API. Safe to call often; does nothing within a week. */
    fun refreshIfStale() {
        val last = db.meta(Db.META_AUTHORITIES_AT)?.toLongOrNull() ?: 0
        if (System.currentTimeMillis() - last < WEEK) return
        val request = Request.Builder()
            .url("https://api.ratings.food.gov.uk/Authorities")
            .header("x-api-version", "2")
            .header("Accept", "application/json")
            .build()
        val list = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Authorities: HTTP ${r.code}")
            val arr = JSONObject(r.body.string()).getJSONArray("authorities")
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                LocalAuthority(
                    code = o.getString("LocalAuthorityIdCode").trim().toInt(),
                    name = o.getString("Name"),
                    email = o.optStringOrNull("Email"),
                    url = o.optStringOrNull("Url"),
                )
            }
        }
        if (list.size > 100) store(list)
        db.setMeta(db.writableDatabase, Db.META_AUTHORITIES_AT, System.currentTimeMillis().toString())
    }

    private fun store(list: List<LocalAuthority>) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.execSQL("DELETE FROM la")
            val st = w.compileStatement("INSERT OR REPLACE INTO la(code, name, email, url) VALUES(?,?,?,?)")
            for (a in list) {
                st.clearBindings()
                st.bindLong(1, a.code.toLong())
                st.bindString(2, a.name)
                if (a.email != null) st.bindString(3, a.email) else st.bindNull(3)
                if (a.url != null) st.bindString(4, a.url) else st.bindNull(4)
                st.executeInsert()
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    companion object {
        private const val WEEK = 7L * 24 * 60 * 60 * 1000

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).trim().ifEmpty { null }
    }
}
