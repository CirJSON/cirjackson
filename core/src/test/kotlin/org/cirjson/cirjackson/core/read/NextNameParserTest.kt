package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextNameParserTest : TestBase() {

    @Test
    fun testBasicNextName() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            basicNextName(mode)
        }
    }

    private fun basicNextName(mode: Int) {
        val parser = createParser(mode, DOC)

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("data", parser.currentName())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("primary", parser.currentName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-15, parser.intValue)

        assertEquals("vector", parser.nextName())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("yes", parser.text)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())

        assertEquals("misc", parser.nextName())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        assertEquals("name", parser.nextName())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("Bob", parser.text)

        assertNull(parser.nextName())
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        assertEquals("array", parser.nextName())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("message", parser.nextName())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("hello", parser.text)
        assertEquals("value", parser.nextName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(42, parser.intValue)
        assertEquals("misc", parser.nextName())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2, parser.intValue)

        assertNull(parser.nextName())
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    companion object {

        private val DOC = apostropheToQuote(
                "{ '__cirJsonId__' : 'root'," +
                        " 'data' : { '__cirJsonId__' : 'data', 'primary' : -15, 'vector' : [ 'data/vector', 'yes', false ], 'misc' : null, 'name' : 'Bob'  },\n" +
                        "  'array' : [ 'array', true,   {'__cirJsonId__' : 'array/1', 'message':'hello', 'value' : 42, 'misc' : ['array/1/misc', 1, 2] }, null, 0.25 ]\n}")

    }

}