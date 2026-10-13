package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MapillaryClientTest {
    private lateinit var userDb: UserDb
    private val http = FakeHttp()
    private lateinit var mapillary: MapillaryClient

    // The business, and photos around it. A photo north of the business looks at it when facing south (180°).
    private val lat = 52.2000
    private val lon = 0.1200

    private fun image(id: String, dLat: Double, dLon: Double, compass: Double?, pano: Boolean = false, at: Long = 1_700_000_000_000) =
        """{"id":"$id","computed_geometry":{"type":"Point","coordinates":[${lon + dLon},${lat + dLat}]},""" +
            (compass?.let { "\"computed_compass_angle\":$it," } ?: "") +
            """"is_pano":$pano,"captured_at":$at,"thumb_1024_url":"https://img.test/$id.jpg","creator":{"username":"snapper","id":"9"}}"""

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(UserDb.NAME)
        userDb = UserDb(context)
        mapillary = MapillaryClient(http.client, ApiBudget(userDb), base = "https://graph.test")
    }

    @After
    fun tearDown() = userDb.close()

    @Test
    fun picksTheNearestPhotoFacingTheBusiness() {
        val json = JSONObject("""{"data":[
            ${image("away", 0.0002, 0.0, compass = 0.0)},
            ${image("facing", 0.0002, 0.0, compass = 175.0)},
            ${image("far-facing", 0.0004, 0.0, compass = 180.0)},
            ${image("no-angle", 0.0001, 0.0, compass = null)}
        ]}""")
        val photo = MapillaryClient.choose(lat, lon, MapillaryClient.parse(json))!!
        assertEquals("facing", photo.id)
        assertEquals("snapper", photo.author)
        assertEquals("https://img.test/facing.jpg", photo.imageUrl)
        assertEquals("https://www.mapillary.com/app/?pKey=facing", photo.viewUrl)
    }

    @Test
    fun aPanoramaFacesEverywhereAndNewerWinsWhenEquallyClose() {
        val json = JSONObject("""{"data":[
            ${image("old", 0.0002, 0.0, compass = 180.0, at = 1_500_000_000_000)},
            ${image("pano", 0.00021, 0.0, compass = 0.0, pano = true, at = 1_750_000_000_000)}
        ]}""")
        assertEquals("pano", MapillaryClient.choose(lat, lon, MapillaryClient.parse(json))!!.id)
    }

    @Test
    fun sendsTheTokenAndSearchesAroundTheBusiness() {
        http.handler = { req -> FakeHttp.json(req, 200, """{"data":[${image("facing", 0.0002, 0.0, compass = 180.0)}]}""") }
        val e = Establishment(1, "X", null, null, 1, "", 1, Rating.R5, null, null, null, null, lat, lon, LocSource.FSA, false, null, false, false, null)
        assertEquals("facing", mapillary.photo(e, "MLY|abc")!!.id)
        val req = http.requests.single()
        assertEquals("OAuth MLY|abc", req.header("Authorization"))
        val box = req.url.queryParameter("bbox")!!.split(',').map { it.toDouble() }
        assertEquals(true, box[0] < lon && box[2] > lon && box[1] < lat && box[3] > lat)
    }

    @Test
    fun postcodePositionsAreTooRoughToAim() {
        val e = Establishment(1, "X", null, null, 1, "", 1, Rating.R5, null, null, null, null, lat, lon, LocSource.POSTCODE_OS, false, null, false, false, null)
        assertNull(mapillary.photo(e, "MLY|abc"))
        assertEquals(0, http.requests.size)
    }
}
