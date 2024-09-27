package org.cirjson.cirjackson.core.read.location

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationInArrayTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testLocationInArray() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            if (mode == MODE_DATA_INPUT) {
                continue
            }

            locationInArray(mode)
        }
    }

    private fun locationInArray(mode: Int) {
        val useBytes = mode in ALL_BINARY_PARSER_MODES
        val doc = "  [\"root\", 10, 251,\n   3  ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertLocation(useBytes, parser.currentTokenLocation(), 2L, 1, 3)
        assertLocation(useBytes, parser.currentLocation(), 3L, 1, 4)

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertLocation(useBytes, parser.currentTokenLocation(), 3L, 1, 4)
        assertLocation(useBytes, parser.currentLocation(), 4L, 1, 5)
        assertEquals("root", parser.text)
        assertLocation(useBytes, parser.currentTokenLocation(), 3L, 1, 4)
        assertLocation(useBytes, parser.currentLocation(), 9L, 1, 10)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertLocation(useBytes, parser.currentTokenLocation(), 11L, 1, 12)
        assertLocation(useBytes, parser.currentLocation(), 13L, 1, 14)
        assertEquals(10, parser.intValue)
        assertLocation(useBytes, parser.currentTokenLocation(), 11L, 1, 12)
        assertLocation(useBytes, parser.currentLocation(), 13L, 1, 14)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertLocation(useBytes, parser.currentTokenLocation(), 15L, 1, 16)
        assertLocation(useBytes, parser.currentLocation(), 18L, 1, 19)
        assertEquals(251, parser.intValue)
        assertLocation(useBytes, parser.currentTokenLocation(), 15L, 1, 16)
        assertLocation(useBytes, parser.currentLocation(), 18L, 1, 19)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertLocation(useBytes, parser.currentTokenLocation(), 23L, 2, 4)
        assertLocation(useBytes, parser.currentLocation(), 24L, 2, 5)
        assertEquals(3, parser.intValue)
        assertLocation(useBytes, parser.currentTokenLocation(), 23L, 2, 4)
        assertLocation(useBytes, parser.currentLocation(), 24L, 2, 5)

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertLocation(useBytes, parser.currentTokenLocation(), 26L, 2, 7)
        assertLocation(useBytes, parser.currentLocation(), 27L, 2, 8)

        parser.close()
    }

    private fun assertLocation(useBytes: Boolean, location: CirJsonLocation, offset: Long, line: Int, column: Int) {
        assertEquals(line, location.lineNumber, "lineNumber")
        assertEquals(column, location.columnNumber, "columnNumber")

        if (useBytes) {
            assertEquals(offset, location.byteOffset, "byteOffset")
        } else {
            assertEquals(offset, location.charOffset, "charOffset")
        }
    }

}