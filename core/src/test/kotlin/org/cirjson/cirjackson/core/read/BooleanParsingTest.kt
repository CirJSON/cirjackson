package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.InputCoercionException
import kotlin.test.Test
import kotlin.test.fail

class BooleanParsingTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testSimpleBoolean() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simpleBoolean(mode)
        }
    }

    private fun simpleBoolean(mode: Int) {
        val doc = "[ \"root\",true,true,true,true,true,false,false,false,false,false ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testInvalidBooleanAccess() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            invalidBooleanAccess(mode)
        }
    }

    private fun invalidBooleanAccess(mode: Int) {
        val doc = "[ \"abc\" ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.booleanValue
            fail("Should have thrown an exception")
        } catch (e: InputCoercionException) {
            verifyException(e, "not of boolean type")
        }

        parser.close()
    }

}