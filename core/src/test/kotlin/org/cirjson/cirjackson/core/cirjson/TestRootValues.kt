package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamReadException
import java.io.InputStream
import java.io.Reader
import kotlin.test.*

class TestRootValues : TestBase() {

    @Test
    fun testSimpleNumbers() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simpleNumbers(mode)
        }
    }

    private fun simpleNumbers(mode: Int) {
        val doc = if (mode != MODE_DATA_INPUT) "1 2\t3\r4\n5\r\n6\r\n   7" else "1 2\t3\r4\n5\r\n6\r\n   7 "
        val parser = createParser(mode, doc)

        for (expected in 1..7) {
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            val actual = parser.intValue
            assertEquals(expected, actual)
        }

        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testBrokenNumber() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            brokenNumber(mode)
        }
    }

    private fun brokenNumber(mode: Int) {
        val doc = "14:89:FD:D3:E7:8C"
        val parser = createParser(mode, doc)

        try {
            parser.nextToken()
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, "unexpected character")
        }

        parser.close()
    }

    @Test
    fun testSimpleBooleans() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simpleBooleans(mode)
        }
    }

    private fun simpleBooleans(mode: Int) {
        val doc = if (mode != MODE_DATA_INPUT) {
            "true false\ttrue\rfalse\ntrue\r\nfalse\r\n   true"
        } else {
            "true false\ttrue\rfalse\ntrue\r\nfalse\r\n   true "
        }
        val parser = createParser(mode, doc)
        var expected = true

        for (i in 1..7) {
            assertToken(if (expected) CirJsonToken.VALUE_TRUE else CirJsonToken.VALUE_FALSE, parser.nextToken())
            val actual = parser.booleanValue
            assertEquals(expected, actual)
            expected = !expected
        }

        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testInvalidToken() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (weirdChar in WEIRD_CHARS) {
                invalidToken(mode, weirdChar)
            }
        }
    }

    private fun invalidToken(mode: Int, weirdChar: Char) {
        val doc = " A${weirdChar}C "
        val parser = createParser(mode, doc)

        try {
            parser.nextToken()
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, "Unrecognized token")
        }

        parser.close()
    }

    @Test
    fun testSimpleWrites() {
        for (mode in ALL_GENERATOR_MODES) {
            simpleWrites(mode)
        }
    }

    private fun simpleWrites(mode: Int) {
        val generator = createGenerator(mode)
        generator.writeNumber(123)
        generator.writeString("abc")
        generator.writeBoolean(true)
        generator.close()
        assertEquals("123 \"abc\" true", generator.streamWriteOutputTarget!!.toString())
    }

    private class MockInputStream(private val myReads: Array<ByteArray>) : InputStream() {

        private var myCurrentRead = 0

        override fun read(): Int {
            throw UnsupportedOperationException()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (myCurrentRead >= myReads.size) {
                return -1
            }

            val bytes = myReads[myCurrentRead++]

            if (len < bytes.size) {
                throw IllegalArgumentException()
            }

            bytes.copyInto(b, off, 0, bytes.size)
            return bytes.size
        }

    }

    @Test
    fun testRootOffsetChars() {
        val input = MockReader(arrayOf("1234".toCharArray(), "5 true".toCharArray()))
        val parser = CIRJSON_FACTORY.createParser(ObjectReadContext.empty(), input)
        assertEquals(12345, parser.nextIntValue(0))
        assertTrue(parser.nextBooleanValue()!!)
        parser.close()
    }

    @Test
    fun testRootOffsetBytes() {
        val input = MockInputStream(arrayOf("1234".toByteArray(), "5 true".toByteArray()))
        val parser = CIRJSON_FACTORY.createParser(ObjectReadContext.empty(), input)
        assertEquals(12345, parser.nextIntValue(0))
        assertTrue(parser.nextBooleanValue()!!)
        parser.close()
    }

    private class MockReader(private val myReads: Array<CharArray>) : Reader() {

        private var myCurrentRead = 0

        override fun close() {
            // no-op
        }

        override fun read(): Int {
            throw UnsupportedOperationException()
        }

        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (myCurrentRead >= myReads.size) {
                return -1
            }

            val chars = myReads[myCurrentRead++]

            if (len < chars.size) {
                throw IllegalArgumentException()
            }

            chars.copyInto(cbuf, off, 0, chars.size)
            return chars.size
        }

    }

    companion object {

        private val WEIRD_CHARS = charArrayOf('\u00c4', '\u3456')

    }

}