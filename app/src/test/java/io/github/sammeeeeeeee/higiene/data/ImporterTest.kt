package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ImporterTest {
    private lateinit var context: Context
    private lateinit var db: Db
    private lateinit var repo: Repository
    private lateinit var importer: FsaImporter

    private val sample: String by lazy { javaClass.classLoader!!.getResource("fhrs_sample.csv").readText(Charsets.UTF_8) }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(Db.NAME)
        db = Db(context)
        repo = Repository(db) { null }
        importer = FsaImporter(db, okhttp3.OkHttpClient(), { null }, minRows = 10)
    }

    @After
    fun tearDown() = db.close()

    private fun merge(csv: String, checkpoint: (Int) -> Unit = {}): FsaImporter.Outcome.Updated {
        val previous = db.meta(Db.META_ROWS)?.toIntOrNull() ?: 0
        return importer.merge(CsvReader(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))), previous, { false }, checkpoint = checkpoint)
    }

    private fun count(sql: String) = db.readableDatabase.rawQuery(sql, null).use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun firstImportThenDiff() {
        val first = merge(sample)
        val rows = CsvReader(ByteArrayInputStream(sample.toByteArray())).let { r -> var n = -1; while (r.next()) n++; n }
        assertEquals(rows, first.rows)
        assertEquals(rows, first.added)
        assertEquals(rows, count("SELECT count(*) FROM est"))
        assertTrue(count("SELECT count(*) FROM btype") > 3)

        // A known Cambridge row.
        val china = repo.establishment(1472964)!!
        assertEquals("@72.China", china.name)
        assertEquals(Rating.R4, china.rating)
        assertEquals("CB21DP", china.pcKey)
        assertEquals(27, china.laCode)
        assertEquals(LocSource.FSA, china.loc)
        assertEquals(java.time.LocalDate.of(2026, 3, 24), china.ratingDate)
        assertEquals(10, china.structural)

        // Full-text search over names (prefix) and nearby search.
        assertTrue(repo.search("china", null, Filters()).any { it.est.id == 1472964L })
        assertTrue(repo.search("72 chin", null, Filters()).any { it.est.id == 1472964L })
        val centre = Centre(52.2003982, 0.1261394, "x", fromGps = true)
        val nearby = repo.nearby(centre, Filters())
        assertTrue(nearby.listings.isNotEmpty())
        assertEquals(1472964L, nearby.listings.first().est.id)
        val d = nearby.listings.map { it.distanceM!! }
        assertEquals(d.sorted(), d)

        // Unlocated rows sharing a postcode with located ones get an FSA-derived position.
        assertTrue(count("SELECT count(*) FROM est WHERE loc = ${LocSource.POSTCODE_FSA.code}") > 0)
        assertTrue(count("SELECT count(*) FROM est WHERE lat IS NULL") > 0)

        // Filters.
        val fours = repo.nearby(centre, Filters(minRating = 5)).listings
        assertTrue(fours.all { it.est.rating == Rating.R5 || it.est.rating == Rating.PASS || it.est.rating == Rating.PASS_EAT_SAFE })
        val noExempt = repo.nearby(centre, Filters(hideExempt = true, hideAwaiting = true)).listings
        assertTrue(noExempt.none { it.est.rating == Rating.EXEMPT || it.est.rating == Rating.AWAITING_INSPECTION })

        // Welsh and Scottish rows.
        assertTrue(count("SELECT count(*) FROM est WHERE welsh = 1") > 0)
        assertTrue(count("SELECT count(*) FROM est WHERE rating IN ('PASS','IMPROVEMENT_REQUIRED','PASS_EAT_SAFE')") > 0)

        // Second import: one changed, one removed, one added.
        repo.setFavourite(1472964, "@72.China", true)
        val removedId = 1472953L
        val lines = sample.split("\r\n").toMutableList()
        val removedLine = lines.indexOfFirst { it.contains("\"$removedId\"") }
        lines.removeAt(removedLine)
        val chinaLine = lines.indexOfFirst { it.contains("\"1472964\"") }
        lines[chinaLine] = lines[chinaLine].replace("\"fhrs_4_en-GB\",\"4\"", "\"fhrs_2_en-GB\",\"2\"")
        lines.add(lines.size - 1, lines[chinaLine].replace("\"1472964\"", "\"999999999\"").replace("@72.China", "New Place"))
        val second = merge(lines.joinToString("\r\n"))
        assertEquals(1, second.added)
        assertEquals(1, second.changed)
        assertEquals(1, second.removed)
        assertEquals(1, second.favouriteChanges.size)
        assertEquals(Rating.R4, second.favouriteChanges[0].old)
        assertEquals(Rating.R2, second.favouriteChanges[0].new)
        assertNull(repo.establishment(removedId))
        assertEquals(Rating.R2, repo.establishment(1472964)!!.rating)
        assertEquals(1, repo.history(1472964).size)
        assertEquals(1, repo.history(removedId).size)
        // FTS stays in sync: the removed name is gone, the new one is found, the changed one still found.
        assertTrue(repo.search("new place", null, Filters()).any { it.est.id == 999999999L })
        assertTrue(repo.search("china", null, Filters()).any { it.est.id == 1472964L })
        assertEquals(0, count("SELECT count(*) FROM est_fts WHERE est_fts MATCH '1 and 1 rougamo'"))

        // Third import identical to second: nothing changes.
        val third = merge(lines.joinToString("\r\n"))
        assertEquals(0, third.added + third.changed + third.removed)
    }

    @Test
    fun failedVerificationRollsBack() {
        merge(sample)
        val before = count("SELECT count(*) FROM est")
        val broken = sample.replace("@72.China", "Changed")
        try {
            merge(broken) { if (it < 0) throw IOException("MD5 mismatch") }
            fail("expected failure")
        } catch (e: IOException) {
            assertEquals("MD5 mismatch", e.message)
        }
        assertEquals(before, count("SELECT count(*) FROM est"))
        assertEquals("@72.China", repo.establishment(1472964)!!.name)
    }

    @Test
    fun truncatedFileIsRejected() {
        merge(sample)
        val truncated = sample.split("\r\n").take(20).joinToString("\r\n")
        try {
            merge(truncated)
            fail("expected failure")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("incomplete"))
        }
        assertNotNull(repo.establishment(1472964))
        assertFalse(count("SELECT count(*) FROM est") < 200)
    }

    @Test
    fun postcodeQueriesWithoutCodePointUseFsaPositions() {
        merge(sample)
        val c = repo.resolvePlace("CB2 1DP")
        assertNotNull(c)
        assertTrue(Repository.distance(c!!.lat, c.lon, 52.2003, 0.1262) < 200)
        assertNotNull(repo.resolvePlace("CB2"))
        assertNotNull(repo.resolvePlace("CB2 1"))
        assertNull(repo.resolvePlace("ZZ9 9ZZ"))
    }
}
