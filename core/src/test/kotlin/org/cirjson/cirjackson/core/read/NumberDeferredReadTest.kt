package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NumberDeferredReadTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testDeferredInt() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            deferredInt(mode)
        }
    }

    private fun deferredInt(mode: Int) {
        createParser(factory, mode, " 12345 ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(12345, parser.numberValueDeferred)
            assertEquals(CirJsonParser.NumberType.INT, parser.numberType)
            assertNull(parser.nextToken())
        }
    }

    @Test
    fun testDeferredLong() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            deferredLong(mode)
        }
    }

    private fun deferredLong(mode: Int) {
        val value = 100L + Int.MAX_VALUE
        createParser(factory, mode, " $value ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(value, parser.numberValueDeferred)
            assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)
            assertNull(parser.nextToken())
        }
    }

    @Test
    fun testDeferredBigInteger() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            deferredBigInteger(mode)
        }
    }

    private fun deferredBigInteger(mode: Int) {
        val value = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN)
        createParser(factory, mode, " $value ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(CirJsonParser.NumberType.BIG_INTEGER, parser.numberType)
            val deferred = parser.numberValueDeferred
            assertEquals(String::class.java, deferred::class.java)
            assertEquals(value.toString(), deferred)

            assertEquals(value, parser.bigIntegerValue)
            assertEquals(value, parser.numberValueDeferred)
            assertNull(parser.nextToken())
        }
    }

    @Test
    fun testDeferredFloatingPoint() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            deferredFloatingPoint(mode)
        }
    }

    private fun deferredFloatingPoint(mode: Int) {
        createParser(factory, mode, " 0.25 ").use { parser ->
            val value = BigDecimal("0.25")
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            val deferred = parser.numberValueDeferred
            assertEquals(String::class.java, deferred::class.java)
            assertEquals(value.toString(), deferred)

            assertEquals(value, parser.bigDecimalValue)
            assertEquals(value, parser.numberValueDeferred)
            assertEquals(CirJsonParser.NumberType.BIG_DECIMAL, parser.numberType)
            assertNull(parser.nextToken())
        }

        createParser(factory, mode, " 0.25 ").use { parser ->
            val value = 0.25
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            val deferred = parser.numberValueDeferred
            assertEquals(String::class.java, deferred::class.java)
            assertEquals(value.toString(), deferred)

            assertEquals(value, parser.doubleValue)
            assertEquals(value, parser.numberValueDeferred)
            assertEquals(CirJsonParser.NumberType.DOUBLE, parser.numberType)
            assertNull(parser.nextToken())
        }

        createParser(factory, mode, " 0.25 ").use { parser ->
            val value = 0.25f
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            val deferred = parser.numberValueDeferred
            assertEquals(String::class.java, deferred::class.java)
            assertEquals(value.toString(), deferred)

            assertEquals(value, parser.floatValue)
            assertEquals(value, parser.numberValueDeferred)
            assertEquals(CirJsonParser.NumberType.FLOAT, parser.numberType)
            assertNull(parser.nextToken())
        }
    }

}