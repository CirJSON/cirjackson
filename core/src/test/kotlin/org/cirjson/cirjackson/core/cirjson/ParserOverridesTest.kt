package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.TreeNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class ParserOverridesTest : TestBase() {

    @Test
    fun testTokenAccess() {
        val factory = CirJsonFactory()

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            tokenAccess(factory, mode)
        }
    }

    private fun tokenAccess(factory: CirJsonFactory, mode: Int) {
        val doc = "[ ]"

        val parser = createParser(factory, mode, doc)
        assertNull(parser.currentToken())
        parser.clearCurrentToken()
        assertNull(parser.currentToken())
        assertNull(parser.embeddedObject)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.START_ARRAY, parser.currentToken())
        parser.clearCurrentToken()
        assertNull(parser.currentToken())

        try {
            parser.readValueAsTree<TreeNode>()
            fail("Should get exception without codec")
        } catch (e: UnsupportedOperationException) {
            verifyException(e, "Operation not supported")
        }

        parser.close()
    }

    @Test
    fun testCurrentName() {
        val factory = CirJsonFactory()

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            currentName(factory, mode)
        }
    }

    private fun currentName(factory: CirJsonFactory, mode: Int) {
        val doc = "{\"__cirJsonId__\": \"root\", \"first\":{\"__cirJsonId__\": \"2\", \"second\":3, \"third\":false}}"

        val parser = createParser(factory, mode, doc)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("__cirJsonId__", parser.currentName())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("__cirJsonId__", parser.currentName())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("first", parser.currentName())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("first", parser.currentName())

        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertEquals("__cirJsonId__", parser.currentName())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("__cirJsonId__", parser.currentName())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("second", parser.currentName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("second", parser.currentName())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("third", parser.currentName())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertEquals("third", parser.currentName())

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.clearCurrentToken()
        assertNull(parser.currentToken())
        parser.close()
    }

}