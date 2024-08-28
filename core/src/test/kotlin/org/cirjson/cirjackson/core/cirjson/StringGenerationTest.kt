package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonToken
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class StringGenerationTest : StringGenerationTestBase() {

    @Test
    fun testBasicEscaping() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (valueIndex in SAMPLES.indices) {
                    basicEscaping(generatorMode, parserMode, valueIndex, true)
                    basicEscaping(generatorMode, parserMode, valueIndex, false)
                }
            }
        }
    }

    private fun basicEscaping(generatorMode: Int, parserMode: Int, valueIndex: Int, useChars: Boolean) {
        val value = SAMPLES[valueIndex]
        val generator = createGenerator(generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())

        if (useChars) {
            val ch = CharArray(value.length + valueIndex)
            value.toCharArray(ch, valueIndex, 0, value.length)
            generator.writeString(ch, valueIndex, value.length)
        } else {
            generator.writeString(value)
        }

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
                    mediumStrings(generatorMode, parserMode, size, true)
                    mediumStrings(generatorMode, parserMode, size, false)
                }
            }
        }
    }

    private fun mediumStrings(generatorMode: Int, parserMode: Int, size: Int, useChars: Boolean) {
        val text = generateMediumText(size)
        val generator = createGenerator(generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())

        if (useChars) {
            val ch = CharArray(text.length)
            text.toCharArray(ch, 0, 0, text.length)
            generator.writeString(ch, 0, text.length)
        } else {
            generator.writeString(text)
        }

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

    @Test
    fun testLongerRandomSingleChunk() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (size in 0..<80) {
                    val text = generateRandom(size + 75000)
                    longerRandomSingleChunk(generatorMode, parserMode, text, true)
                    longerRandomSingleChunk(generatorMode, parserMode, text, false)
                }
            }
        }
    }

    private fun longerRandomSingleChunk(generatorMode: Int, parserMode: Int, text: String, useChars: Boolean) {
        val generator = createGenerator(generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())

        if (useChars) {
            val ch = CharArray(text.length)
            text.toCharArray(ch, 0, 0, text.length)
            generator.writeString(ch, 0, text.length)
        } else {
            generator.writeString(text)
        }

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
                    longerRandomMultiChunk(generatorMode, parserMode, text, true)
                    longerRandomMultiChunk(generatorMode, parserMode, text, false)
                }
            }
        }
    }

    private fun longerRandomMultiChunk(generatorMode: Int, parserMode: Int, text: String, useChars: Boolean) {
        val generator = createGenerator(generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())

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

            if (useChars) {
                val ch = CharArray(length)
                text.toCharArray(ch, 0, offset, offset + length)
                generator.writeString(ch, 0, length)
            } else {
                generator.writeString(text.substring(offset, offset + length))
            }

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

    @Test
    fun testNamingStrategy() {
        for (mode in ALL_GENERATOR_MODES) {
            namingStrategy(mode, true)
            namingStrategy(mode, false)
        }
    }

    private fun namingStrategy(mode: Int, useChars: Boolean) {
        val stringBuilder = StringBuilder(8000)
        stringBuilder.append('"')

        for (i in 0..<7988) {
            stringBuilder.append("a")
        }

        stringBuilder.append('"')
        val generator = createGenerator(mode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        val text = stringBuilder.toString()

        if (useChars) {
            val ch = CharArray(text.length)
            text.toCharArray(ch, 0, 0, text.length)
            generator.writeString(ch, 0, text.length)
            generator.writeString(charArrayOf('b'), 0, 1)
            generator.writeString(charArrayOf('c'), 0, 1)
        } else {
            generator.writeString(text)
            generator.writeString("b")
            generator.writeString("c")
        }

        generator.writeEndArray()
        generator.close()
    }

}