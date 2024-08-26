package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.io.SerializedString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CirJsonParserClosedCaseTest : TestBase() {

    @Test
    fun testNullReturnedOnClosedParserOnNextName() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            val parser = createClosedParser(mode)
            assertNull(parser.nextName())
        }
    }

    @Test
    fun testFalseReturnedOnClosedParserOnNextNameSerializedString() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            val parser = createClosedParser(mode)
            assertFalse(parser.nextName(SerializedString("")))
        }
    }

    @Test
    fun testNullReturnedOnClosedParserOnNextToken() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            val parser = createClosedParser(mode)
            assertNull(parser.nextToken())
        }
    }

    @Test
    fun testNullReturnedOnClosedParserOnNextValue() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            val parser = createClosedParser(mode)
            assertNull(parser.nextValue())
        }
    }

    private fun createClosedParser(mode: Int): CirJsonParser {
        val parser = createParser(mode, "{\"__cirJsonId__\":\"root\"}")
        parser.close()
        return parser
    }

}