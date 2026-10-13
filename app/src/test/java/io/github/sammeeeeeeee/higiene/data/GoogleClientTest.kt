package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.Request
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class GoogleClientTest {
    private lateinit var userDb: UserDb
    private lateinit var budget: ApiBudget
    private val http = FakeHttp()
    private lateinit var google: GoogleClient
    private val identity = AppIdentity("io.github.sammeeeeeeee.higiene", "60B395A5")

    private val est = Establishment(
        id = 1472964, name = "@72.China", address = "72 Regent Street, Cambridge", pcKey = "CB21DP", typeId = 1, typeName = "Restaurant/Cafe/Canteen",
        laCode = 1, rating = Rating.R4, ratingDate = null, hygiene = null, structural = null, confidence = null,
        lat = 52.2003982, lon = 0.1261394, loc = LocSource.FSA, newRatingPending = false, reply = null, welsh = false, fhis = false, siteKey = null,
    )

    private val search = """{"places":[
        {"id":"far","displayName":{"text":"72 China"},"location":{"latitude":52.21,"longitude":0.13}},
        {"id":"other","displayName":{"text":"Regent Street Barber"},"location":{"latitude":52.20041,"longitude":0.12615}},
        {"id":"good","displayName":{"text":"72 China Restaurant"},"location":{"latitude":52.20045,"longitude":0.12618}}
    ]}"""

    private val details = """{"id":"good","businessStatus":"OPERATIONAL","googleMapsUri":"https://maps.google.com/?cid=1",
        "rating":4.4,"userRatingCount":1234,
        "currentOpeningHours":{"openNow":true},
        "regularOpeningHours":{"weekdayDescriptions":["Monday: 12:00 – 22:00","Tuesday: Closed"]},
        "nationalPhoneNumber":"01223 000000","websiteUri":"https://72china.example"}"""

    private fun handler(req: Request) = when {
        req.url.encodedPath.endsWith("places:searchText") -> FakeHttp.json(req, 200, search)
        req.url.encodedPath.endsWith("/places/good") -> FakeHttp.json(req, 200, details)
        else -> error("unexpected ${req.url}")
    }

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(UserDb.NAME)
        userDb = UserDb(context)
        budget = ApiBudget(userDb)
        google = GoogleClient(http.client, userDb, budget, identity, placesBase = "https://places.test/v1", mapsBase = "https://maps.test/api")
        http.handler = ::handler
    }

    @After
    fun tearDown() = userDb.close()

    @Test
    fun findsTheMatchingPlaceThenItsDetails() {
        val p = google.place(est, "KEY")!!
        assertEquals("good", p.placeId)
        assertEquals(4.4, p.rating!!, 0.0)
        assertEquals(1234, p.ratingCount)
        assertEquals(true, p.openNow)
        assertEquals(listOf("Monday: 12:00 – 22:00", "Tuesday: Closed"), p.hours)
        assertEquals("01223 000000", p.phone)
        assertEquals("https://72china.example", p.website)

        val (s, d) = http.requests
        assertEquals("KEY", s.header("X-Goog-Api-Key"))
        assertEquals(GoogleClient.SEARCH_FIELDS, s.header("X-Goog-FieldMask"))
        assertEquals("io.github.sammeeeeeeee.higiene", s.header("X-Android-Package"))
        assertEquals("60B395A5", s.header("X-Android-Cert"))
        val body = JSONObject(FakeHttp.bodyOf(s))
        assertEquals("@72.China, CB2 1DP", body.getString("textQuery"))
        assertEquals("gb", body.getString("regionCode"))
        assertEquals(GoogleClient.DETAIL_FIELDS, d.header("X-Goog-FieldMask"))
        assertEquals(1, budget.usage(Api.GOOGLE_SEARCH).today)
        assertEquals(1, budget.usage(Api.GOOGLE_DETAILS).today)
    }

    @Test
    fun keepsThePlaceIdSoLaterViewsSkipTheSearch() {
        google.place(est, "KEY")
        http.requests.clear()
        assertNotNull(google.place(est, "KEY"))
        assertEquals(1, http.requests.size)
        assertTrue(http.requests[0].url.encodedPath.endsWith("/places/good"))
    }

    @Test
    fun noConfidentMatchMeansNoPaidCall() {
        http.handler = { req ->
            if (req.url.encodedPath.endsWith("places:searchText")) {
                FakeHttp.json(req, 200, """{"places":[{"id":"x","displayName":{"text":"Somewhere Else Entirely"},"location":{"latitude":52.2004,"longitude":0.1262}}]}""")
            } else error("details must not be fetched")
        }
        assertNull(google.place(est, "KEY"))
        // The failed match is remembered for a month: no new search either.
        http.requests.clear()
        assertNull(google.place(est, "KEY"))
        assertTrue(http.requests.isEmpty())
        assertEquals(0, budget.usage(Api.GOOGLE_DETAILS).today)
    }

    @Test
    fun anExpiredPlaceIdIsForgotten() {
        google.place(est, "KEY")
        http.handler = { req ->
            if (req.url.encodedPath.endsWith("/places/good")) FakeHttp.json(req, 404, """{"error":{"code":404}}""") else handler(req)
        }
        assertNull(google.place(est, "KEY"))
        http.handler = ::handler
        http.requests.clear()
        assertNotNull(google.place(est, "KEY"))
        assertTrue(http.requests[0].url.encodedPath.endsWith("places:searchText"))
    }

    @Test
    fun capReachedMeansNoRequest() {
        repeat(Api.GOOGLE_DETAILS.perDay) { budget.take(Api.GOOGLE_DETAILS) }
        assertNull(google.place(est, "KEY"))
        assertTrue(http.requests.none { it.url.encodedPath.contains("/places/") && !it.url.encodedPath.endsWith("searchText") })
    }

    @Test
    fun streetPhotoAimsAtTheBusiness() {
        http.handler = { req ->
            assertTrue(req.url.encodedPath.endsWith("/streetview/metadata"))
            FakeHttp.json(req, 200, """{"status":"OK","pano_id":"PANO","location":{"lat":52.20030,"lng":0.12600},"copyright":"© 2025 Google","date":"2025-04"}""")
        }
        val photo = google.streetPhoto(est, "KEY")!!
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("x").build().resolve(photo.imageUrl)!!
        assertEquals("PANO", url.queryParameter("pano"))
        val heading = url.queryParameter("heading")!!.toDouble()
        // The business is north-east of the panorama.
        assertTrue(heading in 20.0..80.0)
        assertEquals("© 2025 Google", photo.copyright)
        assertEquals("2025-04", photo.date)
        assertEquals(1, budget.usage(Api.GOOGLE_STREETVIEW).today)
        assertEquals("io.github.sammeeeeeeee.higiene", http.requests[0].header("X-Android-Package"))
    }

    @Test
    fun noPanoramaCostsNothing() {
        http.handler = { req -> FakeHttp.json(req, 200, """{"status":"ZERO_RESULTS"}""") }
        assertNull(google.streetPhoto(est, "KEY"))
        assertEquals(0, budget.usage(Api.GOOGLE_STREETVIEW).today)
    }

    @Test
    fun checkKeyReportsTheRefusingApi() {
        http.handler = { req -> FakeHttp.json(req, 200, """{"status":"REQUEST_DENIED","error_message":"This API project is not authorized to use this API."}""") }
        val c = google.checkKey("KEY")
        assertEquals(GoogleClient.KeyCheck.Problem("Street View Static API", "This API project is not authorized to use this API."), c)

        http.handler = { req ->
            if (req.url.encodedPath.endsWith("metadata")) FakeHttp.json(req, 200, """{"status":"OK"}""")
            else FakeHttp.json(req, 403, """{"error":{"code":403,"message":"Places API (New) has not been used in project 1 before or it is disabled."}}""")
        }
        assertEquals("Places API (New)", (google.checkKey("KEY") as GoogleClient.KeyCheck.Problem).api)

        http.handler = { req ->
            if (req.url.encodedPath.endsWith("metadata")) FakeHttp.json(req, 200, """{"status":"OK"}""") else FakeHttp.json(req, 200, """{"places":[]}""")
        }
        assertEquals(GoogleClient.KeyCheck.Ok, google.checkKey("KEY"))
        // Key checks are free and never counted.
        assertEquals(0, budget.usage(Api.GOOGLE_SEARCH).today)
    }

    @Test
    fun nameSimilarity() {
        assertEquals(1.0, GoogleClient.nameSimilarity("@72.China", "72 China Restaurant"), 0.0)
        assertEquals(1.0, GoogleClient.nameSimilarity("McDonald's", "Mc Donalds"), 0.0)
        assertEquals(1.0, GoogleClient.nameSimilarity("Greggs Ltd", "Greggs"), 0.0)
        assertTrue(GoogleClient.nameSimilarity("Café Nero", "Caffe Nero") >= 0.5)
        assertTrue(GoogleClient.nameSimilarity("The Red Lion", "Red Lion Pub") >= 0.99)
        assertTrue(GoogleClient.nameSimilarity("Bagels Bar", "BB Grill House") < 0.34)
        assertEquals(0.0, GoogleClient.nameSimilarity("", "Anything"), 0.0)
        assertEquals(1.0, GoogleClient.nameSimilarity("The Coffee Shop", "Coffee Shop"), 0.0)
        assertEquals(1.0, GoogleClient.nameSimilarity("KFC", "KFC Cambridge Station Road"), 0.0)
    }

    /** Pairs that look alike only by accident must never match. */
    @Test
    fun nameSimilarityRejectsLookalikes() {
        for ((a, b) in listOf(
            "The Coffee Shop" to "Co-op",
            "M&S" to "Williams Bakery",
            "Spar" to "Sparrow Cafe",
            "Station Cafe" to "Corner Cafe",
            "Station Road Fish Bar" to "Co-op Food - Station Road",
            "Sunrise Pizza Kebab" to "Domino's Pizza",
        )) assertEquals("$a / $b", 0.0, GoogleClient.nameSimilarity(a, b), 0.0)
    }

    @Test
    fun matchNeedsAStrongNameAndThePostcodeWithoutAPosition() {
        val noPos = est.copy(lat = null, lon = null, loc = null)
        val here = "72 Regent St, Cambridge CB2 1DP, UK"
        assertEquals("a", GoogleClient.bestMatch(noPos, listOf(GoogleClient.Candidate("a", "72 China", 52.2, 0.12, here)))?.id)
        // Same name elsewhere in the country.
        assertNull(GoogleClient.bestMatch(noPos, listOf(GoogleClient.Candidate("a", "72 China", 51.5, -0.1, "1 High St, London E1 6AN, UK"))))
        assertNull(GoogleClient.bestMatch(noPos, listOf(GoogleClient.Candidate("b", "China Garden", 52.2, 0.12, here))))
    }

    @Test
    fun noPositionAndNoPostcodeMeansNoSearch() {
        assertNull(google.place(est.copy(lat = null, lon = null, loc = null, pcKey = null), "KEY"))
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun creditsThirdPartyProviders() {
        val p = GoogleClient.parsePlace(JSONObject("""{"id":"x","rating":4.0,"attributions":[{"provider":"Example Data Ltd","providerUri":"https://example.org"},{"provider":""}]}"""))
        assertEquals(listOf(GooglePlace.Attribution("Example Data Ltd", "https://example.org")), p.attributions)
    }

    @Test
    fun bearing() {
        assertEquals(0.0, GoogleClient.bearing(51.0, 0.0, 52.0, 0.0), 0.01)
        assertEquals(90.0, GoogleClient.bearing(0.0, 0.0, 0.0, 1.0), 0.01)
        assertEquals(180.0, GoogleClient.bearing(52.0, 0.0, 51.0, 0.0), 0.01)
        assertEquals(270.0, GoogleClient.bearing(0.0, 1.0, 0.0, 0.0), 0.01)
    }

    @Test
    fun parsesAPermanentlyClosedPlaceWithFewFields() {
        val p = GoogleClient.parsePlace(JSONObject("""{"id":"x","businessStatus":"CLOSED_PERMANENTLY","websiteUri":"javascript:alert(1)"}"""))
        assertTrue(p.permanentlyClosed)
        assertNull(p.rating)
        assertNull(p.website)
        assertTrue(p.hasDetails)
        assertFalse(GoogleClient.parsePlace(JSONObject("""{"id":"y"}""")).hasDetails)
    }
}
