package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Optional end-to-end run against the real files. Skipped unless both paths are given, e.g.
 * HIGIENE_CSV=/path/FHRS_All_en-GB.csv HIGIENE_CODEPOINT=/path/codepo_gb.zip ./gradlew testDebugUnitTest --tests '*FullDataTest*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FullDataTest {
    @Test
    fun fullImport() {
        val csv = System.getenv("HIGIENE_CSV")?.let(::File)
        val zip = System.getenv("HIGIENE_CODEPOINT")?.let(::File)
        assumeTrue(csv != null && csv.exists() && zip != null && zip.exists())
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(Db.NAME)
        context.deleteDatabase(UserDb.NAME)
        val db = Db(context)
        val userDb = UserDb(context)
        val indexFile = File(context.filesDir, "postcodes.bin")
        var index: PostcodeIndex? = null
        val repo = Repository(db, userDb) { index }
        val importer = FsaImporter(db, okhttp3.OkHttpClient(), { index }, context.cacheDir, favouriteIds = userDb::favouriteIds)

        fun count(sql: String) = db.readableDatabase.rawQuery(sql, null).use { it.moveToFirst(); it.getLong(0) }
        fun t(label: String, block: () -> Unit) {
            val start = System.nanoTime(); block(); println("$label: ${(System.nanoTime() - start) / 1_000_000} ms")
        }

        t("first import") { csv!!.inputStream().buffered(1 shl 16).use { importer.merge(CsvReader(it), 0, { false }) {} } }
        println("rows=${count("SELECT count(*) FROM est")} located=${count("SELECT count(*) FROM est WHERE lat IS NOT NULL")} fsaDerived=${count("SELECT count(*) FROM est WHERE loc=2")}")
        t("codepoint") { zip!!.inputStream().buffered(1 shl 16).use { CodePointImporter(db, okhttp3.OkHttpClient(), indexFile).importZip(it, null) { false } } }
        index = PostcodeIndex.open(indexFile)
        println("postcodes=${index!!.count} areas=${count("SELECT count(*) FROM area")}")
        println("by loc: fsa=${count("SELECT count(*) FROM est WHERE loc=0")} os=${count("SELECT count(*) FROM est WHERE loc=1")} fsaDerived=${count("SELECT count(*) FROM est WHERE loc=2")} none=${count("SELECT count(*) FROM est WHERE lat IS NULL")}")
        println("restaurants without position: ${count("SELECT count(*) FROM est WHERE type=1 AND lat IS NULL")} of ${count("SELECT count(*) FROM est WHERE type=1")}")
        t("second import (unchanged)") {
            val r = csv!!.inputStream().buffered(1 shl 16).use { importer.merge(CsvReader(it), count("SELECT count(*) FROM est").toInt(), { false }) {} }
            println("added=${r.added} changed=${r.changed} removed=${r.removed}")
            assertTrue(r.added + r.changed + r.removed == 0)
        }
        val soho = Centre(51.5136, -0.1365, "Soho", fromGps = true)
        t("nearby soho") { println("nearby=${repo.nearby(soho, Filters()).listings.size}") }
        t("nearby soho 5 only") { println("nearby5=${repo.nearby(soho, Filters(minRating = 5)).listings.size}") }
        val rural = Centre(57.0, -4.5, "Highlands", fromGps = true)
        t("nearby highlands") { val n = repo.nearby(rural, Filters()); println("rural=${n.listings.size} radius=${n.radiusM}") }
        t("search greggs") { println("greggs=${repo.search("greggs", soho, Filters()).size}") }
        t("search pizza") {
            val r = repo.search("pizza", soho, Filters())
            println("pizza=${r.size} nearest=${r.first().distanceM?.toInt()} m, 20th=${r.getOrNull(19)?.distanceM?.toInt()} m")
            // The nearest results must really be near (the old capped search returned matches 18 km away).
            assertTrue(r.first().distanceM!! < 500)
        }
        t("search th (broad)") { println("th=${repo.search("th", soho, Filters()).size}") }
        t("search cafe 5 only") { println("cafe5=${repo.search("cafe", soho, Filters(minRating = 5)).size}") }
        t("postcode") { println("SW1A 1AA -> ${repo.resolvePlace("SW1A 1AA")}; BT1 1AA -> ${repo.resolvePlace("BT1 1AA")}; BT1 -> ${repo.resolvePlace("BT1")}; E1 6 -> ${repo.resolvePlace("E1 6")}; E16 -> ${repo.resolvePlace("E16")}") }
        // Whitechapel (E1 6) and Canning Town (E16) are ~6 km apart.
        val e1 = repo.resolvePlace("E1 6")!!
        val e16 = repo.resolvePlace("E16")!!
        assertTrue(Repository.distance(e1.lat, e1.lon, 51.517, -0.072) < 1500)
        assertTrue(Repository.distance(e16.lat, e16.lon, 51.510, 0.022) < 2500)
        println("db size MB: ${context.getDatabasePath(Db.NAME).length() / 1_000_000} postcodes.bin MB: ${indexFile.length() / 1_000_000}")
        t("vacuum") { db.writableDatabase.execSQL("VACUUM") }
        println("db size after vacuum MB: ${context.getDatabasePath(Db.NAME).length() / 1_000_000}")
        System.getenv("HIGIENE_DB_COPY")?.let { out ->
            db.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            db.close()
            context.getDatabasePath(Db.NAME).copyTo(File(out), overwrite = true)
        }
    }
}
