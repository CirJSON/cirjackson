package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import java.io.ByteArrayOutputStream
import kotlin.test.*

class AsyncStringArrayTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testLongAsciiStringsSmallLimit() {
        val values = arrayOf(LONG_ASCII, LONG_ASCII, LONG_ASCII, LONG_ASCII, LONG_ASCII)
        val doc = createDoc(values)
        val factory = CirJsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(100).build()).build()

        for (mode in ALL_ASYNC_MODES) {
            longAsciiStringsSmallLimit(factory, doc, values, mode, 0, 9000)
            longAsciiStringsSmallLimit(factory, doc, values, mode, 0, 99)
            longAsciiStringsSmallLimit(factory, doc, values, mode, 0, 5)
            longAsciiStringsSmallLimit(factory, doc, values, mode, 0, 3)
            longAsciiStringsSmallLimit(factory, doc, values, mode, 0, 2)
            longAsciiStringsSmallLimit(factory, doc, values, mode, 0, 1)

            longAsciiStringsSmallLimit(factory, doc, values, mode, 1, 9000)
            longAsciiStringsSmallLimit(factory, doc, values, mode, 1, 99)
            longAsciiStringsSmallLimit(factory, doc, values, mode, 1, 3)
            longAsciiStringsSmallLimit(factory, doc, values, mode, 1, 1)
        }
    }

    private fun longAsciiStringsSmallLimit(factory: CirJsonFactory, doc: ByteArray, values: Array<String>, mode: Int,
            padding: Int, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            for (i in values.indices) {
                parser.nextToken()
                parser.currentText()
            }

            fail("Should have thrown an exception")
        } catch (e: StreamConstraintsException) {
            verifyException(e, "String value length")
            verifyException(e, "exceeds the maximum allowed (100")
        }

        parser.close()
    }

    @Test
    fun testShortAsciiStrings() {
        val values = arrayOf("Test", "", "1",
                "$STRING_0_TO_9$STRING_0_TO_9$STRING_0_TO_9$STRING_0_TO_9$STRING_0_TO_9$STRING_0_TO_9$STRING_0_TO_9",
                "Test", "124")
        val doc = createDoc(values)

        for (mode in ALL_ASYNC_MODES) {
            stringArray(doc, values, mode, 0, 99)
            stringArray(doc, values, mode, 0, 5)
            stringArray(doc, values, mode, 0, 3)
            stringArray(doc, values, mode, 0, 2)
            stringArray(doc, values, mode, 0, 1)

            stringArray(doc, values, mode, 1, 99)
            stringArray(doc, values, mode, 1, 3)
            stringArray(doc, values, mode, 1, 1)
        }
    }

    @Test
    fun testShortUnicodeStrings() {
        val repeat = "Test: $UNICODE_2_BYTES"
        val values = arrayOf(repeat, "", "$UNICODE_2_BYTES", "$UNICODE_3_BYTES",
                "$STRING_0_TO_9 $UNICODE_2_BYTES $STRING_0_TO_9 $UNICODE_3_BYTES $STRING_0_TO_9", "Test", repeat, "!")
        val doc = createDoc(values)

        for (mode in ALL_ASYNC_MODES) {
            stringArray(doc, values, mode, 0, 99)
            stringArray(doc, values, mode, 0, 5)
            stringArray(doc, values, mode, 0, 3)
            stringArray(doc, values, mode, 0, 2)
            stringArray(doc, values, mode, 0, 1)

            stringArray(doc, values, mode, 1, 99)
            stringArray(doc, values, mode, 1, 3)
            stringArray(doc, values, mode, 1, 1)
        }
    }

    @Test
    fun testLongAsciiStrings() {
        val values =
                arrayOf("$STRING_0_TO_9 $STRING_0_TO_9 ... $STRING_0_TO_9 / $STRING_0_TO_9 $STRING_0_TO_9  $STRING_0_TO_9 $STRING_0_TO_9 ... $STRING_0_TO_9",
                        LONG_ASCII)
        val doc = createDoc(values)

        for (mode in ALL_ASYNC_MODES) {
            stringArray(doc, values, mode, 0, 9000)
            stringArray(doc, values, mode, 0, 99)
            stringArray(doc, values, mode, 0, 5)
            stringArray(doc, values, mode, 0, 3)
            stringArray(doc, values, mode, 0, 2)
            stringArray(doc, values, mode, 0, 1)

            stringArray(doc, values, mode, 1, 9000)
            stringArray(doc, values, mode, 1, 99)
            stringArray(doc, values, mode, 1, 3)
            stringArray(doc, values, mode, 1, 1)
        }
    }

    @Test
    fun testLongUnicodeStrings() {
        val base =
                "$STRING_0_TO_9 $STRING_0_TO_9 $UNICODE_2_BYTES $STRING_0_TO_9 $STRING_0_TO_9$UNICODE_3_BYTES $UNICODE_3_BYTES $STRING_0_TO_9 $UNICODE_3_BYTES $STRING_0_TO_9 $STRING_0_TO_9 $UNICODE_2_BYTES$UNICODE_2_BYTES $STRING_0_TO_9"
        val values = arrayOf(base, "$base.", "$base..", "$base...")
        val doc = createDoc(values)

        for (mode in ALL_ASYNC_MODES) {
            stringArray(doc, values, mode, 0, 9000)
            stringArray(doc, values, mode, 0, 99)
            stringArray(doc, values, mode, 0, 5)
            stringArray(doc, values, mode, 0, 3)
            stringArray(doc, values, mode, 0, 2)
            stringArray(doc, values, mode, 0, 1)

            stringArray(doc, values, mode, 1, 9000)
            stringArray(doc, values, mode, 1, 99)
            stringArray(doc, values, mode, 1, 3)
            stringArray(doc, values, mode, 1, 1)
        }
    }

    private fun stringArray(doc: ByteArray, values: Array<String>, mode: Int, padding: Int, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (value in values) {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertEquals(value, parser.currentText())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    private fun createDoc(values: Array<String>): ByteArray {
        val output = ByteArrayOutputStream(1000)
        val generator = factory.createGenerator(ObjectWriteContext.empty(), output)
        generator.writeStartArray()
        generator.writeArrayId(Any())

        for (value in values) {
            generator.writeString(value)
        }

        generator.writeEndArray()
        generator.close()
        return output.toByteArray()
    }

    companion object {

        private const val STRING_0_TO_9 = "1234567890"

        private val LONG_ASCII = StringBuilder(12000).let {
            for (i in 0..<12000) {
                it.append(('a'.code + i and 0x1F).toChar())
            }

            it.toString()
        }

    }

}