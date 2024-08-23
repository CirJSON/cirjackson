package org.cirjson.cirjackson.core.base64

import org.cirjson.cirjackson.core.Base64Variants
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamReadException
import java.io.ByteArrayOutputStream
import kotlin.test.*

class Base64BinaryParsingTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testBase64Text() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_PARSER_MODES) {
                base64Text(generatorMode, parserMode)
            }
        }
    }

    private fun base64Text(generatorMode: Int, parserMode: Int) {
        for (length in LENGTHS) {
            val input = ByteArray(length) { it.toByte() }

            for (variant in BASE64_VARIANTS) {
                val generator = createGenerator(factory, generatorMode)
                generator.writeBinary(variant, input, 0, length)
                generator.close()

                val parser = createParser(factory, parserMode, generator.streamWriteOutputTarget!!.toString())
                assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

                if (length and 1 == 0) {
                    assertNotNull(parser.text)
                }

                val data = try {
                    parser.getBinaryValue(variant)
                } catch (e: Exception) {
                    throw RuntimeException("Failed (variant $variant, data length $length): ${e.message}", e)
                }

                assertNotNull(data)
                assertContentEquals(data, input)

                if (parserMode in ALL_ASYNC_PARSER_MODES) {
                    assertToken(CirJsonToken.NOT_AVAILABLE, parser.nextToken())
                } else if (parserMode != MODE_DATA_INPUT) {
                    assertNull(parser.nextToken())
                }

                parser.close()
            }

        }
    }

    @Test
    fun testStreaming() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_PARSER_MODES) {
                streaming(generatorMode, parserMode)
            }
        }
    }

    private fun streaming(generatorMode: Int, parserMode: Int) {
        for (size in SIZES) {
            val data = generateData(size)
            val generator = createGenerator(factory, generatorMode)
            generator.writeStartObject()
            generator.writeObjectId(Any())
            generator.writeName("b")
            generator.writeBinary(data)
            generator.writeEndObject()
            generator.close()

            val parser = createParser(factory, parserMode, generator.streamWriteOutputTarget!!.toString())
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("b", parser.currentName)
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            val result = ByteArrayOutputStream(size)
            val gotten = parser.readBinaryValue(result)
            assertEquals(size, gotten)
            assertContentEquals(data, result.toByteArray())
            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

            if (parserMode in ALL_ASYNC_PARSER_MODES) {
                assertToken(CirJsonToken.NOT_AVAILABLE, parser.nextToken())
            } else if (parserMode != MODE_DATA_INPUT) {
                assertNull(parser.nextToken())
            }

            parser.close()
        }
    }

    private fun generateData(size: Int): ByteArray {
        return ByteArray(size) { it.mod(255).toByte() }
    }

    @Test
    fun testSimple() {
        for (mode in ALL_PARSER_MODES) {
            for (leadingWhitespace in booleanArrayOf(true, false)) {
                for (trailingWhitespace in booleanArrayOf(true, false)) {
                    simple(mode, leadingWhitespace, trailingWhitespace)
                }
            }
        }
    }

    private fun simple(mode: Int, leadingWhitespace: Boolean, trailingWhitespace: Boolean) {
        val doc = generateDoc(leadingWhitespace, trailingWhitespace)
        val parser = createParser(factory, mode, doc)

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val data = parser.binaryValue
        assertNotNull(data)
        assertContentEquals(WIKIPEDIA_BASE64_AS_BYTES, data)
        parser.close()
    }

    private fun generateDoc(leadingWhitespace: Boolean, trailingWhitespace: Boolean): String {
        var input = WIKIPEDIA_BASE64_ENCODED

        if (leadingWhitespace) {
            input = "   $input"
        }

        if (trailingWhitespace) {
            input = "$input   "
        }

        return quote(input)
    }

    @Test
    fun testInArray() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_PARSER_MODES) {
                inArray(generatorMode, parserMode)
            }
        }
    }

    private fun inArray(generatorMode: Int, parserMode: Int) {
        val entryCount = 7

        val generator = createGenerator(factory, generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())

        val entries = Array(entryCount) { i ->
            val bytes = ByteArray(i * 100 + 200) { x ->
                (i + x).toByte()
            }

            generator.writeBinary(bytes)
            bytes
        }

        generator.writeEndArray()
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (entry in entries) {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            val bytes = parser.binaryValue
            assertContentEquals(entry, bytes)
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testWithEscaped() {
        for (mode in ALL_PARSER_MODES) {
            withEscaped(mode)
        }
    }

    private fun withEscaped(mode: Int) {
        var doc = quote("VGVz\\ndCE=")
        var parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        var bytes = parser.binaryValue
        assertEquals("Test!", String(bytes, Charsets.US_ASCII))

        if (mode in ALL_ASYNC_PARSER_MODES) {
            assertToken(CirJsonToken.NOT_AVAILABLE, parser.nextToken())
        } else if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()

        doc = quote("VGVz\\ndCE=")
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        bytes = parser.binaryValue
        assertEquals("Test!", String(bytes, Charsets.US_ASCII))

        if (mode in ALL_ASYNC_PARSER_MODES) {
            assertToken(CirJsonToken.NOT_AVAILABLE, parser.nextToken())
        } else if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()
    }

    @Test
    fun testWithEscapedPadding() {
        for (mode in ALL_PARSER_MODES) {
            withEscapedPadding(mode)
        }
    }

    private fun withEscapedPadding(mode: Int) {
        var doc = quote("VGVzdCE\\u003d")

        var parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("Test!", String(parser.binaryValue, Charsets.US_ASCII))

        if (mode in ALL_ASYNC_PARSER_MODES) {
            assertToken(CirJsonToken.NOT_AVAILABLE, parser.nextToken())
        } else if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()

        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("Test!", String(readBinary(parser), Charsets.US_ASCII))

        if (mode in ALL_ASYNC_PARSER_MODES) {
            assertToken(CirJsonToken.NOT_AVAILABLE, parser.nextToken())
        } else if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()

        doc = quote("WA\\u003D\\u003D")

        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("X", String(parser.binaryValue, Charsets.US_ASCII))

        if (mode in ALL_ASYNC_PARSER_MODES) {
            assertToken(CirJsonToken.NOT_AVAILABLE, parser.nextToken())
        } else if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()

        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("X", String(readBinary(parser), Charsets.US_ASCII))

        if (mode in ALL_ASYNC_PARSER_MODES) {
            assertToken(CirJsonToken.NOT_AVAILABLE, parser.nextToken())
        } else if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()
    }

    private fun readBinary(parser: CirJsonParser): ByteArray {
        val bytes = ByteArrayOutputStream()
        parser.readBinaryValue(bytes)
        return bytes.toByteArray()
    }

    @Test
    fun testInvalidTokenForBase64() {
        for (mode in ALL_PARSER_MODES) {
            invalidTokenForBase64(mode)
        }
    }

    private fun invalidTokenForBase64(mode: Int) {
        val parser = createParser(factory, mode, "[ \"root\" ]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        try {
            parser.binaryValue
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, "current token")
            verifyException(e, "can not access as binary")
        }

        parser.close()
    }

    @Test
    fun testInvalidChar() {
        for (mode in ALL_PARSER_MODES) {
            invalidChar(mode)
        }
    }

    private fun invalidChar(mode: Int) {
        var parser = createParser(factory, mode, quote("a==="))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.getBinaryValue(Base64Variants.MIME)
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, "padding only legal")
        }

        parser.close()

        parser = createParser(factory, mode, quote("ab de"))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.getBinaryValue(Base64Variants.MIME)
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, "illegal white space")
        }

        parser.close()

        parser = createParser(factory, mode, quote("ab#?"))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.getBinaryValue(Base64Variants.MIME)
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, "illegal character '#'")
        }

        parser.close()
    }

    @Test
    fun testOkMissingPadding() {
        val input1 = byteArrayOf(0xAD.toByte())
        val input2 = byteArrayOf(0xAC.toByte(), 0xDC.toByte())

        for (mode in ALL_PARSER_MODES) {
            okMissingPadding(mode, input1)
            okMissingPadding(mode, input2)
        }
    }

    private fun okMissingPadding(mode: Int, input: ByteArray) {
        val base64Variant = Base64Variants.MODIFIED_FOR_URL
        val encoded = base64Variant.encode(input, false)
        val parser = createParser(factory, mode, quote(encoded))
        assertEquals(input.size + 1, encoded.length)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val actual = parser.getBinaryValue(base64Variant)
        assertContentEquals(input, actual)
        parser.close()
    }

    @Test
    fun testFailDueToMissingPadding() {
        val doc1 = quote("fQ")
        val doc2 = quote("A/A")

        for (mode in ALL_PARSER_MODES) {
            failDueToMissingPadding(mode, doc1)
            failDueToMissingPadding(mode, doc2)
        }
    }

    private fun failDueToMissingPadding(mode: Int, doc: String) {
        val exceptionMatch = "Unexpected end of base64-encoded String: base64 variant 'MIME' expects padding"

        var parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.getBinaryValue(Base64Variants.MIME)
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, exceptionMatch)
        }

        parser.close()

        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        parser.text

        try {
            parser.getBinaryValue(Base64Variants.MIME)
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, exceptionMatch)
        }

        parser.close()
    }

    companion object {

        private val LENGTHS = intArrayOf(1, 2, 3, 4, 7, 9, 32, 33, 34, 35)

        private val SIZES = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 12, 100, 350, 1900, 6000, 19000, 65000, 139000)

        val WIKIPEDIA_BASE64_AS_BYTES = Base64GenerationTest.WIKIPEDIA_BASE64_AS_BYTES

        const val WIKIPEDIA_BASE64_ENCODED = Base64GenerationTest.WIKIPEDIA_BASE64_ENCODED

    }

}