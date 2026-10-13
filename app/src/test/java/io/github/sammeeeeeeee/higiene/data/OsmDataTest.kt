package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class OsmDataTest {
    private lateinit var context: Context
    private lateinit var db: Db
    private val http = FakeHttp()
    private lateinit var osm: OsmData

    private fun file(rows: List<String>, header: String = OsmData.HEADER, first: String = "# higiene-osm 1 2026-10-11T01:34:13Z"): ByteArray {
        val text = (listOf(first, header) + rows).joinToString("\n", postfix = "\n")
        return ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
    }

    private val rows = listOf(
        "53\tw54612199\tMo-Fr 08:00-17:00\tpizza;italian_pizza\twww.example.com\t+44 1475 715824\tinfo@example.co.uk\t\tQ123\tyes\tyes\tno\t\tvegan,gluten_free",
        "81\tn400\t\t\tjavascript:alert(1)\t\tx@y.org?bcc=evil@z.org\t\t\t\t\t\t\t",
        "99\tr6\t\t\t\t\t\t\t\t\t\t\t\t",
        "bad\tn1\t\t\t\t\t\t\t\t\t\t\t\t",
        "100\tn2\ttoo\tfew",
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(Db.NAME)
        db = Db(context)
        osm = OsmData(db, http.client, url = "https://example.test/osm.tsv.gz")
        OsmData.MIN_ROWS = 1
    }

    @After
    fun tearDown() {
        db.close()
        OsmData.MIN_ROWS = 10_000
    }

    @Test
    fun importsRowsAndMapsFields() {
        http.handler = { FakeHttp.respond(it, 200, file(rows), "application/gzip", mapOf("ETag" to "\"v1\"")) }
        assertEquals(OsmData.Outcome.Updated(3), osm.refresh())
        assertEquals(3, osm.rows())
        assertEquals("2026-10-11T01:34:13Z", db.meta(Db.META_OSM_DATE))

        val d = osm.details(53)!!
        assertEquals("Mo-Fr 08:00-17:00", d.openingHours)
        assertEquals(listOf("pizza", "italian pizza"), d.cuisine)
        assertEquals("https://www.example.com", d.website)
        assertEquals("+44 1475 715824", d.phone)
        assertEquals("Q123", d.brandQid)
        assertEquals(listOf("vegan", "gluten-free"), d.diets)
        assertEquals("https://www.openstreetmap.org/way/54612199", d.osmUrl)
        assertTrue(d.hasDetails)

        // Only web links are accepted from OSM.
        val unsafe = osm.details(81)!!
        assertNull(unsafe.website)
        assertNull(unsafe.email)
        assertEquals("info@example.co.uk", d.email)
        assertFalse(unsafe.hasDetails)
        assertEquals("https://www.openstreetmap.org/relation/6", osm.details(99)!!.osmUrl)
        assertNull(osm.details(12345))
    }

    @Test
    fun unchangedFileIsSkippedByEtag() {
        http.handler = { FakeHttp.respond(it, 200, file(rows), "application/gzip", mapOf("ETag" to "\"v1\"")) }
        osm.refresh()
        http.handler = { req ->
            assertEquals("\"v1\"", req.header("If-None-Match"))
            FakeHttp.respond(req, 304)
        }
        assertEquals(OsmData.Outcome.Unchanged, osm.refresh())
        assertEquals(3, osm.rows())
    }

    @Test
    fun missingFileLeavesDataAlone() {
        http.handler = { FakeHttp.respond(it, 404) }
        assertEquals(OsmData.Outcome.Missing, osm.refresh())
        assertEquals(0, osm.rows())
    }

    @Test
    fun badFileKeepsThePreviousData() {
        http.handler = { FakeHttp.respond(it, 200, file(rows), "application/gzip") }
        osm.refresh()
        for (bad in listOf(file(rows, header = "fhrs_id\tosm"), file(rows, first = "# higiene-osm 2 x"))) {
            http.handler = { FakeHttp.respond(it, 200, bad, "application/gzip") }
            try {
                osm.refresh()
                fail("expected the file to be refused")
            } catch (_: IOException) {
            }
            assertEquals(3, osm.rows())
            assertEquals("Mo-Fr 08:00-17:00", osm.details(53)?.openingHours)
        }
    }

    @Test
    fun truncatedFileIsRefused() {
        OsmData.MIN_ROWS = 10
        http.handler = { FakeHttp.respond(it, 200, file(rows), "application/gzip") }
        try {
            osm.refresh()
            fail("expected a too-small file to be refused")
        } catch (_: IOException) {
        }
        assertEquals(0, osm.rows())
    }

    @Test
    fun clearRemovesEverything() {
        http.handler = { FakeHttp.respond(it, 200, file(rows), "application/gzip", mapOf("ETag" to "\"v1\"")) }
        osm.refresh()
        osm.clear()
        assertEquals(0, osm.rows())
        assertNull(osm.details(53))
        // With no rows the next refresh downloads in full instead of sending the old ETag.
        http.handler = { req ->
            assertNull(req.header("If-None-Match"))
            FakeHttp.respond(req, 200, file(rows), "application/gzip")
        }
        assertEquals(OsmData.Outcome.Updated(3), osm.refresh())
    }
}

/**
 * Imports a real file built by tools/osm_extract.py. Opt-in:
 * HIGIENE_OSM=/path/osm-fhrs.tsv.gz ./gradlew testDebugUnitTest --tests '*OsmRealFileTest*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class OsmRealFileTest {
    @Test
    fun importsTheRealFile() {
        val f = System.getenv("HIGIENE_OSM")?.let { java.io.File(it) }
        org.junit.Assume.assumeTrue(f != null && f.exists())
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(Db.NAME)
        val db = Db(context)
        val http = FakeHttp { FakeHttp.respond(it, 200, f!!.readBytes(), "application/gzip") }
        val start = System.nanoTime()
        val out = OsmData(db, http.client, url = "https://example.test/osm.tsv.gz").refresh()
        val ms = (System.nanoTime() - start) / 1_000_000
        val rows = (out as OsmData.Outcome.Updated).rows
        println("OSM rows=$rows in ${ms}ms")
        assertTrue(rows > 100_000)
        db.close()
    }
}
