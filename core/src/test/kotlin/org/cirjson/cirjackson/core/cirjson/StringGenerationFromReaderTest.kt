package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import java.io.StringReader
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class StringGenerationFromReaderTest : TestBase() {

    @Test
    fun testBasicEscaping() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (value in SAMPLES) {
                    basicEscaping(generatorMode, parserMode, value)
                }
            }
        }
    }

    private fun basicEscaping(generatorMode: Int, parserMode: Int, value: String) {
        val generator = createGenerator(generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        val reader = StringReader(value)
        generator.writeString(reader, -1)
        generator.writeEndArray()
        generator.close()
        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(value, parser.text)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testMediumStrings() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (size in SIZES) {
                    mediumStrings(generatorMode, parserMode, size)
                }
            }
        }
    }

    private fun mediumStrings(generatorMode: Int, parserMode: Int, size: Int) {
        val text = generateMediumText(size)
        val generator = createGenerator(generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        val reader = StringReader(text)
        generator.writeString(reader, -1)
        generator.writeEndArray()
        generator.close()
        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(text, parser.text)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    private fun generateMediumText(size: Int): String {
        val stringBuilder = StringBuilder(size + 1000)
        val random = Random(size.toLong())

        do {
            when (random.nextInt(4)) {
                0 -> stringBuilder.append(" foo")
                1 -> stringBuilder.append(" bar")
                2 -> stringBuilder.append(stringBuilder.length)
                3 -> stringBuilder.append(" \"stuff\"")
            }
        } while (stringBuilder.length < size)

        return stringBuilder.toString()
    }

    @Test
    fun testLongerRandomSingleChunk() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (size in 0..<80) {
                    val text = generateRandom(size + 75000)
                    longerRandomSingleChunk(generatorMode, parserMode, text)
                }
            }
        }
    }

    private fun longerRandomSingleChunk(generatorMode: Int, parserMode: Int, text: String) {
        val generator = createGenerator(generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        val reader = StringReader(text)
        generator.writeString(reader, -1)
        generator.writeEndArray()
        generator.close()
        val doc = generator.streamWriteOutputTarget!!.toString().toByteArray()
        val parser = createParser(parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val actual = parser.text!!

        if (text != actual) {
            if (text.length != actual.length) {
                fail("Expected string length " + text.length + ", actual " + actual.length)
            }

            var i = -1

            while (++i < actual.length) {
                if (text[i] != actual[i]) {
                    break
                }
            }

            fail("Strings differ at position #$i (len ${text.length}): expected char 0x${
                Integer.toHexString(text[i].code)
            }, actual 0x${Integer.toHexString(actual[i].code)}")
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testLongerRandomMultiChunk() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (size in 0..<70) {
                    val text = generateRandom(size + 73000)
                    longerRandomMultiChunk(generatorMode, parserMode, text)
                }
            }
        }
    }

    private fun longerRandomMultiChunk(generatorMode: Int, parserMode: Int, text: String) {
        val generator = createGenerator(generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        var reader: StringReader

        val random = Random(text.length.toLong())
        var offset = 0

        while (offset < text.length) {
            val shift = 1 + (random.nextInt() and 0xFFFFF).mod(12)
            var length = (1 shl shift) + shift

            if (offset + length >= text.length) {
                length = text.length - offset
            } else {
                val c = text[offset + length - 1].code

                if (c in 0xD800..<0xDC00) {
                    ++length
                }
            }

            reader = StringReader(text.substring(offset, offset + length))
            generator.writeString(reader, -1)
            offset += length
        }

        generator.writeEndArray()
        generator.close()
        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        offset = 0
        while (parser.nextToken() == CirJsonToken.VALUE_STRING) {
            val actual = parser.text!!
            val expected = text.substring(offset, offset + actual.length)

            if (expected != actual) {
                if (expected.length != actual.length) {
                    fail("Expected string length " + expected.length + ", actual " + actual.length)
                }

                var i = -1

                while (++i < actual.length) {
                    if (expected[i] != actual[i]) {
                        break
                    }
                }

                fail("Strings differ at position #$i (len ${expected.length}): expected char 0x${
                    Integer.toHexString(expected[i].code)
                }, actual 0x${Integer.toHexString(actual[i].code)}")
            }

            offset += actual.length
        }

        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    private fun generateRandom(size: Int): String {
        val stringBuilder = StringBuilder(size + 1000)
        val random = Random(size.toLong())

        for (i in 0..<size) {
            if (random.nextBoolean()) {
                var value = random.nextInt() and 0xFFFF

                if (value in 0xD800..0xDFFF) {
                    val fullValue = random.nextInt() and 0xFFFFF
                    stringBuilder.append((0xD800 + (fullValue shr 10)).toChar())
                    value = 0xDC00 + (fullValue and 0x3FF)
                }

                stringBuilder.append(value.toChar())
            } else {
                stringBuilder.append((random.nextInt() and 0x7F).toChar())
            }
        }

        return stringBuilder.toString()
    }

    @Test
    fun testNamingStrategy() {
        for (mode in ALL_GENERATOR_MODES) {
            namingStrategy(mode)
        }
    }

    private fun namingStrategy(mode: Int) {
        val stringBuilder = StringBuilder(8000)
        stringBuilder.append('"')

        for (i in 0..<7988) {
            stringBuilder.append("a")
        }

        stringBuilder.append('"')
        val generator = createGenerator(mode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeString(StringReader(stringBuilder.toString()), -1)
        generator.writeString(StringReader("b"), -1)
        generator.writeString(StringReader("c"), -1)
        generator.writeEndArray()
        generator.close()
    }

    companion object {

        private val SIZES = intArrayOf(1100, 2300, 3800, 7500, 19000, 33333)

        private val SAMPLES = arrayOf("\"test\"", "\n", "\\n", "\r\n", "a\\b", "tab:\nok?",
                "a\tb\tc\n\u000cdef\t \tg\"\"\"h\"\\ijklmn\b", "\"\"\"", "\\r)'\"",
                "Longer text & other stuff:\twith some\r\n\r\n random linefeeds etc added in to cause some \"special\" handling \\\\ to occur...\n")

    }

}