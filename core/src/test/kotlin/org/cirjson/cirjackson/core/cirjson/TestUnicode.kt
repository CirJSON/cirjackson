package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestUnicode : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testSurrogates() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (checkText in BOOLEAN_OPTIONS) {
                surrogates(mode, checkText)
            }
        }
    }

    private fun surrogates(mode: Int, checkText: Boolean) {
        val parser = createParser(factory, mode, DOC)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())

        if (checkText) {
            assertEquals("__cirJsonId__", parser.text)
        }

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        if (checkText) {
            assertEquals("root", parser.text)
        }

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (checkText) {
            assertEquals("text", parser.text)
        }

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        if (checkText) {
            assertEquals("\uD83D\uDE03", parser.text)
        }

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    companion object {

        private val DOC = "{\"__cirJsonId__\":\"root\",\"text\":\"\uD83D\uDE03\"}".toByteArray()

    }

}