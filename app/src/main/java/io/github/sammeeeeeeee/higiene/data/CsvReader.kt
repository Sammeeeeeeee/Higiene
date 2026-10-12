package io.github.sammeeeeeeee.higiene.data

import java.io.InputStream

/**
 * Streaming RFC 4180 reader that works on bytes, so the 145 MB FSA file is never held in memory
 * and unchanged rows are never decoded into Strings.
 *
 * Handles quoted fields, doubled quotes, embedded CR/LF inside quotes, CRLF or LF row ends and a
 * leading UTF-8 BOM. After [next] returns true, fields are read with [string], [isBlank], [long].
 * [hash] is a 64-bit FNV-1a over the unescaped fields of the current row, used to skip unchanged rows.
 */
class CsvReader(private val input: InputStream, bufferSize: Int = 1 shl 16) {
    private val buf = ByteArray(bufferSize)
    private var pos = 0
    private var lim = 0
    private var eof = false
    private var first = true

    private var data = ByteArray(8192)
    private var len = 0
    private var starts = IntArray(32)
    private var ends = IntArray(32)

    var fieldCount = 0
        private set
    var hash = 0L
        private set

    private fun fill(): Boolean {
        if (eof) return false
        val n = input.read(buf, 0, buf.size)
        if (n <= 0) {
            eof = true
            return false
        }
        pos = 0
        lim = n
        return true
    }

    private fun read(): Int {
        if (pos >= lim && !fill()) return -1
        return buf[pos++].toInt() and 0xFF
    }

    private fun peek(): Int {
        if (pos >= lim && !fill()) return -1
        return buf[pos].toInt() and 0xFF
    }

    private fun put(b: Int) {
        if (len == data.size) data = data.copyOf(data.size * 2)
        data[len++] = b.toByte()
    }

    private fun startField() {
        if (fieldCount == starts.size) {
            starts = starts.copyOf(starts.size * 2)
            ends = ends.copyOf(ends.size * 2)
        }
        starts[fieldCount] = len
    }

    private fun endField() {
        ends[fieldCount] = len
        fieldCount++
    }

    /** Reads the next non-empty row. Returns false at end of input. */
    fun next(): Boolean {
        while (true) {
            fieldCount = 0
            len = 0
            if (first) {
                first = false
                if (peek() == 0xEF) {
                    read()
                    if (read() != 0xBB || read() != 0xBF) throw CsvFormatException("Bad byte order mark")
                }
            }
            var c = read()
            if (c == -1) return false
            if (c == '\r'.code || c == '\n'.code) continue // blank line
            // Parse one row.
            while (true) {
                startField()
                if (c == '"'.code) {
                    while (true) {
                        c = read()
                        if (c == -1) throw CsvFormatException("Unterminated quoted field")
                        if (c == '"'.code) {
                            if (peek() == '"'.code) {
                                read()
                                put('"'.code)
                            } else break
                        } else put(c)
                    }
                    endField()
                    c = read()
                } else {
                    while (c != -1 && c != ','.code && c != '\n'.code && c != '\r'.code) {
                        put(c)
                        c = read()
                    }
                    endField()
                }
                when (c) {
                    // Next field. If the row ends straight after the comma, the unquoted branch
                    // above records an empty last field on the next pass.
                    ','.code -> c = read()
                    '\r'.code -> {
                        if (peek() == '\n'.code) read()
                        return finish()
                    }
                    '\n'.code, -1 -> return finish()
                    else -> throw CsvFormatException("Unexpected byte $c after quoted field")
                }
            }
        }
    }

    private fun finish(): Boolean {
        var h = FNV_OFFSET
        for (f in 0 until fieldCount) {
            for (i in starts[f] until ends[f]) {
                h = (h xor (data[i].toLong() and 0xFF)) * FNV_PRIME
            }
            h = (h xor 0x1F) * FNV_PRIME
        }
        hash = h
        return true
    }

    fun string(i: Int): String =
        if (i < 0 || i >= fieldCount) "" else String(data, starts[i], ends[i] - starts[i], Charsets.UTF_8)

    /** Trimmed string, or null when blank. */
    fun stringOrNull(i: Int): String? = string(i).trim().ifEmpty { null }

    fun isBlank(i: Int): Boolean {
        if (i < 0 || i >= fieldCount) return true
        for (k in starts[i] until ends[i]) if (data[k] != ' '.code.toByte()) return false
        return true
    }

    /** Parses an unsigned/negative ASCII integer field without allocating. Null if blank or invalid. */
    fun long(i: Int): Long? {
        if (i < 0 || i >= fieldCount) return null
        var k = starts[i]
        val end = ends[i]
        while (k < end && data[k] == ' '.code.toByte()) k++
        if (k == end) return null
        var neg = false
        if (data[k] == '-'.code.toByte()) { neg = true; k++ }
        var v = 0L
        var digits = 0
        while (k < end) {
            val d = data[k] - '0'.code.toByte()
            if (d !in 0..9) break
            v = v * 10 + d
            digits++
            k++
        }
        while (k < end && data[k] == ' '.code.toByte()) k++
        if (digits == 0 || k != end) return null
        return if (neg) -v else v
    }

    fun double(i: Int): Double? = stringOrNull(i)?.toDoubleOrNull()

    companion object {
        private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        private const val FNV_PRIME = 0x100000001b3L
    }
}

class CsvFormatException(message: String) : java.io.IOException(message)
