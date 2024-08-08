package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CirJsonStringEncoderTest : TestBase() {

    @Test
    fun testQuoteAsCharArray() {
        var result = CirJsonStringEncoder.quoteAsCharArray("foobar")
        assertContentEquals("foobar".toCharArray(), result)
        result = CirJsonStringEncoder.quoteAsCharArray("\"x\"")
        assertContentEquals("\\\"x\\\"".toCharArray(), result)
    }

    @Test
    fun testQuoteCharSequenceAsString() {
        val output = StringBuilder()
        val builder = StringBuilder()
        builder.append("foobar")
        CirJsonStringEncoder.quoteAsString(builder, output)
        assertEquals("foobar", output.toString())
        builder.setLength(0)
        output.setLength(0)
        builder.append("\"x\"")
        CirJsonStringEncoder.quoteAsString(builder, output)
        assertEquals("\\\"x\\\"", output.toString())
    }

    @Test
    fun testQuoteLongAsString() {
        val stringBuilder = StringBuilder()
        val stringBuilder2 = StringBuilder()

        for (i in 1..1111) {
            stringBuilder.append('"')
            stringBuilder2.append("\\\"")
        }

        val input = stringBuilder.toString()
        val expected = stringBuilder2.toString()
        val result = CirJsonStringEncoder.quoteAsCharArray(input)
        assertEquals(2 * input.length, result.size)
        assertEquals(expected, String(result))
    }

    @Test
    fun testQuoteLongCharSequenceAsString() {
        val output = StringBuilder()
        val stringBuilder = StringBuilder()
        val stringBuilder2 = StringBuilder()

        for (i in 1..1111) {
            stringBuilder.append('"')
            stringBuilder2.append("\\\"")
        }

        val expected = stringBuilder2.toString()
        CirJsonStringEncoder.quoteAsString(stringBuilder, output)
        assertEquals(2 * stringBuilder.length, output.length)
        assertEquals(expected, output.toString())
    }

    @Test
    fun testQuoteAsUTF8() {
        val factory = CirJsonFactory()
        val lengths = intArrayOf(5, 19, 200, 7000, 21000, 37000)

        for (length in lengths) {
            val string = generateRandom(length)
            val stringWriter = StringWriter(length * 2)
            val generator = factory.createGenerator(ObjectWriteContext.empty(), stringWriter)
            generator.writeString(string)
            generator.close()
            var encoded = stringWriter.toString()
            encoded = encoded.substring(1, encoded.length - 1)
            val expected = encoded.toByteArray(StandardCharsets.UTF_8)
            val actual = CirJsonStringEncoder.quoteAsUTF8(string)
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun testEncodeAsUTF8() {
        val strings = arrayOf("a", "foobar", "p\u00f6ll\u00f6", "\"foo\"", generateRandom(200), generateRandom(5000),
                generateRandom(39000))

        for (string in strings) {
            assertContentEquals(string.toByteArray(StandardCharsets.UTF_8), CirJsonStringEncoder.encodeAsUTF8(string))
        }
    }

    @Test
    fun testControlChars() {
        val input = charArrayOf(0.toChar(), 1.toChar(), 2.toChar(), 3.toChar(), 4.toChar())
        val quoted = CirJsonStringEncoder.quoteAsCharArray(String(input))
        assertEquals("\\u0000\\u0001\\u0002\\u0003\\u0004", String(quoted))
    }

    @Test
    fun testSequenceWithControlChars() {
        val input = charArrayOf(0.toChar(), 1.toChar(), 2.toChar(), 3.toChar(), 4.toChar())
        val builder = StringBuilder()
        builder.append(input)
        val output = StringBuilder()
        CirJsonStringEncoder.quoteAsString(builder, output)
        assertEquals("\\u0000\\u0001\\u0002\\u0003\\u0004", output.toString())
    }

    @Test
    fun testByteBufferDefaultSize() {
        assertEquals(CirJsonStringEncoder.MIN_BYTE_BUFFER_SIZE, CirJsonStringEncoder.initialByteBufferSize(1))
        assertEquals(CirJsonStringEncoder.MIN_BYTE_BUFFER_SIZE, CirJsonStringEncoder.initialByteBufferSize(11))

        assertEquals(36, CirJsonStringEncoder.initialByteBufferSize(20))
        assertEquals(73, CirJsonStringEncoder.initialByteBufferSize(45))
        assertEquals(1506, CirJsonStringEncoder.initialByteBufferSize(1000))
        assertEquals(9006, CirJsonStringEncoder.initialByteBufferSize(6000))

        assertEquals(CirJsonStringEncoder.MAX_BUFFER_SIZE,
                CirJsonStringEncoder.initialByteBufferSize(CirJsonStringEncoder.MAX_BUFFER_SIZE + 1))
        assertEquals(CirJsonStringEncoder.MAX_BUFFER_SIZE, CirJsonStringEncoder.initialByteBufferSize(999999))
    }

    @Test
    fun testCharBufferDefaultSize() {
        assertEquals(CirJsonStringEncoder.MIN_CHAR_BUFFER_SIZE, CirJsonStringEncoder.initialCharBufferSize(1))
        assertEquals(CirJsonStringEncoder.MIN_CHAR_BUFFER_SIZE, CirJsonStringEncoder.initialCharBufferSize(8))

        assertEquals(62, CirJsonStringEncoder.initialCharBufferSize(50))
        assertEquals(118, CirJsonStringEncoder.initialCharBufferSize(100))
        assertEquals(1131, CirJsonStringEncoder.initialCharBufferSize(1000))
        assertEquals(9000, CirJsonStringEncoder.initialCharBufferSize(8000))

        assertEquals(CirJsonStringEncoder.MAX_BUFFER_SIZE,
                CirJsonStringEncoder.initialCharBufferSize(CirJsonStringEncoder.MAX_BUFFER_SIZE + 1))
        assertEquals(CirJsonStringEncoder.MAX_BUFFER_SIZE, CirJsonStringEncoder.initialCharBufferSize(900000))
    }

    private fun generateRandom(length: Int): String {
        val stringBuilder = StringBuilder()
        val random = Random(length.toLong())

        for (i in 0..<length) {
            val ch = random.nextInt(0xCFFF).toChar()
            stringBuilder.append(ch)
        }

        return stringBuilder.toString()
    }

}