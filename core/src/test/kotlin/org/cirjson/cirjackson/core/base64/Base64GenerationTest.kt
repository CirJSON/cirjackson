package org.cirjson.cirjackson.core.base64

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.support.ThrottledInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class Base64GenerationTest : TestBase() {

    @Test
    fun testStreamingBinaryWrites() {
        streamingWrites(true)
        streamingWrites(false)
    }

    private fun streamingWrites(useBytes: Boolean) {
        val input = TEXT4.toByteArray(Charsets.UTF_8)

        for (variant in BASE64_VARIANTS) {
            val expected = "[\"0\",${quote(variant.encode(input))}]"

            for (passLength in booleanArrayOf(true, false)) {
                for (chunkSize in intArrayOf(1, 2, 3, 4, 7, 11, 29, 5000)) {
                    val bytes = ByteArrayOutputStream()

                    val generator = if (useBytes) {
                        CIRJSON_FACTORY.createGenerator(ObjectWriteContext.empty(), bytes)
                    } else {
                        CIRJSON_FACTORY.createGenerator(ObjectWriteContext.empty(),
                                OutputStreamWriter(bytes, Charsets.UTF_8))
                    }

                    generator.writeStartArray()
                    generator.writeArrayId(intArrayOf())
                    val length = if (passLength) input.size else -1
                    val data = ThrottledInputStream(input, chunkSize)
                    generator.writeBinary(variant, data, length)
                    generator.writeEndArray()
                    generator.close()
                    val actual = bytes.toString(Charsets.UTF_8)
                    assertEquals(expected, actual)
                }
            }
        }
    }

    @Test
    fun testSimple() {
        val bytes = ByteArrayOutputStream()

        var generator = CIRJSON_FACTORY.createGenerator(ObjectWriteContext.empty(), bytes)
        var data = ByteArrayInputStream(ByteArray(2000))
        generator.writeBinary(data, 1999)
        generator.close()

        val expected = 2670

        assertEquals(expected, bytes.size())

        val writer = StringWriter()

        generator = CIRJSON_FACTORY.createGenerator(ObjectWriteContext.empty(), writer)
        data = ByteArrayInputStream(ByteArray(2000))
        generator.writeBinary(data, 1999)
        generator.close()

        assertEquals(expected, writer.toString().length)
    }

    @Test
    fun testSimpleBinaryWrite() {
        simpleBinaryWrite(true)
        simpleBinaryWrite(false)
    }

    private fun simpleBinaryWrite(useBytes: Boolean) {
        val variant = Base64Variants.defaultVariant

        for (i in 0..<3) {
            val output = ByteArrayOutputStream(200)
            val generator = if (useBytes) {
                CIRJSON_FACTORY.createGenerator(ObjectWriteContext.empty(), output, CirJsonEncoding.UTF8)
            } else {
                CIRJSON_FACTORY.createGenerator(ObjectWriteContext.empty(), OutputStreamWriter(output, Charsets.UTF_8))
            }

            when (i) {
                0 -> {
                    generator.writeBinary(variant, WIKIPEDIA_BASE64_AS_BYTES, 0, WIKIPEDIA_BASE64_AS_BYTES.size)
                }

                1 -> {
                    generator.writeStartArray()
                    generator.writeArrayId(Any())
                    generator.writeBinary(variant, WIKIPEDIA_BASE64_AS_BYTES, 0, WIKIPEDIA_BASE64_AS_BYTES.size)
                    generator.writeEndArray()
                }

                2 -> {
                    generator.writeStartObject()
                    generator.writeObjectId(Any())
                    generator.writeName("field")
                    generator.writeBinary(variant, WIKIPEDIA_BASE64_AS_BYTES, 0, WIKIPEDIA_BASE64_AS_BYTES.size)
                    generator.writeEndObject()
                }
            }

            generator.close()

            for (mode in ALL_PARSER_MODES) {
                val parser = createParser(mode, output.toString(Charsets.UTF_8))

                when (i) {
                    1 -> {
                        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
                        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
                    }

                    2 -> {
                        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
                        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
                        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
                        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
                    }
                }

                assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
                val actual = parser.text
                parser.close()
                assertEquals(WIKIPEDIA_BASE64_ENCODED, actual)
            }
        }
    }

    @Test
    fun testBinaryAsEmbeddedObject() {
        val writer = StringWriter()
        var generator = CIRJSON_FACTORY.createGenerator(ObjectWriteContext.empty(), writer)
        generator.writeEmbeddedObject(WIKIPEDIA_BASE64_AS_BYTES)
        generator.close()
        assertEquals(quote(WIKIPEDIA_BASE64_ENCODED), writer.toString())

        val bytes = ByteArrayOutputStream(100)
        generator = CIRJSON_FACTORY.createGenerator(ObjectWriteContext.empty(), bytes)
        generator.writeEmbeddedObject(WIKIPEDIA_BASE64_AS_BYTES)
        generator.close()
        assertEquals(quote(WIKIPEDIA_BASE64_ENCODED), bytes.toString(Charsets.UTF_8))
    }

    companion object {

        const val WIKIPEDIA_BASE64_TEXT =
                "Man is distinguished, not only by his reason, but by this singular passion from other animals, which is a lust of the mind, that by a perseverance of delight in the continued and indefatigable generation of knowledge, exceeds the short vehemence of any carnal pleasure."

        val WIKIPEDIA_BASE64_AS_BYTES = WIKIPEDIA_BASE64_TEXT.toByteArray(Charsets.US_ASCII)

        const val WIKIPEDIA_BASE64_ENCODED =
                "TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ1dCBieSB0aGlzIHNpbmd1bGFyIHBhc3Npb24gZnJvbSBvdGhlciBhbmltYWxzLCB3aGljaCBpcyBhIGx1c3Qgb2YgdGhlIG1pbmQsIHRoYXQgYnkgYSBwZXJzZXZlcmFuY2Ugb2YgZGVsaWdodCBpbiB0aGUgY29udGludWVkIGFuZCBpbmRlZmF0aWdhYmxlIGdlbmVyYXRpb24gb2Yga25vd2xlZGdlLCBleGNlZWRzIHRoZSBzaG9ydCB2ZWhlbWVuY2Ugb2YgYW55IGNhcm5hbCBwbGVhc3VyZS4="

        private const val TEXT =
                "Some content so that we can test encoding of base64 data; must be long enough include a line wrap or two..."

        private const val TEXT4 = TEXT + TEXT + TEXT + TEXT

    }

}