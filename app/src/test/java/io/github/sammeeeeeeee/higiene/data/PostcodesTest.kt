package io.github.sammeeeeeeee.higiene.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PostcodesTest {
    @Test
    fun keysAndFormat() {
        assertEquals("SW1A1AA", Postcodes.key(" sw1a 1aa "))
        assertEquals("SW1A 1AA", Postcodes.format("SW1A1AA"))
        assertEquals("E1 6AN", Postcodes.format("E16AN"))
        assertNull(Postcodes.key("  "))
    }

    @Test
    fun parseQueries() {
        assertEquals(Postcodes.Query.Full("SW1A1AA"), Postcodes.parse("sw1a1aa"))
        assertEquals(Postcodes.Query.Full("BT11AA"), Postcodes.parse("BT1 1AA"))
        assertEquals(Postcodes.Query.Outcode("SW11"), Postcodes.parse("SW11"))
        assertEquals(Postcodes.Query.Sector("SW1 1"), Postcodes.parse("SW1 1"))
        assertEquals(Postcodes.Query.Sector("SW1A 1"), Postcodes.parse("sw1a  1"))
        assertEquals("E1 6", Postcodes.sectorOf("E16AN"))
        assertEquals("E16", Postcodes.outcodeOf("E161AB"))
        assertEquals("E16 1", Postcodes.sectorOf("E161AB"))
        assertEquals(Postcodes.Query.Outcode("M1"), Postcodes.parse("m1"))
        assertNull(Postcodes.parse("pizza"))
        assertNull(Postcodes.parse("greggs high street"))
        assertNull(Postcodes.parse("1"))
    }

    @Test
    fun packIsUniqueAndBounded() {
        val a = Postcodes.pack("SW1A1AA")
        val b = Postcodes.pack("SW1A1AB")
        val c = Postcodes.pack("W1A1AA") // shorter key must not collide with a longer one
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        assertTrue(a in 0 until (1L shl 40))
        assertEquals(-1L, Postcodes.pack("TOOLONG12"))
        assertEquals(-1L, Postcodes.pack("AB-1"))
    }

    @Test
    fun indexRoundTrip() {
        val f = File.createTempFile("pcidx", ".bin")
        try {
            val b = PostcodeIndex.Builder()
            b.add("SW1A1AA", 529090, 179645)
            b.add("AB101AB", 394235, 806529)
            b.add("CB21DP", 545409, 257905)
            b.add("SW1A1AA", 1, 1) // duplicate: first wins
            b.writeTo(f)
            val idx = PostcodeIndex.open(f)!!
            assertEquals(3, idx.count)
            val sw = idx.lookup("SW1A1AA")!!
            assertTrue(Repository.distance(sw[0], sw[1], 51.50101, -0.141563) < 10)
            assertTrue(idx.lookup("CB21DP") != null)
            assertNull(idx.lookup("ZZ99ZZ"))
            assertEquals(8L + 3 * 11, f.length())
        } finally {
            f.delete()
        }
    }
}
