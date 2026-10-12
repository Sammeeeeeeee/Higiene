package io.github.sammeeeeeeee.higiene.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.data.Centre
import io.github.sammeeeeeeee.higiene.data.CsvReader
import io.github.sammeeeeeeee.higiene.data.Db
import io.github.sammeeeeeeee.higiene.data.FsaImporter
import io.github.sammeeeeeeee.higiene.ui.theme.HigieneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders key screens to PNGs for design review. Opt-in (slow):
 * HIGIENE_SCREENSHOTS=build/screens ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val out = System.getenv("HIGIENE_SCREENSHOTS")
    private lateinit var app: HigieneApp

    @Before
    fun setUp() {
        assumeTrue(out != null)
        app = ApplicationProvider.getApplicationContext()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        SingletonImageLoader.setUnsafe { ctx: coil3.PlatformContext ->
            ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.coroutineContext(Dispatchers.Unconfined).build()
        }
    }

    private fun importSample() {
        val csv = javaClass.classLoader!!.getResource("fhrs_sample.csv").readBytes()
        FsaImporter(app.graph.db, app.graph.http, { null }, minRows = 10).merge(CsvReader(csv.inputStream()), 0, { false }) {}
        app.graph.authorities.ensureLoaded()
        runBlocking { app.graph.settings.setLastCentre(Centre(52.2003982, 0.1261394, "CB2 1DP", fromGps = false)) }
        app.graph.repo.notifyChanged()
    }

    private fun shot(name: String, dark: Boolean = false, open: Long? = null) {
        compose.setContent { HigieneTheme(darkTheme = dark) { AppNav(openEstablishment = open, onOpenedEstablishment = {}) } }
        // Let debounces (main looper) and database reads (IO threads) finish.
        repeat(6) {
            compose.waitForIdle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
            Thread.sleep(250)
            compose.mainClock.advanceTimeBy(400)
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test
    fun onboarding() {
        (app as Context).deleteDatabase(Db.NAME)
        shot("01-onboarding")
    }

    @Test
    fun nearby() {
        importSample()
        shot("02-nearby")
    }

    @Test
    fun nearbyDark() {
        importSample()
        shot("03-nearby-dark", dark = true)
    }

    @Test
    fun detail() {
        importSample()
        shot("04-detail", open = 1472964)
    }

    @Test
    fun favourites() {
        importSample()
        app.graph.repo.setFavourite(1472964, "@72.China", true)
        compose.setContent { HigieneTheme { io.github.sammeeeeeeee.higiene.ui.favourites.FavouritesScreen(onOpen = {}) } }
        repeat(4) {
            compose.waitForIdle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
            Thread.sleep(250)
        }
        compose.onRoot().captureRoboImage("$out/07-favourites.png")
    }

    @Test
    fun settings() {
        importSample()
        compose.setContent { HigieneTheme { io.github.sammeeeeeeee.higiene.ui.settings.SettingsScreen(onLicences = {}) } }
        repeat(4) {
            compose.waitForIdle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
            Thread.sleep(250)
        }
        compose.onRoot().captureRoboImage("$out/08-settings.png")
    }

    @Test
    fun detailScotland() {
        importSample()
        val id = app.graph.db.readableDatabase.rawQuery("SELECT id FROM est WHERE fhis = 1 AND rating = 'PASS' LIMIT 1", null).use { it.moveToFirst(); it.getLong(0) }
        shot("05-detail-scotland", open = id)
    }

    @Test
    fun detailWelsh() {
        importSample()
        val id = app.graph.db.readableDatabase.rawQuery("SELECT id FROM est WHERE welsh = 1 AND rnum IS NOT NULL LIMIT 1", null).use { it.moveToFirst(); it.getLong(0) }
        shot("06-detail-welsh", open = id)
    }
}
