package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** The download path: full fetch, 304, resume after a cut-off, and a corrupted file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DownloadTest {
    private lateinit var context: Context
    private lateinit var db: Db
    private lateinit var userDb: UserDb
    private lateinit var work: File
    private val requests = mutableListOf<Request>()
    private val bytes: ByteArray by lazy { javaClass.classLoader!!.getResource("fhrs_sample.csv").readBytes() }
    private val md5: String by lazy { Base64.encodeToString(MessageDigest.getInstance("MD5").digest(bytes), Base64.NO_WRAP) }

    /** Next response to give, chosen per request. */
    private var respond: (Request) -> Response = { error("no response set") }

    private fun importer(): FsaImporter {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requests += chain.request()
            respond(chain.request())
        }).build()
        return FsaImporter(db, client, { null }, work, favouriteIds = userDb::favouriteIds, minRows = 10, url = "https://example.test/fsa.csv")
    }

    private fun response(req: Request, code: Int, body: ByteArray, headers: Map<String, String>, failAfter: Int? = null): Response {
        val source: BufferedSource = Buffer().write(body).let { buf ->
            if (failAfter == null) buf else object : ForwardingSource(buf) {
                var read = 0L
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (read >= failAfter) throw IOException("connection reset")
                    val n = super.read(sink, minOf(byteCount, failAfter - read))
                    if (n > 0) read += n
                    return n
                }
            }.buffer()
        }
        val b = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(ResponseBody.create("text/csv".toMediaType(), body.size.toLong(), source))
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    private fun full(req: Request, failAfter: Int? = null) = response(
        req, 200, bytes,
        mapOf("ETag" to "0xABC", "Content-MD5" to md5, "Last-Modified" to "Mon, 12 Oct 2026 01:07:55 GMT"),
        failAfter,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(Db.NAME)
        context.deleteDatabase(UserDb.NAME)
        db = Db(context)
        userDb = UserDb(context)
        work = File(context.cacheDir, "fsa-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        userDb.close()
    }

    private fun refresh() = importer().refresh(force = false, onProgress = {}, isStopped = { false })

    private fun rows() = db.readableDatabase.rawQuery("SELECT count(*) FROM est", null).use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun fullThenNotModified() {
        respond = { full(it) }
        val first = refresh()
        assertTrue(first is FsaImporter.Outcome.Updated)
        assertTrue(rows() > 200)
        assertEquals("0xABC", db.meta(Db.META_ETAG))
        assertFalse(File(work, "fsa.csv.part").exists())

        respond = { req ->
            assertEquals("0xABC", req.header("If-None-Match"))
            response(req, 304, ByteArray(0), emptyMap())
        }
        assertEquals(FsaImporter.Outcome.Unchanged, refresh())
    }

    @Test
    fun resumesAfterCutOff() {
        val cut = bytes.size / 3
        respond = { full(it, failAfter = cut) }
        try {
            refresh()
            fail("expected the cut-off to fail")
        } catch (_: IOException) {
        }
        assertEquals(0, rows())
        assertEquals(cut.toLong(), File(work, "fsa.csv.part").length())

        respond = { req ->
            assertEquals("bytes=$cut-", req.header("Range"))
            assertEquals("0xABC", req.header("If-Match"))
            response(
                req, 206, bytes.copyOfRange(cut, bytes.size),
                mapOf("ETag" to "0xABC", "Content-Range" to "bytes $cut-${bytes.size - 1}/${bytes.size}"),
            )
        }
        assertTrue(refresh() is FsaImporter.Outcome.Updated)
        assertTrue(rows() > 200)
    }

    @Test
    fun restartsWhenFileChangedSincePartialDownload() {
        val cut = bytes.size / 2
        respond = { full(it, failAfter = cut) }
        runCatching { refresh() }
        assertEquals(cut.toLong(), File(work, "fsa.csv.part").length())
        respond = { req ->
            if (req.header("Range") != null) response(req, 412, ByteArray(0), emptyMap()) else full(req)
        }
        assertTrue(refresh() is FsaImporter.Outcome.Updated)
        assertTrue(rows() > 200)
        assertEquals(null, requests.last().header("Range"))
    }

    @Test
    fun resumeWithWrongTotalStartsAgain() {
        val cut = bytes.size / 2
        respond = { full(it, failAfter = cut) }
        runCatching { refresh() }
        respond = { req ->
            if (req.header("Range") != null) {
                // Server ignored the precondition and served a different, bigger file's tail.
                response(req, 206, ByteArray(10), mapOf("ETag" to "0xABC", "Content-Range" to "bytes $cut-${cut + 9}/${bytes.size + 50}"))
            } else {
                full(req)
            }
        }
        assertTrue(refresh() is FsaImporter.Outcome.Updated)
    }

    @Test
    fun unimportableFileIsDiscarded() {
        val broken = "Nonsense,Header\r\n\"a\",\"b\"\r\n".toByteArray()
        respond = { req -> response(req, 200, broken, mapOf("ETag" to "0xBAD")) }
        try {
            refresh()
            fail("expected format error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("format"))
        }
        assertFalse(File(work, "fsa.csv.part").exists())
    }

    @Test
    fun corruptedFileNeverTouchesDatabase() {
        respond = { full(it) }
        refresh()
        val before = rows()
        val changed = String(bytes).replace("@72.China", "@72.Chinx").toByteArray()
        respond = { req ->
            // Same length, but the MD5 header belongs to the original bytes.
            response(req, 200, changed, mapOf("ETag" to "0xDEF", "Content-MD5" to md5))
        }
        try {
            importer().refresh(force = true, onProgress = {}, isStopped = { false })
            fail("expected MD5 failure")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("MD5"))
        }
        assertEquals(before, rows())
        assertEquals("0xABC", db.meta(Db.META_ETAG))
        assertFalse(File(work, "fsa.csv.part").exists())
    }
}
