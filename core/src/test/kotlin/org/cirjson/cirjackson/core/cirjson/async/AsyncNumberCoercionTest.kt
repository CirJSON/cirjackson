package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.exception.InputCoercionException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AsyncNumberCoercionTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testToIntCoercion() {
        for (mode in ALL_ASYNC_MODES) {
            toIntCoercion(mode, 0, 99)
            toIntCoercion(mode, 0, 5)
            toIntCoercion(mode, 0, 3)
            toIntCoercion(mode, 0, 2)
            toIntCoercion(mode, 0, 1)

            toIntCoercion(mode, 1, 99)
            toIntCoercion(mode, 1, 3)
            toIntCoercion(mode, 1, 1)
        }
    }

    private fun toIntCoercion(mode: Int, padding: Int, bytesPerFeed: Int) {
        var parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("1"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1L, parser.longValue)
        assertEquals(1, parser.intValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("10"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigInteger.TEN, parser.bigIntegerValue)
        assertEquals(10, parser.intValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("2"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0, parser.doubleValue)
        assertEquals(2, parser.intValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("2"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0f, parser.floatValue)
        assertEquals(2, parser.intValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("0.1"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.1, parser.doubleValue)
        assertEquals(0, parser.intValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("10"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigDecimal.TEN, parser.bigDecimalValue)
        assertEquals(10, parser.intValue)
        parser.close()
    }

    @Test
    fun testToIntFailing() {
        for (mode in ALL_ASYNC_MODES) {
            toIntFailing(mode, 0, 99)
            toIntFailing(mode, 0, 5)
            toIntFailing(mode, 0, 3)
            toIntFailing(mode, 0, 2)
            toIntFailing(mode, 0, 1)

            toIntFailing(mode, 1, 99)
            toIntFailing(mode, 1, 3)
            toIntFailing(mode, 1, 1)
        }
    }

    private fun toIntFailing(mode: Int, padding: Int, bytesPerFeed: Int) {
        val big = 1L + Int.MAX_VALUE
        createAsync(factory, mode, bytesPerFeed, utf8Bytes(big.toString()), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(big, parser.longValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        val small = Int.MIN_VALUE - 1L
        createAsync(factory, mode, bytesPerFeed, utf8Bytes(small.toString()), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(small, parser.numberValue)
            assertEquals(small, parser.longValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        createAsync(factory, mode, bytesPerFeed, utf8Bytes("$big.0"), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            assertEquals(big.toDouble(), parser.doubleValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_FLOAT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        createAsync(factory, mode, bytesPerFeed, utf8Bytes("$small.0"), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            assertEquals(small.toDouble(), parser.doubleValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_FLOAT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        createAsync(factory, mode, bytesPerFeed, utf8Bytes(big.toString()), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(BigInteger.valueOf(big), parser.bigIntegerValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        createAsync(factory, mode, bytesPerFeed, utf8Bytes(small.toString()), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(BigInteger.valueOf(small), parser.bigIntegerValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }
    }

    @Test
    fun testToLongCoercion() {
        for (mode in ALL_ASYNC_MODES) {
            toLongCoercion(mode, 0, 99)
            toLongCoercion(mode, 0, 5)
            toLongCoercion(mode, 0, 3)
            toLongCoercion(mode, 0, 2)
            toLongCoercion(mode, 0, 1)

            toLongCoercion(mode, 1, 99)
            toLongCoercion(mode, 1, 3)
            toLongCoercion(mode, 1, 1)
        }
    }

    private fun toLongCoercion(mode: Int, padding: Int, bytesPerFeed: Int) {
        var parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("1"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertEquals(1L, parser.longValue)
        parser.close()

        val biggish = 12345678901L
        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes(biggish.toString()), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigInteger.valueOf(biggish), parser.bigIntegerValue)
        assertEquals(biggish, parser.longValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("2"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0, parser.doubleValue)
        assertEquals(2L, parser.longValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("2"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0f, parser.floatValue)
        assertEquals(2L, parser.longValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("0.1"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.1, parser.doubleValue)
        assertEquals(0L, parser.longValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("10"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigDecimal.TEN, parser.bigDecimalValue)
        assertEquals(10L, parser.longValue)
        parser.close()
    }

    @Test
    fun testToLongFailing() {
        for (mode in ALL_ASYNC_MODES) {
            toLongFailing(mode, 0, 99)
            toLongFailing(mode, 0, 5)
            toLongFailing(mode, 0, 3)
            toLongFailing(mode, 0, 2)
            toLongFailing(mode, 0, 1)

            toLongFailing(mode, 1, 99)
            toLongFailing(mode, 1, 3)
            toLongFailing(mode, 1, 1)
        }
    }

    private fun toLongFailing(mode: Int, padding: Int, bytesPerFeed: Int) {
        val big = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN)
        createAsync(factory, mode, bytesPerFeed, utf8Bytes(big.toString()), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(big, parser.bigIntegerValue)
            assertEquals(big, parser.numberValue)

            try {
                parser.longValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `Long`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Long::class.java, e.targetType)
            }
        }

        val small = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.TEN)
        createAsync(factory, mode, bytesPerFeed, utf8Bytes(small.toString()), padding).use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(small, parser.bigIntegerValue)

            try {
                parser.longValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `Long`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Long::class.java, e.targetType)
            }
        }
    }

    @Test
    fun testToBigIntegerCoercion() {
        for (mode in ALL_ASYNC_MODES) {
            toBigIntegerCoercion(mode, 0, 99)
            toBigIntegerCoercion(mode, 0, 5)
            toBigIntegerCoercion(mode, 0, 3)
            toBigIntegerCoercion(mode, 0, 2)
            toBigIntegerCoercion(mode, 0, 1)

            toBigIntegerCoercion(mode, 1, 99)
            toBigIntegerCoercion(mode, 1, 3)
            toBigIntegerCoercion(mode, 1, 1)
        }
    }

    private fun toBigIntegerCoercion(mode: Int, padding: Int, bytesPerFeed: Int) {
        var parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("1"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertEquals(BigInteger.ONE, parser.bigIntegerValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("1"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1L, parser.longValue)
        assertEquals(BigInteger.ONE, parser.bigIntegerValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("2"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0, parser.doubleValue)
        assertEquals(BigInteger.TWO, parser.bigIntegerValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("2"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0f, parser.floatValue)
        assertEquals(BigInteger.TWO, parser.bigIntegerValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("0.1"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.1, parser.doubleValue)
        assertEquals(BigInteger.ZERO, parser.bigIntegerValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("10"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigDecimal.TEN, parser.bigDecimalValue)
        assertEquals(BigInteger.TEN, parser.bigIntegerValue)
        parser.close()
    }

    @Test
    fun testToFloatCoercion() {
        for (mode in ALL_ASYNC_MODES) {
            toFloatCoercion(mode, 0, 99)
            toFloatCoercion(mode, 0, 5)
            toFloatCoercion(mode, 0, 3)
            toFloatCoercion(mode, 0, 2)
            toFloatCoercion(mode, 0, 1)

            toFloatCoercion(mode, 1, 99)
            toFloatCoercion(mode, 1, 3)
            toFloatCoercion(mode, 1, 1)
        }
    }

    private fun toFloatCoercion(mode: Int, padding: Int, bytesPerFeed: Int) {
        var parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("100.5"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(BigDecimal("100.5"), parser.bigDecimalValue)
        assertEquals(100.5f, parser.floatValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("10"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigInteger.TEN, parser.bigIntegerValue)
        assertEquals(10.0f, parser.floatValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("0.5"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.5, parser.doubleValue)
        assertEquals(0.5f, parser.floatValue)
        parser.close()
    }

    @Test
    fun testToDoubleCoercion() {
        for (mode in ALL_ASYNC_MODES) {
            toDoubleCoercion(mode, 0, 99)
            toDoubleCoercion(mode, 0, 5)
            toDoubleCoercion(mode, 0, 3)
            toDoubleCoercion(mode, 0, 2)
            toDoubleCoercion(mode, 0, 1)

            toDoubleCoercion(mode, 1, 99)
            toDoubleCoercion(mode, 1, 3)
            toDoubleCoercion(mode, 1, 1)
        }
    }

    private fun toDoubleCoercion(mode: Int, padding: Int, bytesPerFeed: Int) {
        var parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("100.5"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(BigDecimal("100.5"), parser.bigDecimalValue)
        assertEquals(100.5, parser.doubleValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("10"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigInteger.TEN, parser.bigIntegerValue)
        assertEquals(10.0, parser.doubleValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("0.5"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.5f, parser.floatValue)
        assertEquals(0.5, parser.doubleValue)
        parser.close()
    }

    @Test
    fun testToBigDecimalCoercion() {
        for (mode in ALL_ASYNC_MODES) {
            toBigDecimalCoercion(mode, 0, 99)
            toBigDecimalCoercion(mode, 0, 5)
            toBigDecimalCoercion(mode, 0, 3)
            toBigDecimalCoercion(mode, 0, 2)
            toBigDecimalCoercion(mode, 0, 1)

            toBigDecimalCoercion(mode, 1, 99)
            toBigDecimalCoercion(mode, 1, 3)
            toBigDecimalCoercion(mode, 1, 1)
        }
    }

    private fun toBigDecimalCoercion(mode: Int, padding: Int, bytesPerFeed: Int) {
        var parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes("1"), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertEquals(BigDecimal.ONE, parser.bigDecimalValue)
        parser.close()

        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes(Long.MAX_VALUE.toString()), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(Long.MAX_VALUE, parser.longValue)
        assertEquals(BigDecimal.valueOf(Long.MAX_VALUE), parser.bigDecimalValue)
        parser.close()

        val biggie = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN)
        parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes(biggie.toString()), padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(biggie, parser.bigIntegerValue)
        assertEquals(BigDecimal(biggie), parser.bigDecimalValue)
        parser.close()
    }

}