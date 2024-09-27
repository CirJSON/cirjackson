package org.cirjson.cirjackson.core.read.location

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationInObjectTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testLocationInObject() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            if (mode == MODE_DATA_INPUT) {
                continue
            }

            locationInObject(mode)
        }
    }

    private fun locationInObject(mode: Int) {
        val useBytes = mode in ALL_BINARY_PARSER_MODES
        val doc =
                "{\"__cirJsonId__\":\"root\",\"f1\":\"v1\",\"f2\":{\"__cirJsonId__\":\"f2\",\"f3\":\"v3\"},\"f4\":[\"f4\",true,false],\"f5\":5}"
        //       0 1                 17       24     29     34    3940                56     61     66   70 72    7778    83   88   93 95    100101
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertOffset(parser, useBytes, 0L)
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertOffset(parser, useBytes, 1L)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertOffset(parser, useBytes, 17L)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertOffset(parser, useBytes, 24L)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertOffset(parser, useBytes, 29L)

        assertEquals("f2", parser.nextName())
        assertOffset(parser, useBytes, 34L)
        assertToken(CirJsonToken.START_OBJECT, parser.nextValue())
        assertOffset(parser, useBytes, 39L)
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertOffset(parser, useBytes, 40L)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertOffset(parser, useBytes, 56L)

        assertEquals("f3", parser.nextName())
        assertOffset(parser, useBytes, 61L)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextValue())
        assertOffset(parser, useBytes, 66L)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertOffset(parser, useBytes, 70L)

        assertEquals("f4", parser.nextName())
        assertOffset(parser, useBytes, 72L)
        assertToken(CirJsonToken.START_ARRAY, parser.nextValue())
        assertOffset(parser, useBytes, 77L)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertOffset(parser, useBytes, 78L)

        assertToken(CirJsonToken.VALUE_TRUE, parser.nextValue())
        assertOffset(parser, useBytes, 83L)

        assertToken(CirJsonToken.VALUE_FALSE, parser.nextValue())
        assertOffset(parser, useBytes, 88L)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertOffset(parser, useBytes, 93L)

        assertEquals("f5", parser.nextName())
        assertOffset(parser, useBytes, 95L)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertOffset(parser, useBytes, 100L)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertOffset(parser, useBytes, 101L)

        parser.close()
    }

    private fun assertOffset(parser: CirJsonParser, useBytes: Boolean, expected: Long) {
        if (useBytes) {
            assertEquals(expected, parser.currentTokenLocation().byteOffset, "byteOffset")
        } else {
            assertEquals(expected, parser.currentTokenLocation().charOffset, "charOffset")
        }
    }

}