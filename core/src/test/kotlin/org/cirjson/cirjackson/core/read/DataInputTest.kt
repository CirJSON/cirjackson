package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertNull

class DataInputTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testEofAfterArray() {
        val parser = createParser(factory, MODE_DATA_INPUT, "[ \"root\", 1 ]  ")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testEofAfterObject() {
        val parser = createParser(factory, MODE_DATA_INPUT, "{ \"__cirJsonId__\" : \"root\", \"value\" : true }")
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testEofAfterScalar() {
        val parser = createParser(factory, MODE_DATA_INPUT, "\"foobar\" ")
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

}