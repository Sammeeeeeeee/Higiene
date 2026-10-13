package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Databases left by earlier builds must open in this one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun tables(db: SQLiteDatabase) = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
        buildSet { while (c.moveToNext()) add(c.getString(0)) }
    }

    @Test
    fun ratingsDbFromATestBuildWithTheOldOsmCache() {
        context.deleteDatabase(Db.NAME)
        // A version 1 higiene.db as written by builds before v0.1: OSM cache and favourites inside it.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(Db.NAME), null).use { old ->
            old.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT) WITHOUT ROWID")
            old.execSQL("CREATE TABLE osm(id INTEGER PRIMARY KEY, fetched INTEGER NOT NULL, json TEXT)")
            old.execSQL("CREATE TABLE fav(id INTEGER PRIMARY KEY, name TEXT NOT NULL, added INTEGER NOT NULL)")
            old.execSQL("INSERT INTO osm VALUES(1, 0, '{}')")
            old.version = 1
        }
        val db = Db(context)
        val w = db.writableDatabase
        assertEquals(Db.VERSION, w.version)
        assertFalse("fav" in tables(w))
        // The new table has the new columns and is empty.
        w.execSQL("INSERT INTO osm(id, el) VALUES(5, 'n1')")
        assertEquals(1, w.rawQuery("SELECT count(*) FROM osm", null).use { it.moveToFirst(); it.getInt(0) })
        db.close()
    }

    @Test
    fun userDbFromV01() {
        context.deleteDatabase(UserDb.NAME)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(UserDb.NAME), null).use { old ->
            old.execSQL("CREATE TABLE fav(id INTEGER PRIMARY KEY, name TEXT NOT NULL, added INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE osm(id INTEGER PRIMARY KEY, fetched INTEGER NOT NULL, json TEXT)")
            old.execSQL("CREATE TABLE osm_calls(at INTEGER NOT NULL)")
            old.execSQL("INSERT INTO fav VALUES(42, 'Bagels Bar', 0)")
            old.version = 1
        }
        val u = UserDb(context)
        // Favourites survive; the Overpass cache is gone; the new tables work.
        assertEquals(setOf(42L), u.favouriteIds())
        val t = tables(u.readableDatabase)
        assertFalse("osm" in t || "osm_calls" in t)
        ApiBudget(u).take(Api.MAPILLARY)
        u.close()
    }
}
