package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ParserSymbolHandlingTest : TestBase() {

    @Test
    fun testSymbolsWithNull() {
        val factory = newStreamFactory()

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            symbolsWithNull(factory, mode)
            symbolsWithNull(factory, mode)
        }
    }

    private fun symbolsWithNull(factory: CirJsonFactory, mode: Int) {
        val doc = "{\"__cirJsonId__\":\"root\", \"\\u0000abc\" : 1, \"abc\":2}"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        var currentName = parser.currentName!!

        if ("\u0000abc" != currentName) {
            fail("Expected \\u0000abc (4 bytes), actual '$currentName' (${currentName.length})")
        }

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        currentName = parser.currentName!!

        if ("abc" != currentName) {
            fail("Expected abc (3 bytes), actual '$currentName' (${currentName.length})")
        }

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2, parser.intValue)

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    @Test
    fun testSymbolsWithNullOnlyName() {
        val factory = newStreamFactory()

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            symbolsWithNullOnlyName(factory, mode)
            symbolsWithNullOnlyName(factory, mode)
        }
    }

    private fun symbolsWithNullOnlyName(factory: CirJsonFactory, mode: Int) {
        val parser = createParser(factory, mode, DOC_NULL_ONLY)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertNullStrings(NAME_1, parser.currentName!!)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertNullStrings(NAME_2, parser.currentName!!)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2, parser.intValue)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertNullStrings(NAME_3, parser.currentName!!)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(3, parser.intValue)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertNullStrings(NAME_4, parser.currentName!!)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(4, parser.intValue)

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    private fun assertNullStrings(expected: String, actual: String) {
        if (expected.length != actual.length) {
            fail("Expected ${expected.length} nulls, got ${actual.length}")
        }

        assertEquals(expected, actual)
    }

    companion object {

        private const val NAME_1 = "\u0000"

        private const val NAME_2 = NAME_1 + NAME_1

        private const val NAME_3 = NAME_2 + NAME_1

        private const val NAME_4 = NAME_3 + NAME_1

        private const val QUOTED_NULL = "\\u0000"

        private val DOC_NULL_ONLY = apostropheToQuote(
                "{'__cirJsonId__':'root', '$QUOTED_NULL':1, '${QUOTED_NULL + QUOTED_NULL}':2, '${QUOTED_NULL + QUOTED_NULL + QUOTED_NULL}':3, '${QUOTED_NULL + QUOTED_NULL + QUOTED_NULL + QUOTED_NULL}':4}")

    }

}