package io.github.sammeeeeeeee.higiene.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvReaderTest {
    private fun reader(s: String, buf: Int = 7) = CsvReader(ByteArrayInputStream(s.toByteArray(Charsets.UTF_8)), buf)

    private fun rows(s: String, buf: Int = 7): List<List<String>> {
        val r = reader(s, buf)
        return buildList { while (r.next()) add(List(r.fieldCount) { r.string(it) }) }
    }

    @Test
    fun bomQuotesAndCrlf() {
        val csv = "﻿\"a\",\"b\"\r\n\"1\",\"two, three\"\r\n"
        assertEquals(listOf(listOf("a", "b"), listOf("1", "two, three")), rows(csv))
    }

    @Test
    fun doubledQuotesAndEmbeddedNewlines() {
        val csv = "\"x\",\"He said \"\"hi\"\"\",\"line1\r\n\tline2\"\n\"y\",\"\",\"\"\n"
        val r = rows(csv)
        assertEquals("He said \"hi\"", r[0][1])
        assertEquals("line1\r\n\tline2", r[0][2])
        assertEquals(listOf("y", "", ""), r[1])
    }

    @Test
    fun unquotedFieldsTrailingCommaAndNoFinalNewline() {
        assertEquals(listOf(listOf("a", "b", ""), listOf("1", "2", "3")), rows("a,b,\n1,2,3"))
    }

    @Test
    fun skipsBlankLines() {
        assertEquals(listOf(listOf("a"), listOf("b")), rows("a\r\n\r\n\nb\r\n"))
    }

    @Test
    fun utf8AcrossBufferBoundaries() {
        val r = rows("\"Caffè Nero\",\"Ynys Môn – café\"\n", buf = 3)
        assertEquals(listOf("Caffè Nero", "Ynys Môn – café"), r[0])
    }

    @Test
    fun numbers() {
        val r = reader("\"027\",\" 12 \",\"\",\"-4\",\"x1\",\"52.2003982\"\n")
        assertTrue(r.next())
        assertEquals(27L, r.long(0))
        assertEquals(12L, r.long(1))
        assertNull(r.long(2))
        assertEquals(-4L, r.long(3))
        assertNull(r.long(4))
        assertEquals(52.2003982, r.double(5)!!, 0.0)
        assertTrue(r.isBlank(2))
        assertFalse(r.isBlank(0))
        assertNull(r.stringOrNull(2))
    }

    @Test
    fun hashChangesWithAnyField() {
        val r = reader("\"a\",\"b\"\n\"a\",\"b\"\n\"a\",\"c\"\n\"ab\",\"\"\n")
        r.next(); val h1 = r.hash
        r.next(); val h2 = r.hash
        r.next(); val h3 = r.hash
        r.next(); val h4 = r.hash
        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
        // Field boundaries matter: "a","b" must differ from "ab","".
        assertNotEquals(h1, h4)
    }

    @Test(expected = CsvFormatException::class)
    fun unterminatedQuoteFails() {
        val r = reader("\"abc\n")
        r.next()
    }
}
