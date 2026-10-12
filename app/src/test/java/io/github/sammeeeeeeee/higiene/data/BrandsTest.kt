package io.github.sammeeeeeeee.higiene.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrandsTest {
    private val names: Map<String, Int> by lazy {
        val json = JSONObject(File("src/main/assets/brands.json").readText())
        val n = json.getJSONObject("names")
        n.keys().asSequence().associateWith { n.getInt(it) }
    }
    private val brands by lazy {
        val arr = JSONObject(File("src/main/assets/brands.json").readText()).getJSONArray("brands")
        List(arr.length()) { arr.getJSONArray(it).getString(0) }
    }

    private fun match(name: String): String? = Brands.findIndex(names, Brands.normalise(name)).let { if (it >= 0) brands[it] else null }

    @Test
    fun normalise() {
        assertEquals("mcdonalds restaurants ltd", Brands.normalise("McDonald's Restaurants Ltd"))
        assertEquals("caffe nero", Brands.normalise("Caffè Nero"))
        assertEquals("marks and spencer", Brands.normalise("Marks & Spencer"))
    }

    @Test
    fun matchesChains() {
        assertEquals("McDonald's", match("McDonald's Restaurants Ltd"))
        assertEquals("Greggs", match("Greggs Plc"))
        assertEquals("Greggs", match("GREGGS"))
        assertEquals("Sainsbury's Local", match("Sainsbury's Local"))
        assertEquals("Tesco Express", match("Tesco Express"))
        assertEquals("Costa", match("Costa Coffee"))
        assertEquals("KFC", match("KFC"))
    }

    @Test
    fun leavesIndependentsAlone() {
        for (n in listOf("Costa Del Sol", "Paul's Cafe", "The Crown", "Leon's Kebab House", "Best Kebab", "Central Fish Bar", "Golden Dragon")) {
            assertEquals("unexpected match for $n", null, match(n))
        }
    }

    @Test
    fun logoUrlsAreEncoded() {
        val b = Brands.Brand("McDonald's", "Q38076", "McDonald's square 2020.svg", "mcdonalds")
        assertTrue(b.logoUrls[0].startsWith("https://commons.wikimedia.org/wiki/Special:FilePath/McDonald%27s_square_2020.svg?width=120"))
        assertEquals("https://graph.facebook.com/mcdonalds/picture?type=large", b.logoUrls[1])
    }
}
