package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValueConversionsTest : TestBase() {

    @Test
    fun testAsBoolean() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            asBoolean(mode)
        }
    }

    private fun asBoolean(mode: Int) {
        val doc = "[ \"root\", true, false, null, 1, 0, -1, \"true\", \"false\", \"foo\" ]"
        val parser = createParser(mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertFalse(parser.valueAsBoolean)
        assertTrue(parser.getValueAsBoolean(true))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertFalse(parser.valueAsBoolean)
        assertTrue(parser.getValueAsBoolean(true))

        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertTrue(parser.valueAsBoolean)
        assertTrue(parser.getValueAsBoolean(true))
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertFalse(parser.valueAsBoolean)
        assertFalse(parser.getValueAsBoolean(true))
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertFalse(parser.valueAsBoolean)
        assertFalse(parser.getValueAsBoolean(true))

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertTrue(parser.valueAsBoolean)
        assertTrue(parser.getValueAsBoolean(true))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertFalse(parser.valueAsBoolean)
        assertFalse(parser.getValueAsBoolean(true))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertTrue(parser.valueAsBoolean)
        assertTrue(parser.getValueAsBoolean(true))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertTrue(parser.valueAsBoolean)
        assertTrue(parser.getValueAsBoolean(true))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertFalse(parser.valueAsBoolean)
        assertFalse(parser.getValueAsBoolean(true))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertFalse(parser.valueAsBoolean)
        assertTrue(parser.getValueAsBoolean(true))
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertFalse(parser.valueAsBoolean)
        assertTrue(parser.getValueAsBoolean(true))
        parser.close()
    }

    @Test
    fun testAsInt() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            asInt(mode)
        }
    }

    private fun asInt(mode: Int) {
        val doc = "[ \"root\", 1, -3, 4.98, true, false, null, \"-17\", \"foo\" ]"
        val parser = createParser(mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals(0, parser.valueAsInt)
        assertEquals(9, parser.getValueAsInt(9))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(0, parser.valueAsInt)
        assertEquals(9, parser.getValueAsInt(9))

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.valueAsInt)
        assertEquals(1, parser.getValueAsInt(9))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-3, parser.valueAsInt)
        assertEquals(-3, parser.getValueAsInt(9))
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(4, parser.valueAsInt)
        assertEquals(4, parser.getValueAsInt(9))

        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertEquals(1, parser.valueAsInt)
        assertEquals(1, parser.getValueAsInt(9))
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertEquals(0, parser.valueAsInt)
        assertEquals(0, parser.getValueAsInt(9))
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertEquals(0, parser.valueAsInt)
        assertEquals(0, parser.getValueAsInt(9))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(-17, parser.valueAsInt)
        assertEquals(-17, parser.getValueAsInt(9))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(0, parser.valueAsInt)
        assertEquals(9, parser.getValueAsInt(9))
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals(0, parser.valueAsInt)
        assertEquals(9, parser.getValueAsInt(9))
        parser.close()
    }

    @Test
    fun testAsLong() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            asLong(mode)
        }
    }

    private fun asLong(mode: Int) {
        val doc = "[ \"root\", 1, -3, 4.98, true, false, null, \"-17\", \"foo\" ]"
        val parser = createParser(mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals(0L, parser.valueAsLong)
        assertEquals(9L, parser.getValueAsLong(9L))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(0L, parser.valueAsLong)
        assertEquals(9L, parser.getValueAsLong(9L))

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1L, parser.valueAsLong)
        assertEquals(1L, parser.getValueAsLong(9L))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-3L, parser.valueAsLong)
        assertEquals(-3L, parser.getValueAsLong(9L))
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(4L, parser.valueAsLong)
        assertEquals(4L, parser.getValueAsLong(9L))

        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertEquals(1L, parser.valueAsLong)
        assertEquals(1L, parser.getValueAsLong(9L))
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertEquals(0L, parser.valueAsLong)
        assertEquals(0L, parser.getValueAsLong(9L))
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertEquals(0L, parser.valueAsLong)
        assertEquals(0L, parser.getValueAsLong(9L))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(-17L, parser.valueAsLong)
        assertEquals(-17L, parser.getValueAsLong(9L))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(0L, parser.valueAsLong)
        assertEquals(9L, parser.getValueAsLong(9L))
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals(0L, parser.valueAsLong)
        assertEquals(9L, parser.getValueAsLong(9L))
        parser.close()
    }

    @Test
    fun testAsDouble() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            asDouble(mode)
        }
    }

    private fun asDouble(mode: Int) {
        val doc = "[ \"root\", 1, -3, 4.98, true, false, null, \"-17.25\", \"foo\" ]"
        val parser = createParser(mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertEquals(0.0, parser.valueAsDouble)
        assertEquals(9.0, parser.getValueAsDouble(9.0))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(0.0, parser.valueAsDouble)
        assertEquals(9.0, parser.getValueAsDouble(9.0))

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1.0, parser.valueAsDouble)
        assertEquals(1.0, parser.getValueAsDouble(9.0))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-3.0, parser.valueAsDouble)
        assertEquals(-3.0, parser.getValueAsDouble(9.0))
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(4.98, parser.valueAsDouble)
        assertEquals(4.98, parser.getValueAsDouble(9.0))

        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertEquals(1.0, parser.valueAsDouble)
        assertEquals(1.0, parser.getValueAsDouble(9.0))
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertEquals(0.0, parser.valueAsDouble)
        assertEquals(0.0, parser.getValueAsDouble(9.0))
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertEquals(0.0, parser.valueAsDouble)
        assertEquals(0.0, parser.getValueAsDouble(9.0))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(-17.25, parser.valueAsDouble)
        assertEquals(-17.25, parser.getValueAsDouble(9.0))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(0.0, parser.valueAsDouble)
        assertEquals(9.0, parser.getValueAsDouble(9.0))
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertEquals(0.0, parser.valueAsDouble)
        assertEquals(9.0, parser.getValueAsDouble(9.0))
        parser.close()
    }

}