package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AsyncUnicodeHandlingTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testShortUnicodeWithSurrogates() {
        val lengths = intArrayOf(28, 53)

        for (mode in ALL_ASYNC_MODES) {
            for (length in lengths) {
                unicodeWithSurrogates(mode, 0, length, 99)
                unicodeWithSurrogates(mode, 0, length, 5)
                unicodeWithSurrogates(mode, 0, length, 3)
                unicodeWithSurrogates(mode, 0, length, 2)
                unicodeWithSurrogates(mode, 0, length, 1)

                unicodeWithSurrogates(mode, 1, length, 99)
                unicodeWithSurrogates(mode, 1, length, 3)
                unicodeWithSurrogates(mode, 1, length, 1)
            }
        }
    }

    @Test
    fun testLongUnicodeWithSurrogates() {
        val lengths = intArrayOf(230, 700, 9600)

        for (mode in ALL_ASYNC_MODES) {
            for (length in lengths) {
                unicodeWithSurrogates(mode, 0, length, Int.MAX_VALUE)
                unicodeWithSurrogates(mode, 0, length, 99)
                unicodeWithSurrogates(mode, 0, length, 5)
                unicodeWithSurrogates(mode, 0, length, 3)
                unicodeWithSurrogates(mode, 0, length, 2)
                unicodeWithSurrogates(mode, 0, length, 1)

                unicodeWithSurrogates(mode, 1, length, Int.MAX_VALUE)
                unicodeWithSurrogates(mode, 1, length, 99)
                unicodeWithSurrogates(mode, 1, length, 3)
                unicodeWithSurrogates(mode, 1, length, 1)
            }
        }
    }

    private fun unicodeWithSurrogates(mode: Int, padding: Int, length: Int, bytesPerFeed: Int) {
        val text = createDocText(length)
        val quoted = quote(text)
        var doc = utf8Bytes(quoted)

        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(text, parser.currentText())
        assertNull(parser.nextToken())
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        doc = utf8Bytes("{\"__cirJsonId__\":\"root\",$quoted:true}")
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(text, parser.currentName, "length: $length, bytesPerFeed: $bytesPerFeed")
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    private fun createDocText(length: Int): String {
        val stringBuilder = StringBuilder()

        while (stringBuilder.length < length) {
            stringBuilder.append(SURROGATE_CHARS)
            stringBuilder.append(stringBuilder.length)

            if (stringBuilder.length and 1 == 1) {
                stringBuilder.append('\u00A3')
            } else {
                stringBuilder.append('\u3800')
            }
        }

        return stringBuilder.toString()
    }

    companion object {

        private const val SURROGATE_CHARS = "\uD834\uDD1E"

    }

}