package io.github.sammeeeeeeee.higiene.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsgbTest {
    @Test
    fun osWorkedExampleInverseTransverseMercator() {
        // OS "A guide to coordinate systems in Great Britain", worked example C.1 (reverse):
        // E 651409.903, N 313177.270 -> 52°39'27.2531"N, 1°43'4.5177"E on OSGB36.
        val ll = Osgb.gridToOsgb36(651409.903, 313177.270)
        assertEquals(52 + 39 / 60.0 + 27.2531 / 3600, ll[0], 1e-7)
        assertEquals(1 + 43 / 60.0 + 4.5177 / 3600, ll[1], 1e-7)
    }

    @Test
    fun wgs84MatchesPostcodesIoWithinHelmertAccuracy() {
        // Eastings/northings from OS Code-Point Open; lat/lon from api.postcodes.io (OSTN15).
        val cases = listOf(
            doubleArrayOf(529090.0, 179645.0, 51.50101, -0.141563), // SW1A 1AA
            doubleArrayOf(531073.0, 182317.0, 51.524567, -0.112017), // EC1A 1BB
            doubleArrayOf(394235.0, 806529.0, 57.14959, -2.096923), // AB10 1AB
            doubleArrayOf(545409.0, 257905.0, 52.200216, 0.126277), // CB2 1DP
        )
        for (c in cases) {
            val ll = Osgb.gridToWgs84(c[0], c[1])
            val metres = Repository.distance(ll[0], ll[1], c[2], c[3])
            assertTrue("off by $metres m for ${c.toList()}", metres < 10)
        }
    }
}
