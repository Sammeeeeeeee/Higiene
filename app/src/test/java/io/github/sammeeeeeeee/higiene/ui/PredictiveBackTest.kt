package io.github.sammeeeeeeee.higiene.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.data.Centre
import io.github.sammeeeeeeee.higiene.data.CsvReader
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
 * Records frames of going back from the detail page by swipe and by the back arrow, to check by eye
 * that they match. Opt-in:
 * HIGIENE_SCREENSHOTS=build/screens ./gradlew testDebugUnitTest --tests '*PredictiveBackTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PredictiveBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = System.getenv("HIGIENE_SCREENSHOTS")
    private lateinit var app: HigieneApp

    @Before
    fun setUp() {
        assumeTrue(out != null)
        app = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        SingletonImageLoader.setUnsafe { ctx: coil3.PlatformContext ->
            ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.coroutineContext(Dispatchers.Unconfined).build()
        }
        val csv = javaClass.classLoader!!.getResource("fhrs_sample.csv").readBytes()
        FsaImporter(app.graph.db, app.graph.http, { null }, app.cacheDir, minRows = 10).merge(CsvReader(csv.inputStream()), 0, { false }) {}
        app.graph.authorities.ensureLoaded()
        runBlocking { app.graph.settings.setLastCentre(Centre(52.2003982, 0.1261394, "CB2 1DP", fromGps = false)) }
        app.graph.repo.notifyChanged()
    }

    private fun settle() {
        repeat(6) {
            compose.waitForIdle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
            Thread.sleep(250)
            compose.mainClock.advanceTimeBy(400)
        }
        compose.waitForIdle()
    }

    private fun open() {
        // Like MainActivity: the request to open a business is cleared once it has been followed.
        val open = androidx.compose.runtime.mutableStateOf<Long?>(1472964)
        compose.setContent { HigieneTheme { AppNav(openEstablishment = open.value, onOpenedEstablishment = { open.value = null }) } }
        settle()
        compose.mainClock.autoAdvance = false
    }

    private fun record(dir: String, frames: Int = 10) {
        repeat(frames) {
            compose.mainClock.advanceTimeBy(40)
            compose.onRoot().captureRoboImage("$out/$dir/%02d-%03dms.png".format(it, (it + 1) * 40))
        }
    }

    /** A swipe that is dragged part way and released should look exactly like tapping the back arrow. */
    @Test
    fun swipeBackFromDetail() {
        open()
        val back = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread { back.dispatchOnBackStarted(BackEventCompat(0f, 1200f, 0f, BackEventCompat.EDGE_LEFT)) }
        for (p in listOf(0.1f, 0.3f, 0.5f)) {
            compose.runOnUiThread { back.dispatchOnBackProgressed(BackEventCompat(p * 600f, 1200f, p, BackEventCompat.EDGE_LEFT)) }
            compose.mainClock.advanceTimeBy(48)
        }
        compose.onRoot().captureRoboImage("$out/swipe/drag.png")
        compose.runOnUiThread { back.onBackPressed() }
        record("swipe")
    }

    @Test
    fun tapBackFromDetail() {
        open()
        compose.onNodeWithContentDescription("Back").performClick()
        record("tap")
    }
}
