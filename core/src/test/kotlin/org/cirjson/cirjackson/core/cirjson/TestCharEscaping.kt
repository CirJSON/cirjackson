package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.io.CharacterEscapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TestCharEscaping : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testMissingEscaping() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            missingEscaping(mode)
        }
    }

    private fun missingEscaping(mode: Int) {
        val doc = "[\"0\", \"Linefeed: \n.\"]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            parser.text
            fail("Expected an exception for un-escaped linefeed in string value")
        } catch (e: StreamReadException) {
            verifyException(e, "has to be escaped")
        }

        parser.close()
    }

    @Test
    fun testSimpleEscaping() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simpleEscaping(mode)
        }
    }

    private fun simpleEscaping(mode: Int) {
        var parser = createParser(factory, mode, "[\"0\", \"LF=\\n\"]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("LF=\n", parser.text)
        parser.close()

        parser = createParser(factory, mode, "[\"0\", \"NULL:\\u0000!\"]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("NULL:\u0000!", parser.text)
        parser.close()

        parser = createParser(factory, mode, "[\"0\", \"\\u0123\"]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("\u0123", parser.text)
        parser.close()

        parser = createParser(factory, mode, "[\"0\", \"\\u0041\\u0043\"]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("AC", parser.text)
        parser.close()
    }

    @Test
    fun testSimpleNameEscaping() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simpleNameEscaping(mode)
        }
    }

    private fun simpleNameEscaping(mode: Int) {
        for (i in 0..<16) {
            val base = "1234567890abcdef".substring(0, i)
            val inputKey = "$base\\\""
            val expected = "$base\""
            val parser = createParser(factory, mode, "{\"__cirJsonId__\": \"root\", \"$inputKey\": 123456789}       ")
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals(expected, parser.currentName())
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(123456789, parser.intValue)
            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
            parser.close()
        }
    }

    @Test
    fun testInvalid() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            invalid(mode)
        }
    }

    private fun invalid(mode: Int) {
        val doc = "[\"0\", \"\\u41=A\"]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            parser.text
            fail("Expected an exception for unclosed ARRAY")
        } catch (e: StreamReadException) {
            verifyException(e, "for character escape")
        }

        parser.close()
    }

    @Test
    fun testEightDigitSequence() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            eightDigitSequence(mode)
        }
    }

    private fun eightDigitSequence(mode: Int) {
        val parser = createParser(factory, mode, "[\"0\", \"\\u00411234\"]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("A1234", parser.text)
        parser.close()
    }

    @Test
    fun testEscapesForCharArrays() {
        for (mode in ALL_GENERATOR_MODES) {
            escapesForCharArrays(mode)
        }
    }

    private fun escapesForCharArrays(mode: Int) {
        val generator = createGenerator(factory, mode)
        generator.writeString(charArrayOf('\u0000'), 0, 1)
        generator.close()
        assertEquals("\"\\u0000\"", generator.streamWriteOutputTarget!!.toString())
    }

    @Test
    fun testInvalidEscape() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            invalidEscape(mode)
        }
    }

    private fun invalidEscape(mode: Int) {
        val parser = createParser(factory, mode, "[\"0\", \"\\u\u0080...\"]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            parser.text
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character")
        }

        parser.close()
    }

    @Test
    fun testEscapeNonLatin1() {
        for (mode in ALL_GENERATOR_MODES) {
            var factory = CirJsonFactory()
            escapeNonLatin1(factory, mode, VALUE_IN)

            factory = CirJsonFactory.builder().highestNonEscapedCharCode(255).build()
            escapeNonLatin1(factory, mode, "Line\\u2028feed, \u00D6l!")
        }
    }

    private fun escapeNonLatin1(factory: CirJsonFactory, mode: Int, expEncoded: String) {
        val generator = createGenerator(factory, mode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeString(VALUE_IN)
        generator.writeEndArray()
        generator.close()

        val actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals("[\"0\",\"$expEncoded\"]", actual)
    }

    @Test
    fun testWriteLongCustomEscapes() {
        for (mode in ALL_GENERATOR_MODES) {
            writeLongCustomEscapes(mode)
        }
    }

    private fun writeLongCustomEscapes(mode: Int) {
        val stringBuilder = StringBuilder()

        while (stringBuilder.length < 2000) {
            stringBuilder.append("\u65e5\u672c\u8a9e")
        }

        val factory = CirJsonFactory.builder().characterEscapes(CUSTOM_ESCAPES).highestNonEscapedCharCode(127).build()
        val generator = createGenerator(factory, mode)
        generator.writeString(stringBuilder.toString())
        generator.close()
    }

    companion object {

        private const val VALUE_IN = "Line\u2028feed, \u00D6l!"

        private val CUSTOM_ESCAPES = object : CharacterEscapes() {

            override val escapeCodesForAscii: IntArray = standardAsciiEscapesForCirJSON.apply {
                this['<'.code] = ESCAPE_STANDARD
                this['>'.code] = ESCAPE_STANDARD
            }

            override fun getEscapeSequence(ch: Int): SerializableString? {
                throw UnsupportedOperationException("Not implemented for test")
            }

        }

    }

}