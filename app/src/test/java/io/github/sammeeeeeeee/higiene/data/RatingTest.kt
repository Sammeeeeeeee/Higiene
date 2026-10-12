package io.github.sammeeeeeeee.higiene.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RatingTest {
    @Test
    fun csvValues() {
        val expected = mapOf(
            "5" to Rating.R5, "0" to Rating.R0, "Pass" to Rating.PASS, "Pass and Eat Safe" to Rating.PASS_EAT_SAFE,
            "Improvement Required" to Rating.IMPROVEMENT_REQUIRED, "AwaitingInspection" to Rating.AWAITING_INSPECTION,
            "Awaiting Inspection" to Rating.AWAITING_INSPECTION, "AwaitingPublication" to Rating.AWAITING_PUBLICATION,
            "Exempt" to Rating.EXEMPT, "Something new" to Rating.UNKNOWN,
        )
        for ((csv, r) in expected) assertEquals(csv, r, Rating.fromCsv(csv))
        for (r in Rating.entries) assertEquals(r, Rating.fromCode(r.code))
    }

    @Test
    fun siteKey() {
        assertEquals(FsaImporter.siteKey("UNIT 3 10 HIGH ST", "E16AN"), FsaImporter.siteKey("Unit 3, 10 High St.", "E16AN"))
        assertNotEquals(FsaImporter.siteKey("Unit 3, 10 High St.", "E16AN"), FsaImporter.siteKey("Unit 31, 0 High St.", "E16AX"))
        assertEquals(null, FsaImporter.siteKey(null, "E16AN"))
        assertEquals(null, FsaImporter.siteKey("--", "E16AN"))
    }
}
