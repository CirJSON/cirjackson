package org.cirjson.cirjackson.core.read.location

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocationDuringInputStreamParsingTest : TestBase() {

    @Test
    fun testLocationAtEndOfParse() {
        for (locationTestCase in LocationTestCase.entries) {
            locationAtEndOfParse(MODE_INPUT_STREAM, locationTestCase)
            locationAtEndOfParse(MODE_INPUT_STREAM_THROTTLED, locationTestCase)
        }
    }

    private fun locationAtEndOfParse(mode: Int, locationTestCase: LocationTestCase) {
        val doc = locationTestCase.doc
        val parser = createParser(mode, doc)

        while (parser.nextToken() != null) {
            assertNotNull(parser.currentLocation())
            assertNotNull(parser.currentTokenLocation())
        }

        assertCurrentLocation(parser, locationTestCase.finalLocation)
        parser.close()
    }

    @Test
    fun testInitialLocation() {
        for (locationTestCase in LocationTestCase.entries) {
            initialLocation(MODE_INPUT_STREAM, locationTestCase)
            initialLocation(MODE_INPUT_STREAM_THROTTLED, locationTestCase)
        }
    }

    private fun initialLocation(mode: Int, locationTestCase: LocationTestCase) {
        val doc = locationTestCase.doc
        val parser = createParser(mode, doc)
        val location = parser.currentLocation()
        parser.close()

        assertLocation(location, at(1, 1, 0))
    }

    @Test
    fun testTokenLocation() {
        for (locationTestCase in LocationTestCase.entries) {
            tokenLocation(MODE_INPUT_STREAM, locationTestCase)
            tokenLocation(MODE_INPUT_STREAM_THROTTLED, locationTestCase)
        }
    }

    private fun tokenLocation(mode: Int, locationTestCase: LocationTestCase) {
        val doc = locationTestCase.doc
        val parser = createParser(mode, doc)
        var i = 0

        while (parser.nextToken() != null) {
            assertTokenLocation(parser, locationTestCase.locations[i++])
        }

        assertEquals(locationTestCase.locations.size, i + 1)
    }

    private fun assertCurrentLocation(parser: CirJsonParser, locationData: LocationData) {
        assertLocation(parser.currentLocation(), locationData)
    }

    private fun assertTokenLocation(parser: CirJsonParser, locationData: LocationData) {
        assertLocation(parser.currentTokenLocation(), locationData)
    }

    private fun assertLocation(location: CirJsonLocation, locationData: LocationData) {
        val expected = "(${locationData.lineNumber}, ${locationData.columnNumber}, ${locationData.offset})"
        val actual =
                "(${location.lineNumber}, ${location.columnNumber}, ${location.byteOffset.takeIf { it != -1L } ?: location.charOffset})"
        assertEquals(expected, actual)
    }

    private data class LocationData(val lineNumber: Long, val columnNumber: Long, val offset: Long)

    private enum class LocationTestCase(val doc: String, vararg val locations: LocationData) {

        SIMPLE_VALUE("42", at(1, 1, 0), at(1, 3, 2)),

        SIMPLE_VALUE_WITH_PADDING("   1337  ", at(1, 4, 3), at(1, 10, 9)),

        SIMPLE_VALUE_WITH_MULTIBYTE_CHARS("\"Правда\"",
                at(1, 1, 0),
                at(1, 15, 14)
        ),

        SIMPLE_VALUE_INCLUDING_SURROGATE_PAIR_CHARS("\"a П \uD83D\uDE01\"",
                at(1, 1, 0),
                at(1, 12, 11) // reader counts surrogate pairs as two chars
        ),

        ARRAY_IN_ONE_LINE("[\"root\",\"hello\",42,true]",
                at(1, 1, 0), // [
                at(1, 2, 1), // "root"
                at(1, 9, 8), // "hello"
                at(1, 17, 16), // 42
                at(1, 20, 19), // true
                at(1, 24, 23), // ]
                at(1, 25, 24) // end of input
        ),

        ARRAY_IN_ONE_LINE_WITH_PADDING("  [ \"root\" ,   \"hello\" ,   42   ,   true   ]   ",
                at(1, 3, 2), // [
                at(1, 5, 4), // "root"
                at(1, 16, 15), // "hello"
                at(1, 28, 27), // 42
                at(1, 37, 36), // true
                at(1, 44, 43), // ]
                at(1, 48, 47) // end of input
        ),

        ARRAY_IN_MULTIPLE_LINES("[\n    \"root\",\n    \"hello\",\n    42,\n    true\n]",
                at(1, 1, 0), // [
                at(2, 5, 6), // "root"
                at(3, 5, 18), // "hello"
                at(4, 5, 31), // 42
                at(5, 5, 39), // true
                at(6, 1, 44), // ]
                at(6, 2, 45) // end of input
        ),

        ARRAY_IN_MULTIPLE_LINES_WITH_WEIRD_SPACING(" [\n    \"root\"   ,  \n  \"hello\" ,  \n 42   ,\n      true\n ]",
                at(1, 2, 1), // [
                at(2, 5, 7), // "root"
                at(3, 3, 22), // "hello"
                at(4, 2, 35), // 42
                at(5, 7, 48), // true
                at(6, 2, 54), // ]
                at(6, 3, 55) // end of input
        ),

        ARRAY_IN_MULTIPLE_LINES_CRLF("[\r\n    \"root\",\r\n    \"hello\",\r\n    42,\r\n    true\r\n]",
                at(1, 1, 0), // [
                at(2, 5, 7), // "root"
                at(3, 5, 20), // "hello"
                at(4, 5, 34), // 42
                at(5, 5, 43), // true
                at(6, 1, 49), // ]
                at(6, 2, 50) // end of input
        ),

        OBJECT_IN_ONE_LINE("{\"__cirJsonId__\":\"root\",\"first\":\"hello\",\"second\":42}",
                at(1, 1, 0), // {
                at(1, 2, 1), // "__cirJsonId__"
                at(1, 18, 17), // "root"
                at(1, 25, 24), // "first"
                at(1, 33, 32), // "hello"
                at(1, 41, 40), // "second"
                at(1, 50, 49), // 42
                at(1, 52, 51), // }
                at(1, 53, 52) // end of input
        ),

        OBJECT_IN_MULTIPLE_LINES("{\n    \"__cirJsonId__\":\"root\",\n    \"first\":\"hello\",\n    \"second\":42\n}",
                at(1, 1, 0), // {
                at(2, 5, 6), // "__cirJsonId__"
                at(2, 21, 22), // "root"
                at(3, 5, 34), // "first"
                at(3, 13, 42), // "hello"
                at(4, 5, 55), // "second"
                at(4, 14, 64), // 42
                at(5, 1, 67), // }
                at(5, 2, 68) // end of input
        ),

        OBJECT_IN_MULTIPLE_LINES_CRLF(
                "{\r\n    \"__cirJsonId__\":\"root\",\r\n    \"first\":\"hello\",\r\n    \"second\":42\r\n}",
                at(1, 1, 0), // {
                at(2, 5, 7), // "__cirJsonId__"
                at(2, 21, 23), // "root"
                at(3, 5, 36), // "first"
                at(3, 13, 44), // "hello"
                at(4, 5, 58), // "second"
                at(4, 14, 67), // 42
                at(5, 1, 71), // }
                at(5, 2, 72) // end of input
        );

        val finalLocation
            get() = locations.last()

    }

    companion object {

        private fun at(lineNumber: Long, columnNumber: Long, offset: Long): LocationData {
            return LocationData(lineNumber, columnNumber, offset)
        }

    }
}