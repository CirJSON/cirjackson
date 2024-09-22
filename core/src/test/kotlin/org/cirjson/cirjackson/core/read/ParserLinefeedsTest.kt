package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class ParserLinefeedsTest : TestBase() {

    @Test
    fun testLinefeeds() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            linefeeds(mode, "\r")
            linefeeds(mode, "\n")
            linefeeds(mode, "\r\n")
        }
    }

    private fun linefeeds(mode: Int, linefeed: String) {
        val doc = "[\"root\",1,${linefeed}2,$linefeed-478$linefeed]"
        val parser = createParser(mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(1, parser.currentLocation().lineNumber)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertEquals(1, parser.currentLocation().lineNumber)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2, parser.intValue)
        assertEquals(2, parser.currentLocation().lineNumber)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-478, parser.intValue)
        assertEquals(3, parser.currentLocation().lineNumber)

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals(4, parser.currentLocation().lineNumber)

        parser.close()
    }

}