package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * One SQLite database for FSA data and the user's own state (favourites, seen history, cached OSM
 * details). Write-ahead logging lets the UI keep reading while a refresh runs in one transaction.
 */
/**
 * Positions are stored as integer microdegrees (lat/lon × 1e6) to keep the 600k-row table and its
 * index small.
 */
class Db(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE est(
              id INTEGER PRIMARY KEY,
              name TEXT NOT NULL,
              address TEXT,
              pc TEXT,
              site INTEGER,
              type INTEGER NOT NULL,
              la INTEGER NOT NULL,
              rating TEXT NOT NULL,
              rnum INTEGER,
              rdate INTEGER,
              hyg INTEGER,
              str INTEGER,
              cim INTEGER,
              lat INTEGER,
              lon INTEGER,
              loc INTEGER,
              pending INTEGER NOT NULL DEFAULT 0,
              reply TEXT,
              welsh INTEGER NOT NULL DEFAULT 0,
              fhis INTEGER NOT NULL DEFAULT 0,
              h INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX est_latlon ON est(lat, lon)")
        db.execSQL("CREATE INDEX est_pc ON est(pc)")
        db.execSQL("CREATE INDEX est_site ON est(site)")
        // Only businesses without a position are ever looked up by council.
        db.execSQL("CREATE INDEX est_la_unlocated ON est(la) WHERE lat IS NULL")
        // External-content FTS4 over name/address/postcode. Kept in sync by the importer.
        db.execSQL("CREATE VIRTUAL TABLE est_fts USING fts4(content=\"est\", name, address, pc, tokenize=unicode61)")
        db.execSQL("CREATE TABLE btype(id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE la(code INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT, url TEXT)")
        db.execSQL("CREATE TABLE fav(id INTEGER PRIMARY KEY, name TEXT NOT NULL, added INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE history(id INTEGER NOT NULL, seen INTEGER NOT NULL, old_rating TEXT, new_rating TEXT, old_date INTEGER, new_date INTEGER)",
        )
        db.execSQL("CREATE INDEX history_id ON history(id)")
        db.execSQL("CREATE TABLE osm(id INTEGER PRIMARY KEY, fetched INTEGER NOT NULL, json TEXT)")
        db.execSQL("CREATE TABLE area(code TEXT PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL) WITHOUT ROWID")
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT) WITHOUT ROWID")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // First schema; future migrations go here.
    }

    fun meta(key: String): String? =
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    fun setMeta(db: SQLiteDatabase, key: String, value: String?) {
        if (value == null) db.execSQL("DELETE FROM meta WHERE k=?", arrayOf(key))
        else db.execSQL("INSERT OR REPLACE INTO meta(k, v) VALUES(?, ?)", arrayOf(key, value))
    }

    companion object {
        const val NAME = "higiene.db"
        const val VERSION = 1

        const val META_ETAG = "csv_etag"
        const val META_LAST_MODIFIED = "csv_last_modified"
        const val META_IMPORTED_AT = "csv_imported_at"
        const val META_CHECKED_AT = "csv_checked_at"
        const val META_ROWS = "csv_rows"
        const val META_CODEPOINT_MD5 = "codepoint_md5"
        const val META_CODEPOINT_AT = "codepoint_checked_at"
        const val META_CODEPOINT_YEAR = "codepoint_year"
        const val META_AUTHORITIES_AT = "authorities_at"
    }
}
