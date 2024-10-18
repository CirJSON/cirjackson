package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonEncoding
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.io.SerializedString
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RawStringWriteTest : TestBase() {

    private val factory = sharedStreamFactory()

    @Test
    fun testWriteRawUTF8String() {
        for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
            writeRawUTF8String(parserMode)
        }
    }

    private fun writeRawUTF8String(parserMode: Int) {
        val strings = generateStrings(Random(28L), 750000, false)
        val output = ByteArrayOutputStream(751287)
        val generator = factory.createGenerator(ObjectWriteContext.empty(), output, CirJsonEncoding.UTF8)
        generator.writeStartArray()
        generator.writeArrayId(Any())

        for (string in strings) {
            generator.writeRawUTF8String(string, 0, string.size)
        }

        generator.writeEndArray()
        generator.close()

        val doc = output.toByteArray()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (expected in strings) {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            val string = parser.text!!
            val actual = string.toByteArray()
            assertEquals(expected.size, actual.size)
            assertContentEquals(expected, actual)
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testWriteUTF8String() {
        for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
            writeUTF8String(parserMode)
        }
    }

    private fun writeUTF8String(parserMode: Int) {
        val strings = generateStrings(Random(28L), 720000, true)
        val output = ByteArrayOutputStream(817832)
        val generator = factory.createGenerator(ObjectWriteContext.empty(), output, CirJsonEncoding.UTF8)
        generator.writeStartArray()
        generator.writeArrayId(Any())

        for (string in strings) {
            generator.writeUTF8String(string, 0, string.size)
            generator.writeRaw('\n')
        }

        generator.writeEndArray()
        generator.close()

        val doc = output.toByteArray()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (expected in strings) {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            val string = parser.text!!
            val actual = string.toByteArray()
            assertEquals(expected.size, actual.size)
            assertContentEquals(expected, actual)
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testWriteRawWithSerializableString() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                writeRawWithSerializableString(generatorMode, parserMode)
            }
        }
    }

    private fun writeRawWithSerializableString(generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeRawValue(SerializedString("\"foo\""))
        generator.writeRawValue(SerializedString("12"))
        generator.writeRaw(SerializedString(", false"))
        generator.writeEndArray()
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("foo", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(12, parser.intValue)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    private fun generateStrings(random: Random, expectedLength: Int, includeControlChars: Boolean): List<ByteArray> {
        var totalLength = expectedLength
        val strings = mutableListOf<ByteArray>()

        while (totalLength > 0) {
            var length = 2
            var bits = random.nextInt(13)

            while (--bits >= 0) {
                length += length
            }

            length = 1 + (length + length) / 3
            val string = generateString(random, length, includeControlChars)
            val bytes = string.toByteArray()
            strings.add(bytes)
            totalLength -= bytes.size
        }

        return strings
    }

    private fun generateString(random: Random, length: Int, includeControlChars: Boolean): String {
        val stringBuilder = StringBuilder(length)

        while (stringBuilder.length < length) {
            val i = when (random.nextInt(3)) {
                0 -> 2048 + random.nextInt(16383)

                1 -> 128 + random.nextInt(1024)

                else -> {
                    var i = random.nextInt(192)

                    if (!includeControlChars) {
                        i += 32

                        if (i == '\\'.code || i == '"'.code) {
                            i = '@'.code
                        }
                    }

                    i
                }
            }

            stringBuilder.append(i.toChar())
        }

        return stringBuilder.toString()
    }

}