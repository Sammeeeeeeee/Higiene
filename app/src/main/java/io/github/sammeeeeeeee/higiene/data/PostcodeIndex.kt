package io.github.sammeeeeeeee.higiene.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Offline postcode → grid reference lookup for every GB postcode (OS Code-Point Open, ~1.7 million
 * rows) in a compact sorted binary file: 11 bytes per postcode, binary-searched through a memory map.
 *
 * Layout: "HPC1" magic, Int32 count, then [count] records of
 * 5-byte packed key (see [Postcodes.pack]), 3-byte easting, 3-byte northing, all big-endian.
 */
class PostcodeIndex private constructor(private val buf: ByteBuffer, val count: Int) {

    /** Returns WGS84 [lat, lon] for a full postcode key, or null if unknown. */
    fun lookup(key: String): DoubleArray? {
        val packed = Postcodes.pack(key)
        if (packed < 0) return null
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val k = keyAt(mid)
            when {
                k < packed -> lo = mid + 1
                k > packed -> hi = mid - 1
                else -> {
                    val base = HEADER + mid * RECORD + 5
                    return Osgb.gridToWgs84(int24(base).toDouble(), int24(base + 3).toDouble())
                }
            }
        }
        return null
    }

    private fun keyAt(i: Int): Long {
        val base = HEADER + i * RECORD
        var v = 0L
        for (j in 0 until 5) v = (v shl 8) or (buf.get(base + j).toLong() and 0xFF)
        return v
    }

    private fun int24(at: Int): Int =
        ((buf.get(at).toInt() and 0xFF) shl 16) or ((buf.get(at + 1).toInt() and 0xFF) shl 8) or (buf.get(at + 2).toInt() and 0xFF)

    companion object {
        private const val HEADER = 8
        private const val RECORD = 11
        private val MAGIC = byteArrayOf('H'.code.toByte(), 'P'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())

        fun open(file: File): PostcodeIndex? {
            if (!file.exists() || file.length() < HEADER) return null
            RandomAccessFile(file, "r").use { raf ->
                val map = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                for (i in 0 until 4) if (map.get(i) != MAGIC[i]) return null
                val count = map.getInt(4)
                if (count < 0 || HEADER.toLong() + count.toLong() * RECORD != raf.length()) return null
                return PostcodeIndex(map, count)
            }
        }
    }

    /** Collects postcodes in any order, then writes them sorted. */
    class Builder {
        private var keys = LongArray(1 shl 16)
        private var east = IntArray(1 shl 16)
        private var north = IntArray(1 shl 16)
        var size = 0
            private set

        fun add(key: String, easting: Int, northing: Int): Boolean {
            val packed = Postcodes.pack(key)
            if (packed < 0 || easting !in 1..0xFFFFFF || northing !in 1..0xFFFFFF) return false
            if (size == keys.size) {
                keys = keys.copyOf(size * 2)
                east = east.copyOf(size * 2)
                north = north.copyOf(size * 2)
            }
            keys[size] = packed
            east[size] = easting
            north[size] = northing
            size++
            return true
        }

        fun eastingAt(i: Int) = east[i]
        fun northingAt(i: Int) = north[i]

        /** Writes to [tmp] then renames over [target], so readers never see a half-written file. */
        fun writeTo(target: File, tmp: File = File(target.path + ".tmp")) {
            require(size < (1 shl 21)) { "Too many postcodes: $size" }
            val order = LongArray(size) { (keys[it] shl 21) or it.toLong() }
            order.sort()
            BufferedOutputStream(FileOutputStream(tmp), 1 shl 16).use { out ->
                out.write(MAGIC)
                out.write(byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()))
                var last = -1L
                var written = 0
                val rec = ByteArray(RECORD)
                for (v in order) {
                    val idx = (v and 0x1FFFFF).toInt()
                    val k = keys[idx]
                    if (k == last) continue // duplicate postcode: keep the first
                    last = k
                    for (j in 0 until 5) rec[j] = (k ushr (8 * (4 - j))).toByte()
                    put24(rec, 5, east[idx])
                    put24(rec, 8, north[idx])
                    out.write(rec)
                    written++
                }
                out.flush()
                if (written != size) {
                    // Rewrite the count when duplicates were dropped.
                    out.close()
                    RandomAccessFile(tmp, "rw").use { it.seek(4); it.writeInt(written) }
                }
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw java.io.IOException("Could not replace ${target.name}")
            }
        }

        private fun put24(b: ByteArray, at: Int, v: Int) {
            b[at] = (v ushr 16).toByte()
            b[at + 1] = (v ushr 8).toByte()
            b[at + 2] = v.toByte()
        }
    }
}
