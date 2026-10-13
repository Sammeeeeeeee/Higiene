package io.github.sammeeeeeeee.higiene.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ApiBudgetTest {
    private lateinit var userDb: UserDb
    private var now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneId.of("Europe/London")).toInstant().toEpochMilli()
    private lateinit var budget: ApiBudget

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(UserDb.NAME)
        userDb = UserDb(context)
        budget = ApiBudget(userDb) { now }
    }

    @After
    fun tearDown() = userDb.close()

    @Test
    fun stopsAtTheDailyCap() {
        repeat(Api.GOOGLE_DETAILS.perDay) { assertTrue(budget.take(Api.GOOGLE_DETAILS)) }
        assertFalse(budget.take(Api.GOOGLE_DETAILS))
        // Other services have their own allowance.
        assertTrue(budget.take(Api.GOOGLE_STREETVIEW))
        assertEquals(ApiBudget.Usage(today = Api.GOOGLE_DETAILS.perDay, month = Api.GOOGLE_DETAILS.perDay), budget.usage(Api.GOOGLE_DETAILS))
        // Next day it is allowed again.
        now += 24L * 60 * 60 * 1000
        assertTrue(budget.take(Api.GOOGLE_DETAILS))
    }

    @Test
    fun stopsAtTheMonthlyCap() {
        now = ZonedDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneId.of("Europe/London")).toInstant().toEpochMilli()
        val day = 24L * 60 * 60 * 1000
        var taken = 0
        // 30 a day for 30 days reaches 900; the day count resets but the month does not.
        for (d in 0 until 31) {
            while (budget.take(Api.GOOGLE_DETAILS)) taken++
            if (d < 30) now += day else break
        }
        assertEquals(Api.GOOGLE_DETAILS.perMonth, taken)
    }

    @Test
    fun monthStartsAtMidnightPacific() {
        // Midnight on 1 Nov 2026 in California (still summer time, UTC-7) is 07:00 UTC; a minute
        // earlier is October's allowance.
        val oct = ZonedDateTime.of(2026, 11, 1, 6, 59, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val nov = ZonedDateTime.of(2026, 11, 1, 7, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli(), ApiBudget.startOfMonth(oct))
        assertEquals(nov, ApiBudget.startOfMonth(nov))
    }
}
