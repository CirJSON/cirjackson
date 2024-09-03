package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import kotlin.test.Test
import kotlin.test.assertEquals

class AsyncLocationTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testLocationOffsets() {
        for (mode in ALL_ASYNC_MODES) {
            locationOffsets(mode, 0, 99)
            locationOffsets(mode, 0, 5)
            locationOffsets(mode, 0, 3)
            locationOffsets(mode, 0, 2)
            locationOffsets(mode, 0, 1)

            locationOffsets(mode, 1, 99)
            locationOffsets(mode, 1, 3)
            locationOffsets(mode, 1, 1)
        }
    }

    private fun locationOffsets(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\",[\"1\",[\"2\"]]]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals(1, parser.parser.currentLocation().byteOffset)
        assertEquals(1, parser.parser.currentTokenLocation().byteOffset)
        assertEquals(1, parser.parser.currentLocation().lineNumber)
        assertEquals(1, parser.parser.currentTokenLocation().lineNumber)
        assertEquals(2, parser.parser.currentLocation().columnNumber)
        assertEquals(1, parser.parser.currentTokenLocation().columnNumber)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals(6, parser.parser.currentLocation().byteOffset)
        assertEquals(6, parser.parser.currentTokenLocation().byteOffset)
        assertEquals(1, parser.parser.currentLocation().lineNumber)
        assertEquals(1, parser.parser.currentTokenLocation().lineNumber)
        assertEquals(7, parser.parser.currentLocation().columnNumber)
        assertEquals(6, parser.parser.currentTokenLocation().columnNumber)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        parser.close()
    }

}