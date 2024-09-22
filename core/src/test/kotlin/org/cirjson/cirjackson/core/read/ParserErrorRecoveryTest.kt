package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class ParserErrorRecoveryTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testRecoverNumber() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            recoverNumber(mode)
        }
    }

    private fun recoverNumber(mode: Int) {
        val doc = "1\n[\"root\", , ]\n3 "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character (','")
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(3, parser.intValue)
        assertNull(parser.nextToken())
        parser.close()
    }

}