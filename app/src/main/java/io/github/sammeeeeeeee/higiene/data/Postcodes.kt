package io.github.sammeeeeeeee.higiene.data

/** UK postcode helpers. A "key" is the postcode upper-cased with all spaces removed, e.g. "SW1A1AA". */
object Postcodes {
    private val FULL = Regex("^[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2}$")
    private val OUTCODE = Regex("^[A-Z]{1,2}[0-9][A-Z0-9]?$")

    fun key(raw: String?): String? {
        if (raw == null) return null
        val sb = StringBuilder(8)
        for (ch in raw) if (ch.isLetterOrDigit()) sb.append(ch.uppercaseChar())
        return sb.toString().ifEmpty { null }
    }

    sealed interface Query {
        val key: String
        data class Full(override val key: String) : Query
        /** Key with a space, e.g. "E1 6", so it can't be confused with outcode "E16". */
        data class Sector(override val key: String) : Query
        data class Outcode(override val key: String) : Query
    }

    /**
     * Reads user input as a postcode query. Without a space "SW11" is ambiguous (outcode SW11 or
     * sector SW1 1), so sectors are recognised only when the user typed the space.
     */
    fun parse(raw: String): Query? {
        val trimmed = raw.trim().uppercase()
        val k = key(trimmed) ?: return null
        if (isFull(k)) return Query.Full(k)
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size == 2 && OUTCODE.matches(parts[0]) && parts[1].length == 1 && parts[1][0].isDigit()) {
            return Query.Sector(parts[0] + " " + parts[1])
        }
        if (parts.size == 1 && isOutcode(k)) return Query.Outcode(k)
        return null
    }

    fun isFull(key: String) = FULL.matches(key)

    fun isOutcode(key: String) = OUTCODE.matches(key)

    /** Pretty form with a single space before the inward code. */
    fun format(key: String): String = if (key.length >= 5 && isFull(key)) key.substring(0, key.length - 3) + " " + key.takeLast(3) else key

    fun outcodeOf(fullKey: String) = fullKey.substring(0, fullKey.length - 3)

    /** "E16AN" -> "E1 6". The space keeps sectors apart from outcodes ("E16"). */
    fun sectorOf(fullKey: String) = outcodeOf(fullKey) + " " + fullKey[fullKey.length - 3]

    /**
     * Packs a postcode key of up to 7 characters into a Long (base 37), preserving uniqueness.
     * Returns -1 when the key can't be packed.
     */
    fun pack(key: String): Long {
        if (key.isEmpty() || key.length > 7) return -1
        var v = 0L
        for (i in 0 until 7) {
            val c = if (i < 7 - key.length) 0 else {
                val ch = key[i - (7 - key.length)]
                when (ch) {
                    in '0'..'9' -> 1 + (ch - '0')
                    in 'A'..'Z' -> 11 + (ch - 'A')
                    else -> return -1
                }
            }
            v = v * 37 + c
        }
        return v
    }
}
