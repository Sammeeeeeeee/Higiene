package io.github.sammeeeeeeee.higiene.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import io.github.sammeeeeeeee.higiene.data.GooglePlace
import io.github.sammeeeeeeee.higiene.ui.detail.GoogleCard
import io.github.sammeeeeeeee.higiene.ui.theme.HigieneTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleCardTest {
    @get:Rule
    val compose = createComposeRule()

    /** The tick and cross are pictures only, so each access tag must say yes or no to screen readers. */
    @Test
    fun accessTagsSayYesOrNo() {
        val place = GooglePlace(
            placeId = "x", rating = null, ratingCount = null, openNow = null, hours = emptyList(), phone = null, website = null,
            mapsUri = null, permanentlyClosed = false,
            access = GooglePlace.Access(entrance = true, toilet = false, seating = null, parking = null),
        )
        compose.setContent { HigieneTheme { GoogleCard(place) } }
        compose.onNodeWithContentDescription("Entrance: wheelchair accessible").assertExists()
        compose.onNodeWithContentDescription("Toilet: not wheelchair accessible").assertExists()
        // Unknown items are not shown at all.
        compose.onAllNodesWithContentDescription("Seating", substring = true).assertCountEquals(0)
    }
}
