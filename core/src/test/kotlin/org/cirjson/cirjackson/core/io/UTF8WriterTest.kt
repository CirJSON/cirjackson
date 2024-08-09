package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.TestBase
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.*

class UTF8WriterTest : TestBase() {

    @Test
    fun testSimple() {
        val output = ByteArrayOutputStream()
        val writer = UTF8Writer(testIOContext(), output)

        val string = "AB\u00A0\u1AE9\uFFFC"
        val ch = string.toCharArray()

        writer.write(string)
        writer.append(ch[0])
        writer.write(ch[1].code)
        writer.write(ch, 2, 3)
        writer.flush()

        writer.write(string, 0, string.length)
        writer.close()

        val data = output.toByteArray()
        assertEquals(3 * 10, data.size)
        val actual = utf8String(output)
        assertEquals(15, actual.length)

        assertEquals(3 * string.length, actual.length)
        assertEquals("$string$string$string", actual)
    }

    @Test
    fun testSimpleAscii() {
        val output = ByteArrayOutputStream()
        val writer = UTF8Writer(testIOContext(), output)

        val string = "abcdefghijklmnopqrst\u00A0"
        val ch = string.toCharArray()

        writer.write(ch, 0, ch.size)
        writer.flush()
        writer.close()

        val data = output.toByteArray()
        assertEquals(string.length + 1, data.size)
        val actual = utf8String(output)
        assertEquals(string, actual)
    }

    @Test
    fun testFlushAfterClose() {
        val output = ByteArrayOutputStream()
        val writer = UTF8Writer(testIOContext(), output)

        writer.write('X'.code)
        val ch = charArrayOf('Y')
        writer.write(ch)

        writer.close()
        assertEquals(2, output.size())

        writer.flush()
        writer.close()
        writer.flush()
    }

    @Test
    fun testSurrogatesOk() {
        var output = ByteArrayOutputStream()
        var writer = UTF8Writer(testIOContext(), output)

        writer.write(0xD83D)
        writer.write(0xDE03)
        writer.close()
        assertEquals(4, output.size())
        val expectedSurrogates = byteArrayOf(0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x83.toByte())
        assertContentEquals(expectedSurrogates, output.toByteArray())

        output = ByteArrayOutputStream()
        writer = UTF8Writer(testIOContext(), output)
        writer.write("\uD83D\uDE03")
        writer.close()
        assertEquals(4, output.size())
        assertContentEquals(expectedSurrogates, output.toByteArray())

        output = ByteArrayOutputStream()
        writer = UTF8Writer(testIOContext(), output)
        writer.write(charArrayOf('\uD83D', '\uDE03'))
        writer.close()
        assertEquals(4, output.size())
        assertContentEquals(expectedSurrogates, output.toByteArray())
    }

    @Test
    fun testSurrogatesFail() {
        var output = ByteArrayOutputStream()

        try {
            UTF8Writer(testIOContext(), output).use { writer ->
                writer.write(0xDE03)
                fail("should not pass")
            }
        } catch (e: IOException) {
            verifyException(e, "Unmatched second part")
        }

        output = ByteArrayOutputStream()

        try {
            UTF8Writer(testIOContext(), output).use { writer ->
                writer.write(0xD83D)
                writer.write('a'.code)
                fail("should not pass")
            }
        } catch (e: IOException) {
            verifyException(e, "Broken surrogate pair")
        }

        output = ByteArrayOutputStream()

        try {
            UTF8Writer(testIOContext(), output).use { writer ->
                writer.write(0xD83D)
                writer.write(0xFFFF)
                fail("should not pass")
            }
        } catch (e: IOException) {
            verifyException(e, "Broken surrogate pair")
        }

        output = ByteArrayOutputStream()

        try {
            UTF8Writer(testIOContext(), output).use { writer ->
                writer.write("\uDE03")
                fail("should not pass")
            }
        } catch (e: IOException) {
            verifyException(e, "Unmatched second part")
        }

        output = ByteArrayOutputStream()

        try {
            UTF8Writer(testIOContext(), output).use { writer ->
                writer.write("\uD83Da")
                fail("should not pass")
            }
        } catch (e: IOException) {
            verifyException(e, "Broken surrogate pair")
        }

        output = ByteArrayOutputStream()

        try {
            UTF8Writer(testIOContext(), output).use { writer ->
                writer.write("\uD83D\uFFFF")
                fail("should not pass")
            }
        } catch (e: IOException) {
            verifyException(e, "Broken surrogate pair")
        }
    }

    @Test
    fun testSurrogateConversion() {
        for (first in UTF8Writer.SURR1_FIRST..UTF8Writer.SURR1_LAST) {
            for (second in UTF8Writer.SURR2_FIRST..UTF8Writer.SURR2_LAST) {
                val expected = (first - UTF8Writer.SURR1_FIRST shl 10) + second + 0x10000 - UTF8Writer.SURR2_FIRST
                val actual = (first shl 10) + second + UTF8Writer.SURROGATE_BASE

                if (expected != actual) {
                    fail(("Mismatch on: ${Integer.toHexString(first)} ${
                        Integer.toHexString(second)
                    }; expected: $expected, actual: $actual"))
                }
            }
        }
    }

    @Test
    fun testIllegalSurrogateDesc() {
        var desc = UTF8Writer.illegalSurrogateDesc(0x1FFFFF)
        assertTrue(desc.startsWith("Illegal character point"))
        assertTrue(desc.endsWith(" to output; max is 0x10FFFF as per CirJSON standards"))

        desc = UTF8Writer.illegalSurrogateDesc(0x0)
        assertTrue(desc.startsWith("Illegal character point"))
        assertTrue(desc.endsWith(" to output"))

        desc = UTF8Writer.illegalSurrogateDesc(0xDBF0)
        assertTrue(desc.startsWith("Unmatched first part of surrogate pair"))

        desc = UTF8Writer.illegalSurrogateDesc(0xDC0F)
        assertTrue(desc.startsWith("Unmatched second part of surrogate pair"))
    }

}