package io.github.sammeeeeeeee.higiene.data

/**
 * Businesses just shown in a list, kept in memory so a business page can draw its header and rating
 * straight away while the full record (who's cooking, history…) loads.
 */
class RecentListings(private val max: Int = 800) {
    data class Item(val est: Establishment, val distanceM: Double?, val brand: Brands.Brand?, val favourite: Boolean? = null)

    private val map = object : LinkedHashMap<Long, Item>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Item>?) = size > max
    }

    @Synchronized
    fun put(items: Iterable<Item>) {
        items.forEach { map[it.est.id] = it }
    }

    @Synchronized
    fun get(id: Long): Item? = map[id]
}
