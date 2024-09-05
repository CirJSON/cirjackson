package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AsyncNumberDeferredReadTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testDeferredInt() {
        for (mode in ALL_ASYNC_MODES) {
            deferredInt(mode, 0, 99)
            deferredInt(mode, 0, 5)
            deferredInt(mode, 0, 3)
            deferredInt(mode, 0, 2)
            deferredInt(mode, 0, 1)

            deferredInt(mode, 1, 99)
            deferredInt(mode, 1, 3)
            deferredInt(mode, 1, 1)
        }
    }

    private fun deferredInt(mode: Int, padding: Int, bytesPerFeed: Int) {
        createAsync(factory, mode, bytesPerFeed, utf8Bytes(" 12345 "), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(12345, parser.numberValueDeferred)
            assertEquals(CirJsonParser.NumberType.INT, parser.numberType)
            assertNull(parser.nextToken())
        }
    }

    @Test
    fun testDeferredLong() {
        for (mode in ALL_ASYNC_MODES) {
            deferredLong(mode, 0, 99)
            deferredLong(mode, 0, 5)
            deferredLong(mode, 0, 3)
            deferredLong(mode, 0, 2)
            deferredLong(mode, 0, 1)

            deferredLong(mode, 1, 99)
            deferredLong(mode, 1, 3)
            deferredLong(mode, 1, 1)
        }
    }

    private fun deferredLong(mode: Int, padding: Int, bytesPerFeed: Int) {
        val value = 100L + Int.MAX_VALUE
        createAsync(factory, mode, bytesPerFeed, utf8Bytes(" $value "), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(value, parser.numberValueDeferred)
            assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)
            assertNull(parser.nextToken())
        }
    }

    @Test
    fun testDeferredBigInteger() {
        for (mode in ALL_ASYNC_MODES) {
            deferredBigInteger(mode, 0, 99)
            deferredBigInteger(mode, 0, 5)
            deferredBigInteger(mode, 0, 3)
            deferredBigInteger(mode, 0, 2)
            deferredBigInteger(mode, 0, 1)

            deferredBigInteger(mode, 1, 99)
            deferredBigInteger(mode, 1, 3)
            deferredBigInteger(mode, 1, 1)
        }
    }

    private fun deferredBigInteger(mode: Int, padding: Int, bytesPerFeed: Int) {
        val value = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN)
        createAsync(factory, mode, bytesPerFeed, utf8Bytes(" $value "), padding).use { parser ->
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
        for (mode in ALL_ASYNC_MODES) {
            deferredFloatingPoint(mode, 0, 99)
            deferredFloatingPoint(mode, 0, 5)
            deferredFloatingPoint(mode, 0, 3)
            deferredFloatingPoint(mode, 0, 2)
            deferredFloatingPoint(mode, 0, 1)

            deferredFloatingPoint(mode, 1, 99)
            deferredFloatingPoint(mode, 1, 3)
            deferredFloatingPoint(mode, 1, 1)
        }
    }

    private fun deferredFloatingPoint(mode: Int, padding: Int, bytesPerFeed: Int) {
        createAsync(factory, mode, bytesPerFeed, utf8Bytes(" 0.25 "), padding).use { parser ->
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

        createAsync(factory, mode, bytesPerFeed, utf8Bytes(" 0.25 "), padding).use { parser ->
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

        createAsync(factory, mode, bytesPerFeed, utf8Bytes(" 0.25 "), padding).use { parser ->
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