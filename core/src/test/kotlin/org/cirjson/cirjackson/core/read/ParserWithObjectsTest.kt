package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.*

class ParserWithObjectsTest : TestBase() {

    @Test
    fun testNextValue() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextValue(mode)
        }
    }

    private fun nextValue(mode: Int) {
        var doc = "[ \"root\", 1, 2, 3, 4 ]"
        var parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextValue())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())

        for (i in 1..4) {
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextValue())
            assertEquals(i, parser.intValue)
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextValue())
        assertNull(parser.nextValue())
        parser.close()

        doc = "{ \"__cirJsonId__\" : \"root\", \"3\" :3, \"4\": 4, \"5\" : 5 }"
        parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextValue())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())

        for (i in 3..5) {
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextValue())
            assertEquals(i.toString(), parser.currentName)
            assertEquals(i, parser.intValue)
        }

        assertToken(CirJsonToken.END_OBJECT, parser.nextValue())
        assertNull(parser.nextValue())
        parser.close()

        doc = "[ \"root\", true, [ \"1\" ], { \"__cirJsonId__\" : \"2\", \"a\" : 3 } ]"
        parser = createParser(mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextValue())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextValue())
        assertToken(CirJsonToken.START_ARRAY, parser.nextValue())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())
        assertToken(CirJsonToken.END_ARRAY, parser.nextValue())

        assertToken(CirJsonToken.START_OBJECT, parser.nextValue())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextValue())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.END_OBJECT, parser.nextValue())
        assertToken(CirJsonToken.END_ARRAY, parser.nextValue())
        assertNull(parser.nextValue())
        parser.close()
    }

    @Test
    fun testNextValueNested() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextValueNested(mode)
        }
    }

    private fun nextValueNested(mode: Int) {
        var doc =
                "{\"__cirJsonId__\" : \"root\", \"a\": { \"__cirJsonId__\" : \"a\", \"b\" : true, \"c\": false }, \"d\": 3 }"
        var parser = createParser(mode, doc)

        assertToken(CirJsonToken.START_OBJECT, parser.nextValue())
        assertNull(parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())
        assertToken(CirJsonToken.START_OBJECT, parser.nextValue())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextValue())
        assertEquals("b", parser.currentName)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextValue())
        assertEquals("c", parser.currentName)
        assertToken(CirJsonToken.END_OBJECT, parser.nextValue())
        assertEquals("a", parser.currentName)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextValue())
        assertEquals("d", parser.currentName)
        assertToken(CirJsonToken.END_OBJECT, parser.nextValue())
        assertNull(parser.currentName)
        assertNull(parser.nextValue())
        parser.close()

        doc = "{\"__cirJsonId__\" : \"root\", \"a\": [ \"a\", false ] }"
        parser = createParser(mode, doc)

        assertToken(CirJsonToken.START_OBJECT, parser.nextValue())
        assertNull(parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())
        assertToken(CirJsonToken.START_ARRAY, parser.nextValue())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextValue())
        assertNull(parser.currentName)
        assertToken(CirJsonToken.END_ARRAY, parser.nextValue())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.END_OBJECT, parser.nextValue())
        assertNull(parser.currentName)
        assertNull(parser.nextValue())
        parser.close()
    }

    @Test
    fun testIsClosed() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            isClosed(mode, true)
            isClosed(mode, false)
        }
    }

    private fun isClosed(mode: Int, partial: Boolean) {
        val doc = "[ \"root\", 1, 2, 3 ]"
        val parser = createParser(mode, doc)

        assertFalse(parser.isClosed)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertFalse(parser.isClosed)

        if (partial) {
            parser.close()
            assertTrue(parser.isClosed)
            return
        }

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
        parser.close()
    }

}